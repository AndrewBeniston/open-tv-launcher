package app.opentvlauncher.ui.art

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.opentvlauncher.ui.theme.Label
import app.opentvlauncher.ui.theme.LocalLauncherType
import app.opentvlauncher.ui.theme.SquircleShape

// Text that is part of painted art ([PaintedArt]): thumbnail words and poster titles. Real
// thumbnails and posters carry their own lettering. It is part of the picture, so TalkBack
// skips it.

/** Big words on a painted thumbnail. */
@Composable
fun ThumbnailLettering(art: PaintedArt?, modifier: Modifier = Modifier) {
    val lines = art?.thumbLines.orEmpty().ifEmpty { return }
    val base = LocalLauncherType.current.placeholderLogo
    Column(modifier.clearAndSetSemantics {}.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { line ->
            val style = base.copy(fontSize = line.size.sp, lineHeight = (line.size * 0.95f).sp, letterSpacing = (-0.02).em)
            val badge = line.badge
            Label(
                text = line.text,
                style = style,
                color = line.color,
                modifier = if (badge != null) {
                    Modifier.background(badge, SquircleShape(3.dp)).padding(horizontal = 5.dp, vertical = 2.dp)
                } else {
                    Modifier
                },
            )
        }
    }
}

/** The film title printed on a painted poster. */
@Composable
fun PosterLettering(art: PaintedArt?, title: String, modifier: Modifier = Modifier) {
    val size = art?.posterTitleSize ?: return
    val style = LocalLauncherType.current.placeholderLogo.copy(
        fontSize = size.sp,
        lineHeight = (size * 1.05f).sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.12.em,
        textAlign = TextAlign.Center,
    )
    Column(modifier.clearAndSetSemantics {}.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Label(text = title, style = style, color = Color.White, maxLines = 3)
    }
}
