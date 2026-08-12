package com.hopcape.odo.infrastructure.ai

import android.graphics.Bitmap
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hopcape.odo.core.domain.scan.DocumentExtractor
import com.hopcape.odo.core.domain.scan.model.ExtractedDocument
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.core.platform.file.StoredFileKind
import com.hopcape.odo.core.platform.file.StoredFileKinds
import com.hopcape.odo.core.platform.file.StoredPageRenderer
import com.hopcape.odo.infrastructure.ai.observability.AiTelemetry
import com.hopcape.odo.infrastructure.ai.parsing.DocumentTextParser
import com.hopcape.odo.infrastructure.ai.parsing.OcrLine
import com.hopcape.odo.infrastructure.ai.parsing.OcrRowComposer
import kotlinx.coroutines.tasks.await

/**
 * The on-device [DocumentExtractor]: ML Kit reads the text off a vehicle paper,
 * [DocumentTextParser] decides what it means. No photo leaves the device.
 *
 * The bill's twin, and deliberately simpler than it. A bill's hard part is its money column,
 * which is why that reader takes a second pass over the right-hand strip at double scale. A
 * document has no money column — what it has is a date printed beside a label, in a form
 * whose columns OCR reports as separate runs, so composing runs back into printed rows is
 * the whole trick. [OcrRowComposer] already does that.
 *
 * Uploaded PDFs go through the same reader: a policy emailed as a PDF has its first page
 * drawn into an image first ([StoredPageRenderer]), and everything after that is identical.
 *
 * Failures split the way they do everywhere: bytes the device lost, a PDF that will not
 * render, or an image that will not decode are [DomainError.ScanUnreadable] (retake), the
 * recogniser itself failing is [DomainError.ScanUnavailable] (retaking will not help).
 */
internal class MlKitDocumentExtractor(
    private val files: PlatformFileStore,
    private val pages: StoredPageRenderer,
    private val parser: DocumentTextParser,
    private val telemetry: AiTelemetry,
) : DocumentExtractor {

    // One recogniser for the app's lifetime — this is a Koin single, and the client holds
    // the loaded model, which is exactly what should not be re-created per scan.
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Stateless rules; a field rather than a dependency because there is nothing to swap.
    private val composer = OcrRowComposer()

    override suspend fun extract(image: ScannedImage): Either<DomainError, ExtractedDocument> =
        telemetry.span(AiTelemetry.DOCUMENT) {
            either {
                // A PDF holds no pixels to recognise, so its first page is drawn into some.
                // Owners upload policies far more often than they photograph them, so this
                // is the ordinary path for insurance, not a fallback.
                val bytes = when (StoredFileKinds.of(image.storageKey)) {
                    StoredFileKind.PDF -> pages.firstPageAsPng(image.storageKey)
                        ?: raise(DomainError.ScanUnreadable)

                    else -> files.bytes(image.storageKey)
                        .mapLeft { DomainError.ScanUnreadable }
                        .bind()
                }
                ensure(bytes.isNotEmpty()) { DomainError.ScanUnreadable }

                val decoded = decodeBounded(bytes)
                ensureNotNull(decoded) { DomainError.ScanUnreadable }
                val sized = decoded.uprightFrom(bytes).sizedForOcr()
                // A photographed paper is often lit unevenly; the stretch is what makes a
                // washed-out expiry line readable. Blur is not measured here: unlike a
                // bill's amounts, a date either reads or it does not, and the review screen
                // already refuses to save without one.
                val prepared = sized.contrastStretched(sized.graySample())

                val rows = Either.catch { readRows(prepared) }
                    .onLeft { telemetry.malformed(AiTelemetry.DOCUMENT, it) }
                    .mapLeft { DomainError.ScanUnavailable }
                    .bind()

                val document = parser.parse(image.id, rows)
                telemetry.extracted(
                    target = AiTelemetry.DOCUMENT,
                    confidencePercent = document.confidence.percent,
                    manualReview = document.requiresManualReview,
                    engine = AiTelemetry.ENGINE_MLKIT,
                )
                document
            }
        }

    /**
     * The paper's printed rows.
     *
     * A form prints "Valid Upto" and its date in separate columns, and ML Kit reports each
     * as its own run. Read run by run, the label and the date never meet and the date looks
     * unlabelled; composed back into rows, they sit on one line and the label applies.
     */
    private suspend fun readRows(prepared: Bitmap): List<String> =
        composer.rows(recognize(prepared).toOcrLines())

    private suspend fun recognize(bitmap: Bitmap): Text =
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()

    /**
     * ML Kit's lines with the boxes the composer groups them by. A run with no box is kept
     * with a zero box rather than dropped — its text may still hold the expiry, and the
     * composer puts unplaceable rows through on their own.
     */
    private fun Text.toOcrLines(): List<OcrLine> = textBlocks
        .flatMap { it.lines }
        .map { line ->
            val box = line.boundingBox
            OcrLine(
                text = line.text,
                left = box?.left ?: 0,
                top = box?.top ?: 0,
                right = box?.right ?: 0,
                bottom = box?.bottom ?: 0,
            )
        }
}
