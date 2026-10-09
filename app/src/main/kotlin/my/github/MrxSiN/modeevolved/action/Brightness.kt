package my.github.MrxSiN.modeevolved.action

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The brightness slider's scale, as Android's Settings and System UI draw it.
 *
 * The slider is not linear: it follows the hybrid log-gamma curve of SettingsLib's
 * `BrightnessUtils`, so "50 %" here lands where the slider's middle does, not at half the
 * display's power. The display itself takes a linear value between its minimum and maximum.
 */
object Brightness {

    private const val R = 0.5f
    private const val A = 0.17883277f
    private const val B = 0.28466892f
    private const val C = 0.55991073f

    /** [percent] of the slider, as the linear brightness in [min]..[max] it stands for. */
    fun percentToLinear(percent: Int, min: Float, max: Float): Float {
        val gamma = percent.coerceIn(0, 100) / 100f
        val ret = if (gamma <= R) (gamma / R).let { it * it } else exp((gamma - C) / A) + B
        return min + (max - min) * (ret.coerceIn(0f, 12f) / 12f)
    }

    /** A linear brightness as a whole number, so it can be held, compared and stored like any setting. */
    fun toMicro(linear: Float): Int = (linear * 1_000_000f).roundToInt()

    fun fromMicro(micro: Int): Float = micro / 1_000_000f
}
