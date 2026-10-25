package app.opentvlauncher.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * M3 Expressive springs from the design, section 1 "Motion". The standard scheme (damping 0.9)
 * keeps focus calm under key repeat. Springs stop cleanly when a new key arrives.
 */
object LauncherMotion {
    /** Focus scale, corner morph and row header growth: standard spatial fast. */
    fun <T> focus(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)

    /** Page scroll between rows: standard spatial default. */
    fun <T> scroll(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)

    /** Fill, text colour and row dim: effects fast. */
    fun <T> effectsFast(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)

    /** Background tint and banner crossfade: effects slow. */
    fun <T> effectsSlow(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)

    /** Button press: expressive spatial fast. This is the only bounce. */
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    const val BANNER_SLIDE_MILLIS = 12_000
    const val BANNER_DRIFT_SCALE = 1.02f

    /**
     * How often the dot fill and the art drift move. Over 12 s the dot grows by about 1 px per
     * 200 ms and the drift by under 1 px, so 5 updates a second look the same as 60 and the
     * screen stays nearly idle at rest.
     */
    const val BANNER_TICK_MILLIS = 200

    /** On the banner, a slide changes no sooner than this after the last key (25 Oct 2026). */
    const val BANNER_KEY_WAIT_MILLIS = 5_000

    const val SCREENSAVER_SLIDE_MILLIS = 40_000
    const val SCREENSAVER_CROSSFADE_MILLIS = 2_000
    const val SCREENSAVER_PAN = 0.03f
    const val SCREENSAVER_ZOOM = 0.06f
}
