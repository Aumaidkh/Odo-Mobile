package com.hopcape.odo.infrastructure.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hopcape.odo.core.domain.scan.BillExtractor
import com.hopcape.odo.core.domain.scan.model.ExtractedBill
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.camera.uprightBy
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.infrastructure.ai.observability.AiTelemetry
import com.hopcape.odo.infrastructure.ai.parsing.BillTextParser
import com.hopcape.odo.infrastructure.ai.parsing.OcrLine
import com.hopcape.odo.infrastructure.ai.parsing.OcrRowComposer
import com.hopcape.odo.infrastructure.ai.parsing.isMoneyShaped
import com.hopcape.odo.infrastructure.ai.parsing.mergeAmountStrip
import com.hopcape.odo.infrastructure.ai.parsing.stripPassRedundant
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.tasks.await

/**
 * The on-device [BillExtractor]: ML Kit reads the text, [BillTextParser] decides what it
 * means. No photo leaves the device and no backend needs to exist.
 *
 * Recognition runs on a photo prepared for it — upright, sized into ML Kit's sweet spot,
 * contrast-stretched when the shot is washed out — and then runs a **second time** on the
 * right-hand strip at double scale, unless the first pass already read the money column
 * cleanly. Amounts live in that strip, handwritten digits are the weakest read on the
 * page, and doubling their pixel size is the single cheapest accuracy win available.
 *
 * The trade is honesty for reach: OCR plus heuristics is well short of a model-backed
 * reader, so the parser reports every result as needing the owner's review.
 *
 * Failures split the same way as everywhere else: bytes the device lost or an image that
 * will not decode are [DomainError.ScanUnreadable] (retake), the recogniser itself failing
 * is [DomainError.ScanUnavailable] (retaking will not help).
 */
internal class MlKitBillExtractor(
    private val files: PlatformFileStore,
    private val parser: BillTextParser,
    private val telemetry: AiTelemetry,
) : BillExtractor {

    // One recogniser for the app's lifetime — this class is a Koin single, and the client
    // holds the loaded model, which is exactly what should not be re-created per scan.
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Stateless rules; a field rather than a dependency because there is nothing to swap.
    private val composer = OcrRowComposer()

    override suspend fun extract(image: ScannedImage): Either<DomainError, ExtractedBill> =
        telemetry.span(AiTelemetry.BILL) {
            either {
                val bytes = files.bytes(image.storageKey)
                    .mapLeft { DomainError.ScanUnreadable }
                    .bind()
                ensure(bytes.isNotEmpty()) { DomainError.ScanUnreadable }

                val decoded = decodeBounded(bytes)
                ensureNotNull(decoded) { DomainError.ScanUnreadable }
                val sized = decoded.uprightFrom(bytes).sizedForOcr()
                // One small grayscale copy feeds both checks below. Blur is measured
                // before the contrast stretch — stretching amplifies whatever gradients
                // exist, which would flatter a genuinely soft photo.
                val gray = sized.graySample()
                val blurry = gray.measuresBlurry()
                val prepared = sized.contrastStretched(gray)

                val rows = Either.catch { readRows(prepared) }
                    .onLeft { telemetry.malformed(AiTelemetry.BILL, it) }
                    .mapLeft { DomainError.ScanUnavailable }
                    .bind()

                val bill = parser.parse(image.id, rows).withPhotoQuality(blurry)
                telemetry.extracted(
                    target = AiTelemetry.BILL,
                    confidencePercent = bill.confidence.percent,
                    manualReview = bill.requiresManualReview,
                    engine = AiTelemetry.ENGINE_MLKIT,
                )
                bill
            }
        }

    /** One recognised run with its true geometry — centre, size, and baseline angle. */
    private data class Run(
        val text: String,
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float,
        val angleDegrees: Float,
    ) {
        val top: Float get() = centerY - height / 2f
        val bottom: Float get() = centerY + height / 2f
    }

    /**
     * Both recognition passes, levelled and composed into the bill's rows. The strip pass
     * is skipped when the full pass's money column is already clean digits, and best
     * effort otherwise — a strip that fails to read leaves the full pass's answer
     * standing. The substitution rules themselves live in the parsing package, under test.
     */
    private suspend fun readRows(prepared: Bitmap): List<String> {
        val (fullRuns, unboxed) = runsOf(recognize(prepared))
        val tilt = fullRuns.pageTilt()
        val fullLines = fullRuns.levelledBy(tilt).toLines()

        val stripLeft = (prepared.width * STRIP_START_FRACTION).toInt()
        val lines = if (stripPassRedundant(fullLines, stripLeft)) {
            fullLines
        } else {
            val stripRuns = runCatching { amountStripRuns(prepared, stripLeft) }.getOrDefault(emptyList())
            mergeAmountStrip(fullLines, stripRuns.levelledBy(tilt).toLines(), stripLeft)
        }
        return composer.rows(lines) + unboxed
    }

    private suspend fun recognize(bitmap: Bitmap): Text =
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()

    /**
     * ML Kit's runs with their true geometry, from corner points where available. The
     * corner points matter: on a tilted photo the axis-aligned bounding box inflates to
     * the run's envelope, and envelope heights are what glue neighbouring rows together.
     * Box-less runs (rare) are kept aside as their own rows.
     */
    private fun runsOf(text: Text): Pair<List<Run>, List<String>> {
        val boxed = mutableListOf<Run>()
        val unboxed = mutableListOf<String>()
        text.textBlocks.flatMap { it.lines }.forEach { line ->
            val corners = line.cornerPoints
            val box = line.boundingBox
            when {
                corners != null && corners.size == 4 -> {
                    val centerX = corners.map { it.x }.average().toFloat()
                    val centerY = corners.map { it.y }.average().toFloat()
                    val width = hypot(
                        (corners[1].x - corners[0].x).toFloat(),
                        (corners[1].y - corners[0].y).toFloat(),
                    )
                    val height = hypot(
                        (corners[3].x - corners[0].x).toFloat(),
                        (corners[3].y - corners[0].y).toFloat(),
                    )
                    val angle = Math.toDegrees(
                        atan2(
                            (corners[1].y - corners[0].y).toDouble(),
                            (corners[1].x - corners[0].x).toDouble(),
                        ),
                    ).toFloat()
                    boxed += Run(line.text, centerX, centerY, width, height, angle)
                }

                box != null -> boxed += Run(
                    text = line.text,
                    centerX = box.exactCenterX(),
                    centerY = box.exactCenterY(),
                    width = box.width().toFloat(),
                    height = box.height().toFloat(),
                    angleDegrees = 0f,
                )

                else -> unboxed += line.text
            }
        }
        return boxed to unboxed
    }

    /** The rotation that puts a tilted page's rows back on the level, or null when flat. */
    private data class PageTilt(
        val cosine: Float,
        val sine: Float,
        val pivotX: Float,
        val pivotY: Float,
    )

    /**
     * The page's tilt, from the median baseline angle of the full pass's runs.
     *
     * This is what keeps a slanted bill's rows apart: banding by vertical overlap is only
     * sound when rows are horizontal, and on a tilted page each row's far end dips into
     * the next row's band — amounts land one row off, which is worse than missing.
     */
    private fun List<Run>.pageTilt(): PageTilt? {
        if (size < MIN_RUNS_FOR_DESKEW) return null
        val angles = map { it.angleDegrees }.sorted()
        val median = angles[angles.size / 2]
        if (abs(median) < MIN_DESKEW_DEGREES || abs(median) > MAX_DESKEW_DEGREES) return null

        val radians = Math.toRadians(-median.toDouble())
        return PageTilt(
            cosine = cos(radians).toFloat(),
            sine = sin(radians).toFloat(),
            pivotX = map { it.centerX }.average().toFloat(),
            pivotY = map { it.centerY }.average().toFloat(),
        )
    }

    /** Every centre rotated by [tilt]. Both passes get the same rotation, so rows line up. */
    private fun List<Run>.levelledBy(tilt: PageTilt?): List<Run> {
        if (tilt == null) return this
        return map { run ->
            val dx = run.centerX - tilt.pivotX
            val dy = run.centerY - tilt.pivotY
            run.copy(
                centerX = tilt.pivotX + dx * tilt.cosine - dy * tilt.sine,
                centerY = tilt.pivotY + dx * tilt.sine + dy * tilt.cosine,
            )
        }
    }

    private fun List<Run>.toLines(): List<OcrLine> = map { run ->
        OcrLine(
            text = run.text,
            left = (run.centerX - run.width / 2f).roundToInt(),
            top = run.top.roundToInt(),
            right = (run.centerX + run.width / 2f).roundToInt(),
            bottom = run.bottom.roundToInt(),
        )
    }

    /**
     * The right-hand strip re-read at 2× and mapped back to full-image coordinates. Only
     * money-shaped tokens survive: the strip's left edge cuts through long labels, and a
     * truncated word merged back would duplicate the full pass's text.
     */
    private suspend fun amountStripRuns(prepared: Bitmap, stripLeft: Int): List<Run> {
        val stripWidth = prepared.width - stripLeft
        if (stripWidth < MIN_STRIP_WIDTH) return emptyList()

        val strip = Bitmap.createBitmap(prepared, stripLeft, 0, stripWidth, prepared.height)
        val doubled = Bitmap.createScaledBitmap(strip, strip.width * 2, strip.height * 2, true)

        val (runs, _) = runsOf(recognize(doubled))
        return runs
            .filter { it.text.isMoneyShaped() }
            .map { run ->
                run.copy(
                    centerX = stripLeft + run.centerX / 2f,
                    centerY = run.centerY / 2f,
                    width = run.width / 2f,
                    height = run.height / 2f,
                )
            }
    }

    /**
     * A blurry photo reads worse than the parser can tell from text alone, so the fact is
     * carried on the result and the score pays for it. The penalty keeps the number
     * honest: parse coverage measures what matched, not whether the digits under the blur
     * were actually right.
     */
    private fun ExtractedBill.withPhotoQuality(blurry: Boolean): ExtractedBill =
        if (blurry) {
            copy(confidence = confidence.reducedBy(BLUR_CONFIDENCE_PENALTY), photoBlurry = true)
        } else {
            this
        }

    private companion object {
        /* The money column: everything right of this fraction of the width. */
        const val STRIP_START_FRACTION = 0.55f
        const val MIN_STRIP_WIDTH = 200

        /* Deskew: below the floor the tilt is noise, above the ceiling it is not a tilt. */
        const val MIN_RUNS_FOR_DESKEW = 4
        const val MIN_DESKEW_DEGREES = 1.0f
        const val MAX_DESKEW_DEGREES = 25.0f

        /** How many confidence points a blurry photo costs the read. */
        const val BLUR_CONFIDENCE_PENALTY = 15
    }
}
