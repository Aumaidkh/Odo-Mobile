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

    /**
     * Decodes the capture without materialising more pixels than OCR uses. A modern phone
     * writes ~50MP JPEGs; `inSampleSize` halves the decode until the long edge is near
     * [MAX_LONG_EDGE], and [sizedForOcr] does the final exact scale.
     */
    private fun decodeBounded(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sample = 1
        var longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / 2 >= MAX_LONG_EDGE) {
            sample *= 2
            longEdge /= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /**
     * The bitmap with its EXIF rotation applied. CameraX writes portrait captures as
     * landscape pixels plus an orientation tag; OCR on sideways text finds nothing.
     */
    private fun Bitmap.uprightFrom(bytes: ByteArray): Bitmap =
        runCatching { uprightBy(ExifInterface(ByteArrayInputStream(bytes))) }.getOrDefault(this)

    private fun Bitmap.sizedForOcr(): Bitmap {
        val longEdge = maxOf(width, height)
        val target = when {
            longEdge > MAX_LONG_EDGE -> MAX_LONG_EDGE
            longEdge < MIN_LONG_EDGE -> UPSCALED_LONG_EDGE
            else -> return this
        }
        val scale = target.toFloat() / longEdge
        return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    }

    /** A small grayscale copy — computed once, read by both quality checks. */
    private class GraySample(val luma: IntArray, val width: Int, val height: Int)

    private fun Bitmap.graySample(): GraySample {
        val sample = Bitmap.createScaledBitmap(
            this,
            GRAY_SAMPLE_WIDTH,
            (GRAY_SAMPLE_WIDTH * height / width).coerceAtLeast(1),
            true,
        )
        val pixels = IntArray(sample.width * sample.height)
        sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
        val luma = IntArray(pixels.size)
        pixels.forEachIndexed { index, pixel ->
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            luma[index] = (red * 299 + green * 587 + blue * 114) / 1000
        }
        return GraySample(luma, sample.width, sample.height)
    }

    /**
     * Whether the photo is too soft to trust — the variance of a Laplacian over the gray
     * sample. Sharp text produces strong second-order gradients; a focus miss or a shake
     * flattens them. The classic, cheap blur measure.
     */
    private fun GraySample.measuresBlurry(): Boolean {
        if (width < 3 || height < 3) return false

        var sum = 0.0
        var squaredSum = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val laplacian = (4 * luma[index] -
                    luma[index - 1] - luma[index + 1] -
                    luma[index - width] - luma[index + width]).toDouble()
                sum += laplacian
                squaredSum += laplacian * laplacian
                count++
            }
        }
        if (count == 0) return false
        val mean = sum / count
        val variance = squaredSum / count - mean * mean
        return variance < BLUR_VARIANCE_FLOOR
    }

    /**
     * Stretches a narrow brightness range across the full scale — a dim or washed-out
     * photo becomes one with dark ink on light paper, which is what the recogniser is
     * best at. A photo that already spans the range is returned untouched; stretching it
     * further would only amplify noise.
     */
    private fun Bitmap.contrastStretched(gray: GraySample): Bitmap {
        val (low, high) = gray.lumaPercentiles() ?: return this
        if (high - low >= WIDE_LUMA_RANGE) return this

        val scale = LUMA_LEVELS_F / (high - low).coerceAtLeast(MIN_LUMA_RANGE)
        val offset = -low * scale
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, offset,
                    0f, scale, 0f, 0f, offset,
                    0f, 0f, scale, 0f, offset,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(this, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return output
    }

    /** The 2nd and 98th luminance percentiles of the gray sample. */
    private fun GraySample.lumaPercentiles(): Pair<Float, Float>? {
        val total = luma.size
        if (total == 0) return null

        val histogram = IntArray(LUMA_LEVELS)
        luma.forEach { histogram[it]++ }

        var count = 0
        var low = -1f
        var high = -1f
        for (level in 0 until LUMA_LEVELS) {
            count += histogram[level]
            if (low < 0 && count >= total * LOW_PERCENTILE) low = level.toFloat()
            if (high < 0 && count >= total * HIGH_PERCENTILE) {
                high = level.toFloat()
                break
            }
        }
        return if (low < 0 || high <= low) null else low to high
    }

    private companion object {
        /* The money column: everything right of this fraction of the width. */
        const val STRIP_START_FRACTION = 0.55f
        const val MIN_STRIP_WIDTH = 200

        /* Deskew: below the floor the tilt is noise, above the ceiling it is not a tilt. */
        const val MIN_RUNS_FOR_DESKEW = 4
        const val MIN_DESKEW_DEGREES = 1.0f
        const val MAX_DESKEW_DEGREES = 25.0f

        /* Sizing: ML Kit reads best when text is comfortably above its minimum height. */
        const val MAX_LONG_EDGE = 2600
        const val MIN_LONG_EDGE = 1200
        const val UPSCALED_LONG_EDGE = 1700

        /* Photo quality, measured on one shared gray sample. */
        const val GRAY_SAMPLE_WIDTH = 256
        const val BLUR_VARIANCE_FLOOR = 80.0
        const val BLUR_CONFIDENCE_PENALTY = 15

        /* Contrast stretch. */
        const val LUMA_LEVELS = 256
        const val LUMA_LEVELS_F = 255f
        const val WIDE_LUMA_RANGE = 170f
        const val MIN_LUMA_RANGE = 30f
        const val LOW_PERCENTILE = 0.02f
        const val HIGH_PERCENTILE = 0.98f
    }
}
