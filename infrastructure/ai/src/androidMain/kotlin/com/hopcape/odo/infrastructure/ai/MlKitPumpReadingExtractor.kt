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
import com.hopcape.odo.core.domain.scan.PumpReadingExtractor
import com.hopcape.odo.core.domain.scan.model.ExtractedPumpReading
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.infrastructure.ai.observability.AiTelemetry
import com.hopcape.odo.infrastructure.ai.parsing.OcrLine
import com.hopcape.odo.infrastructure.ai.parsing.OcrRowComposer
import com.hopcape.odo.infrastructure.ai.parsing.PumpTextParser
import kotlinx.coroutines.tasks.await

/**
 * The on-device [PumpReadingExtractor]: ML Kit reads the pump's display,
 * [PumpTextParser] decides what the numbers mean. No photo leaves the device.
 *
 * **This is the least certain reader in the app, and it is built to fail usefully.** ML Kit's
 * text recogniser is trained on print and handwriting, and a pump's seven-segment digits are
 * neither. Some displays read cleanly, some read partially, and some do not read at all.
 *
 * Three things make that acceptable. A partial read is still returned, because one number
 * saves most of the typing. The parser recovers a missing third value from the other two and
 * cross-checks all three when it has them, so a misread digit shows up rather than being
 * presented as fact. And the screen behind this always offers "enter the numbers myself",
 * which is the same form the prefill channel uses — a failed scan costs one tap, not the fill.
 *
 * The image preparation is the bill reader's, not the document reader's: contrast stretching
 * is what makes a glare-washed display legible, and it is the same problem a dim receipt has.
 */
internal class MlKitPumpReadingExtractor(
    private val files: PlatformFileStore,
    private val parser: PumpTextParser,
    private val telemetry: AiTelemetry,
) : PumpReadingExtractor {

    // One recogniser for the app's lifetime — this is a Koin single, and the client holds
    // the loaded model, which is exactly what should not be re-created per scan.
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Stateless rules; a field rather than a dependency because there is nothing to swap.
    private val composer = OcrRowComposer()

    override suspend fun extract(image: ScannedImage): Either<DomainError, ExtractedPumpReading> =
        telemetry.span(AiTelemetry.PUMP) {
            either {
                val bytes = files.bytes(image.storageKey)
                    .mapLeft { DomainError.ScanUnreadable }
                    .bind()
                ensure(bytes.isNotEmpty()) { DomainError.ScanUnreadable }

                val decoded = decodeBounded(bytes)
                ensureNotNull(decoded) { DomainError.ScanUnreadable }
                val sized = decoded.uprightFrom(bytes).sizedForOcr()
                // A lit display against a dark housing is the worst case for a recogniser
                // that expects ink on paper; the stretch is what separates the digits from
                // the glow around them.
                val prepared = sized.contrastStretched(sized.graySample())

                val rows = Either.catch { readRows(prepared) }
                    .onLeft { telemetry.malformed(AiTelemetry.PUMP, it) }
                    .mapLeft { DomainError.ScanUnavailable }
                    .bind()

                val reading = parser.parse(image.id, rows)
                // A frame with no numbers at all is a miss, not a partial read. Returning it
                // would put an empty confirm step in front of the owner and call it a scan.
                ensure(!reading.isEmpty) { DomainError.ScanUnreadable }

                telemetry.extracted(
                    target = AiTelemetry.PUMP,
                    // The share of the three numbers that read is the only confidence a pump
                    // display gives, and it is the number worth watching while this reader is
                    // unproven.
                    confidencePercent = reading.readCount * 100 / TOTAL_FIELDS,
                    manualReview = !reading.crossChecked,
                    engine = AiTelemetry.ENGINE_MLKIT,
                )
                reading
            }
        }

    /**
     * The display's printed rows.
     *
     * A pump prints its label and its value in separate columns, and ML Kit reports each as
     * its own run. Read run by run, "AMOUNT" and "1500.00" never meet and every number looks
     * unlabelled; composed back into rows they sit on one line, and the label is what tells
     * the parser which of the three it is looking at.
     */
    private suspend fun readRows(prepared: Bitmap): List<String> =
        composer.rows(recognize(prepared).toOcrLines())

    private suspend fun recognize(bitmap: Bitmap): Text =
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()

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

    private companion object {
        /** Amount, volume and rate — what a pump display always shows. */
        const val TOTAL_FIELDS = 3
    }
}
