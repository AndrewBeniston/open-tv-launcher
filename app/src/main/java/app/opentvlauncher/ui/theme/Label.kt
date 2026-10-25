package app.opentvlauncher.ui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * Plain text for the launcher. It uses foundation's BasicText, because androidx.tv.material3.Text
 * puts every text in its own offscreen layer, which costs GPU memory on the Chromecast.
 * [color] is read at draw time, so an animated colour does not recompose the text.
 */
@Composable
fun Label(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    color: ColorProducer,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        color = color,
    )
}

@Composable
fun Label(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
    )
}
