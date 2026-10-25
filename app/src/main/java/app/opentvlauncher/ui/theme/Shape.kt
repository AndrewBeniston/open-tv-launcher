package app.opentvlauncher.ui.theme

import androidx.compose.ui.unit.dp

/**
 * M3 Expressive corner sizes from the design, section 1 "Shape".
 * The corner grows one step when an item gets focus.
 */
object LauncherShapes {
    val extraSmall = 4.dp
    val medium = 12.dp
    val large = 16.dp
    val largeIncreased = 20.dp
    val extraLarge = 28.dp
}

/** Focus shadows from the design, section 1 "Elevation", as Android elevation values. */
object LauncherElevation {
    /** Design: y 12, blur 28, black 60 %. */
    val focus = 16.dp

    /** Design: y 6, blur 16, black 45 %. */
    val button = 8.dp
}
