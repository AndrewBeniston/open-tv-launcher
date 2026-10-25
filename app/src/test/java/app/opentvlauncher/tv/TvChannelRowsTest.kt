package app.opentvlauncher.tv

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.media.tv.TvContract
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Recommended on YouTube shows YouTube's Recommended channel only. The design has no Subscriptions
 * or Trending rows, and the technical plan hides a row that has nothing to show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TvChannelRowsTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions("android.permission.READ_TV_LISTINGS")
        Robolectric.setupContentProvider(FakeTvProvider::class.java, TvContract.AUTHORITY)
    }

    @Test
    fun withoutRecommendedTheYouTubeRowIsEmpty() {
        FakeTvProvider.channels = listOf(
            FakeChannel(1, "Subscriptions", TvChannels.YOUTUBE, listOf("Only a subscription")),
            FakeChannel(2, "Trending", TvChannels.YOUTUBE, listOf("Only a trend")),
        )
        assertEquals(emptyList<String>(), TvChannels.read(context).youtube.map { it.title })
    }

    @Test
    fun theYouTubeRowComesFromRecommendedWhereverItIsListed() {
        FakeTvProvider.channels = listOf(
            FakeChannel(1, "Subscriptions", TvChannels.YOUTUBE, listOf("Only a subscription")),
            FakeChannel(2, "Recommended", TvChannels.YOUTUBE, listOf("A recommendation")),
            FakeChannel(3, "Trending", TvChannels.YOUTUBE, listOf("Only a trend")),
        )
        assertEquals(listOf("A recommendation"), TvChannels.read(context).youtube.map { it.title })
    }

    data class FakeChannel(val id: Long, val name: String, val packageName: String, val programs: List<String>)

    /** The TV provider's preview channels and programs, as the launcher reads them. */
    class FakeTvProvider : ContentProvider() {
        companion object {
            var channels: List<FakeChannel> = emptyList()
        }

        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor =
            if (uri.pathSegments.first() == "channel") {
                MatrixCursor(
                    arrayOf(
                        TvContract.Channels._ID,
                        TvContract.Channels.COLUMN_DISPLAY_NAME,
                        TvContract.Channels.COLUMN_PACKAGE_NAME,
                        TvContract.Channels.COLUMN_TYPE,
                    ),
                ).apply { channels.forEach { addRow(arrayOf<Any>(it.id, it.name, it.packageName, TvContract.Channels.TYPE_PREVIEW)) } }
            } else {
                val channel = uri.getQueryParameter("channel")!!.toLong()
                MatrixCursor(arrayOf(TvContract.PreviewPrograms.COLUMN_TITLE, TvContract.PreviewPrograms.COLUMN_BROWSABLE))
                    .apply { channels.single { it.id == channel }.programs.forEach { addRow(arrayOf<Any>(it, 1)) } }
            }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }
}
