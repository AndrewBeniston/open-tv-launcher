package app.opentvlauncher.ui.art

import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import app.opentvlauncher.data.Art
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LauncherMotion
import app.opentvlauncher.ui.theme.drawSquircle
import app.opentvlauncher.ui.theme.squircleMask
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws [art] at [size]. Until its bitmap is ready it shows [placeholder], which is the
 * design's colour for a card while its art loads.
 *
 * With a [corner], the art has squircle corners of that many pixels. The art and a cached
 * squircle mask are one shader on a plain rectangle, so the corners need no clip and no path.
 *
 * With [fit], the whole image sits inside [size] at that alignment, as a title logo does.
 * Otherwise the image fills [size] and is cropped. A fitted image smaller than [size] is scaled
 * up to fit, unless [enlarge] is false: then it draws at its own pixel size at most.
 */
@Composable
fun ArtImage(
    art: Art,
    size: DpSize,
    modifier: Modifier = Modifier,
    mask: ArtMask = ArtMask.None,
    placeholder: Color = LauncherColors.surfaceContainer,
    corner: (() -> Float)? = null,
    fit: Alignment? = null,
    enlarge: Boolean = true,
) {
    val density = LocalDensity.current
    val loader = LocalArtLoader.current
    val key = remember(art, size, density, mask, fit != null) { ArtCache.key(art, size, density, mask, fit != null) }
    val bitmap by produceState(key?.let(ArtCache::peek), key) {
        // The state outlives a key change, so read the cache for this key, never the old value.
        value = key?.let(ArtCache::peek)
        if (value == null && key != null) value = ArtCache.load(key, loader)
    }
    // A picture that arrives after the first frame fades in over the placeholder.
    val appear = remember(key) { Animatable(if (key?.let(ArtCache::peek) != null) 1f else 0f) }
    LaunchedEffect(appear, bitmap != null) { if (bitmap != null) appear.animateTo(1f, LauncherMotion.effectsSlow()) }
    val rounded = remember { RoundedArt() }
    Spacer(
        modifier
            .size(size)
            .drawBehind {
                val image = bitmap
                val alpha = appear.value
                val whole = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt())
                if (image == null || alpha < 1f) {
                    if (corner != null) drawSquircle(corner(), placeholder) else drawRect(placeholder)
                }
                when {
                    image == null -> Unit
                    corner != null -> drawRect(rounded.brush(image, this.size, squircleMask(this.size, corner())), alpha = alpha)
                    fit != null -> {
                        // The bitmap may be smaller than its box. Scale it to fit, up only with enlarge, and align it.
                        // Painted art is rendered at a lower resolution, so it always scales.
                        val fill = min(whole.width.toFloat() / image.width, whole.height.toFloat() / image.height)
                        val scale = if (enlarge || art.painted != null) fill else min(1f, fill)
                        val fitted = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt())
                        drawImage(
                            image = image,
                            dstOffset = fit.align(fitted, whole, LayoutDirection.Ltr),
                            dstSize = fitted,
                            alpha = alpha,
                            filterQuality = FilterQuality.Low,
                        )
                    }
                    else -> drawImage(image = image, dstOffset = IntOffset.Zero, dstSize = whole, alpha = alpha, filterQuality = FilterQuality.Low)
                }
            },
    )
}

/**
 * Loads [art] into the cache before it shows, so the next banner slide fades in with its
 * picture. The same arguments as the ArtImage that will draw it.
 */
@Composable
fun PreloadArt(art: Art, size: DpSize, mask: ArtMask = ArtMask.None, fit: Boolean = false) {
    val density = LocalDensity.current
    val loader = LocalArtLoader.current
    val key = remember(art, size, density, mask, fit) { ArtCache.key(art, size, density, mask, fit) }
    LaunchedEffect(key) { if (key != null) ArtCache.load(key, loader) }
}

/**
 * A title logo fitted into [box] at [alignment]. While it loads nothing shows. When there is no
 * logo, [fallback] shows instead, such as the title as text. With [enlarge] false, a logo smaller
 * than [box] keeps its own pixel size.
 */
@Composable
fun LogoImage(
    art: Art,
    box: DpSize,
    alignment: Alignment,
    modifier: Modifier = Modifier,
    mask: ArtMask = ArtMask.None,
    enlarge: Boolean = true,
    fallback: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val loader = LocalArtLoader.current
    val key = remember(art, box, density, mask) { ArtCache.key(art, box, density, mask, fit = true) }
    // Null while the logo loads, true when it is ready, false when there is none.
    val found by produceState(key?.let { ArtCache.peek(it) != null }, key) {
        value = key?.let { ArtCache.load(it, loader) != null } ?: false
    }
    when (found) {
        true -> ArtImage(art, box, modifier, mask = mask, placeholder = Color.Transparent, fit = alignment, enlarge = enlarge)
        false -> fallback()
        null -> Unit
    }
}

/**
 * The art through a squircle mask, as one shader. It is made again only when the bitmap, the
 * size or the mask changes: on each frame of a corner morph, and never at rest.
 */
private class RoundedArt {
    private var image: ImageBitmap? = null
    private var size = Size.Unspecified
    private var art: Shader? = null
    private var mask: BitmapShader? = null
    private var brush: ShaderBrush? = null

    fun brush(image: ImageBitmap, size: Size, mask: BitmapShader): ShaderBrush {
        val art = this.art.takeIf { image === this.image && size == this.size }
            // The bitmap can be smaller than the surface. The shader scales it up to fill it.
            ?: ImageShader(image).apply {
                setLocalMatrix(Matrix().apply { setScale(size.width / image.width, size.height / image.height) })
            }.also {
                this.art = it
                this.image = image
                this.size = size
                this.mask = null
            }
        val brush = this.brush.takeIf { mask === this.mask }
            ?: ShaderBrush(ComposeShader(art, mask, BlendMode.DST_IN)).also {
                this.brush = it
                this.mask = mask
            }
        return brush
    }
}
