package app.opentvlauncher.tv

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract
import app.opentvlauncher.home.Video

/**
 * Rows that other apps publish as TV channels (preview channels): YouTube's "Recommended" and
 * Stremio's "Continue Watching". Reading them needs READ_TV_LISTINGS, granted once with adb.
 * Without it, or when an app publishes nothing, the rows are empty and the screen hides them.
 */
object TvChannels {
    const val YOUTUBE = "com.google.android.youtube.tv"
    const val STREMIO = "com.stremio.one"
    private const val PROGRAM_LIMIT = 20

    // The SDK has no public constant for it.
    private const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

    data class Rows(val youtube: List<Video>, val stremio: List<Video>)

    fun read(context: Context): Rows {
        if (context.checkSelfPermission(READ_TV_LISTINGS) != PackageManager.PERMISSION_GRANTED) {
            return Rows(emptyList(), emptyList())
        }
        val resolver = context.contentResolver
        val channels = runCatching { channels(resolver) }.getOrDefault(emptyList())
        // Only the named channel: YouTube's Subscriptions or Trending never stand in for Recommended.
        fun row(pkg: String, name: String) = channels.firstOrNull { it.packageName == pkg && it.name.contains(name, ignoreCase = true) }
            ?.let { runCatching { programs(resolver, it.id) }.getOrNull() }.orEmpty()
        return Rows(youtube = row(YOUTUBE, "recommended"), stremio = row(STREMIO, "continue"))
    }

    private data class Channel(val id: Long, val name: String, val packageName: String)

    private fun channels(resolver: ContentResolver): List<Channel> {
        val projection = arrayOf(
            TvContract.Channels._ID,
            TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContract.Channels.COLUMN_PACKAGE_NAME,
            TvContract.Channels.COLUMN_TYPE,
        )
        return resolver.query(TvContract.Channels.CONTENT_URI, projection, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    if (c.string(TvContract.Channels.COLUMN_TYPE) != TvContract.Channels.TYPE_PREVIEW) continue
                    add(
                        Channel(
                            id = c.getLong(c.getColumnIndexOrThrow(TvContract.Channels._ID)),
                            name = c.string(TvContract.Channels.COLUMN_DISPLAY_NAME).orEmpty(),
                            packageName = c.string(TvContract.Channels.COLUMN_PACKAGE_NAME).orEmpty(),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    private fun programs(resolver: ContentResolver, channelId: Long): List<Video> {
        val projection = arrayOf(
            TvContract.PreviewPrograms.COLUMN_TITLE,
            TvContract.PreviewPrograms.COLUMN_AUTHOR,
            TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
            TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
            TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
            TvContract.PreviewPrograms.COLUMN_INTENT_URI,
            TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,
            TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            TvContract.PreviewPrograms.COLUMN_BROWSABLE,
            TvContract.PreviewPrograms.COLUMN_CONTENT_ID,
            TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID,
        )
        val uri = TvContract.buildPreviewProgramsUriForChannel(channelId)
        return resolver.query(uri, projection, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext() && size < PROGRAM_LIMIT) {
                    if (c.long(TvContract.PreviewPrograms.COLUMN_BROWSABLE) == 0L) continue
                    val title = c.string(TvContract.PreviewPrograms.COLUMN_TITLE)?.takeIf { it.isNotBlank() } ?: continue
                    val duration = c.long(TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS)?.takeIf { it > 0 }
                    val position = c.long(TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)?.takeIf { it > 0 }
                    val thumbnail = c.string(TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI)?.takeIf { it.isNotBlank() }
                    val poster = c.string(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI)?.takeIf { it.isNotBlank() }
                    val intentUri = c.string(TvContract.PreviewPrograms.COLUMN_INTENT_URI)
                    add(
                        Video(
                            title = title,
                            subtitle = (c.string(TvContract.PreviewPrograms.COLUMN_AUTHOR)
                                ?: c.string(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION))?.takeIf { it.isNotBlank() },
                            image = thumbnail ?: poster,
                            poster = poster?.takeIf { it != thumbnail && thumbnail != null },
                            imdb = imdbId(
                                intentUri,
                                c.string(TvContract.PreviewPrograms.COLUMN_CONTENT_ID),
                                c.string(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID),
                            ),
                            intentUri = intentUri,
                            durationMillis = duration,
                            progress = if (duration != null && position != null) (position.toFloat() / duration).coerceIn(0f, 1f) else null,
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    /** The first IMDb id ("tt" and 7 or more digits) in any of [texts], such as Stremio's link. */
    fun imdbId(vararg texts: String?): String? = texts.firstNotNullOfOrNull { text -> text?.let { IMDB.find(it)?.value } }

    private val IMDB = Regex("tt\\d{7,}")

    private fun Cursor.string(column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

    private fun Cursor.long(column: String): Long? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)
}
