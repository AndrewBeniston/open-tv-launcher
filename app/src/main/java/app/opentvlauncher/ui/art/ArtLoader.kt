package app.opentvlauncher.ui.art

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap

/** Fetches and decodes one image address. Null when the image is missing or the network fails. */
fun interface ArtLoader {
    /**
     * A readable bitmap at least about [width] × [height] pixels where the source allows it.
     * [fit] keeps the whole image (a logo); otherwise the image may be cropped to fill.
     */
    suspend fun bitmap(url: String, width: Int, height: Int, fit: Boolean): Bitmap?
}

/** The app's loader. Without one (tests, previews) only painted art draws. */
val LocalArtLoader = staticCompositionLocalOf<ArtLoader?> { null }

/**
 * Coil does the network, the disk cache and the decode. Its memory cache is off, because
 * ArtCache keeps the finished bitmaps (cropped, faded, at drawn size) in memory.
 */
class CoilArtLoader(private val context: Context, private val images: ImageLoader) : ArtLoader {
    override suspend fun bitmap(url: String, width: Int, height: Int, fit: Boolean): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(width, height)
            .scale(if (fit) Scale.FIT else Scale.FILL)
            // The decoder may shrink by a power of two only. ArtCache does the exact scale.
            .precision(Precision.INEXACT)
            // ArtCache reads the pixels for the tint and draws them into its own bitmap.
            .allowHardware(false)
            .build()
        return (images.execute(request) as? SuccessResult)?.image?.toBitmap()
    }
}
