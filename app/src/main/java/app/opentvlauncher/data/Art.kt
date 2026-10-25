package app.opentvlauncher.data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.opentvlauncher.ui.art.PaintedArt

/**
 * Where a picture comes from: image addresses tried in order (Metahub first, then Trakt), or
 * art drawn in code ([PaintedArt]), which only the tests use.
 */
@Immutable
data class Art(val urls: List<String> = emptyList(), val painted: PaintedArt? = null) {
    constructor(painted: PaintedArt) : this(emptyList(), painted)

    companion object {
        val None = Art()

        /** A faint warm grey, for art whose own tint is not known. */
        val NeutralTint = Color(0x1F8C8478)
    }
}
