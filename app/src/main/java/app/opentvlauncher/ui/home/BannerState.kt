package app.opentvlauncher.ui.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.opentvlauncher.data.BannerSlide

/** Which banner slide shows. The rotation wraps from the last slide to the first. */
@Stable
class BannerState(val slides: List<BannerSlide>, start: Int = 0) {
    init {
        require(slides.isNotEmpty()) { "The banner needs at least one slide" }
    }

    var index by mutableIntStateOf(start.coerceIn(0, slides.lastIndex))
        private set

    val current: BannerSlide get() = slides[index]

    /** The slides on either side of the current one, loaded ahead of time. */
    val neighbours: List<BannerSlide> get() = listOf(slides[(index + 1) % slides.size], slides[(index - 1 + slides.size) % slides.size]).distinct()

    fun next() {
        index = (index + 1) % slides.size
    }

    /** Shows slide [index], as when its dot takes focus. */
    fun show(index: Int) {
        this.index = index.coerceIn(0, slides.lastIndex)
    }
}
