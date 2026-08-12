package com.hopcape.odo.core.platform.camera

/**
 * Finds a paper's outline in a small luminance grid.
 *
 * The whole approach leans on one fact about the scene: a bill is a bright, roughly
 * rectangular patch against whatever darker surface it is lying on. So: split the grid
 * into bright and dark with Otsu's threshold, take the largest bright region, and call its
 * four extreme points the corners. No Sobel, no Hough — on the ~5,000-cell grids the
 * analyser feeds this, the simple version runs every frame without breaking a sweat, and
 * it is pure Kotlin, which is what makes it unit-testable off the device.
 *
 * Honest about its limits: a white bill on a white desk has no boundary to find, and the
 * answer is null rather than a guess. The screen keeps its static frame; the owner aligns
 * by eye like before.
 *
 * **Not thread-safe.** The working buffers are instance fields reused across calls —
 * this runs per camera frame, and reallocating ~30 KB of scratch every frame is the
 * analyser's dominant garbage. Each caller owns its instance and calls serially, which
 * both the frame analyser (CameraX serializes frames) and the cropper (one call) do.
 */
internal class QuadDetector {

    private val histogram = IntArray(LEVELS)
    private var bright = BooleanArray(0)
    private var visited = BooleanArray(0)
    private var stack = IntArray(0)

    /**
     * The outline in [luma] (a [width] × [height] grid, row-major, values 0..255), or null
     * when nothing paper-like is there. [luma] may be larger than the grid — a caller
     * reusing a buffer across frames passes it as-is and only the grid's cells are read.
     */
    fun detect(luma: IntArray, width: Int, height: Int): DetectedQuad? {
        val size = width * height
        require(luma.size >= size) { "grid size mismatch" }
        if (width < MIN_GRID_SIDE || height < MIN_GRID_SIDE) return null

        val threshold = otsuThreshold(luma, size) ?: return null
        if (bright.size < size) {
            bright = BooleanArray(size)
            visited = BooleanArray(size)
            stack = IntArray(size)
        }
        for (index in 0 until size) {
            bright[index] = luma[index] >= threshold
            visited[index] = false
        }

        // One pass: flood-fill each bright region, keeping only the largest one's size and
        // its four extreme cells — the whole region is never materialised.
        var bestCount = 0
        var bestTopLeft = -1
        var bestTopRight = -1
        var bestBottomRight = -1
        var bestBottomLeft = -1

        for (start in 0 until size) {
            if (!bright[start] || visited[start]) continue

            var count = 0
            var minSum = Int.MAX_VALUE
            var maxSum = Int.MIN_VALUE
            var minDiff = Int.MAX_VALUE
            var maxDiff = Int.MIN_VALUE
            var topLeft = start
            var topRight = start
            var bottomRight = start
            var bottomLeft = start

            var top = 0
            visited[start] = true
            stack[top++] = start
            while (top > 0) {
                val index = stack[--top]
                count++
                val x = index % width
                val y = index / width
                if (x + y < minSum) { minSum = x + y; topLeft = index }
                if (x + y > maxSum) { maxSum = x + y; bottomRight = index }
                if (x - y > maxDiff) { maxDiff = x - y; topRight = index }
                if (x - y < minDiff) { minDiff = x - y; bottomLeft = index }

                if (x > 0) {
                    val left = index - 1
                    if (bright[left] && !visited[left]) { visited[left] = true; stack[top++] = left }
                }
                if (x < width - 1) {
                    val right = index + 1
                    if (bright[right] && !visited[right]) { visited[right] = true; stack[top++] = right }
                }
                if (index >= width) {
                    val above = index - width
                    if (bright[above] && !visited[above]) { visited[above] = true; stack[top++] = above }
                }
                if (index < size - width) {
                    val below = index + width
                    if (bright[below] && !visited[below]) { visited[below] = true; stack[top++] = below }
                }
            }

            if (count > bestCount) {
                bestCount = count
                bestTopLeft = topLeft
                bestTopRight = topRight
                bestBottomRight = bottomRight
                bestBottomLeft = bottomLeft
            }
        }

        if (bestCount == 0) return null
        val fraction = bestCount.toFloat() / size
        if (fraction < MIN_AREA_FRACTION || fraction > MAX_AREA_FRACTION) return null

        fun point(index: Int) = QuadPoint(
            x = (index % width + 0.5f) / width,
            y = (index / width + 0.5f) / height,
        )
        val quad = DetectedQuad.fromCorners(
            listOf(point(bestTopLeft), point(bestTopRight), point(bestBottomRight), point(bestBottomLeft)),
            frameAspectRatio = width.toFloat() / height,
        ) ?: return null

        // A stringy or degenerate region has extreme points but no real face — the quad
        // they span covers far less than the region suggests it should.
        if (quad.shoelaceArea() < fraction * MIN_QUAD_COVERAGE) return null
        return quad
    }

    /**
     * Otsu's threshold: the split of the histogram that best separates two brightness
     * classes. Null when the image has no meaningful split — one flat surface, no paper.
     */
    private fun otsuThreshold(luma: IntArray, size: Int): Int? {
        histogram.fill(0)
        for (index in 0 until size) histogram[luma[index].coerceIn(0, LEVELS - 1)]++

        val total = size
        var sumAll = 0L
        for (level in 0 until LEVELS) sumAll += level.toLong() * histogram[level]

        var sumBelow = 0L
        var countBelow = 0
        var bestVariance = 0.0
        var bestThreshold = -1

        for (level in 0 until LEVELS) {
            countBelow += histogram[level]
            if (countBelow == 0) continue
            val countAbove = total - countBelow
            if (countAbove == 0) break
            sumBelow += level.toLong() * histogram[level]

            val meanBelow = sumBelow.toDouble() / countBelow
            val meanAbove = (sumAll - sumBelow).toDouble() / countAbove
            val variance = countBelow.toDouble() * countAbove * (meanBelow - meanAbove) * (meanBelow - meanAbove)
            if (variance > bestVariance) {
                bestVariance = variance
                bestThreshold = level
            }
        }
        return if (bestThreshold < 0) null else bestThreshold + 1
    }

    private companion object {
        const val LEVELS = 256
        const val MIN_GRID_SIDE = 16

        /** Below this the bright patch is a highlight, not a paper. */
        const val MIN_AREA_FRACTION = 0.12f

        /** Above this the "paper" is the whole frame and there is no edge to mark. */
        const val MAX_AREA_FRACTION = 0.95f

        /** The corner quad must cover at least this much of what the region's size implies. */
        const val MIN_QUAD_COVERAGE = 0.5f
    }
}

/** The quad's area by the shoelace formula, in normalized units. */
internal fun DetectedQuad.shoelaceArea(): Float {
    val points = corners
    var doubled = 0f
    for (index in points.indices) {
        val a = points[index]
        val b = points[(index + 1) % points.size]
        doubled += a.x * b.y - b.x * a.y
    }
    return kotlin.math.abs(doubled) / 2f
}
