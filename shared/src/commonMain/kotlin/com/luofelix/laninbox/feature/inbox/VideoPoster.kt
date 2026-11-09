package com.luofelix.laninbox.feature.inbox

import kotlin.math.roundToInt

internal data class VideoPosterSize(val width: Int, val height: Int)

internal fun videoPosterSize(width: Int, height: Int): VideoPosterSize {
    require(width > 0 && height > 0)
    val scale = minOf(1.0, 512.0 / maxOf(width, height))
    return VideoPosterSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
}

internal fun canDecodeLegacyVideoPoster(width: Int?, height: Int?): Boolean =
    width != null && height != null && width > 0 && height > 0 && width.toLong() * height <= 12_000_000

internal fun videoPosterTimeUs(durationMs: Long?): Long =
    if (durationMs == null || durationMs <= 0) -1 else minOf(1_000L, (durationMs - 1) / 2) * 1_000
