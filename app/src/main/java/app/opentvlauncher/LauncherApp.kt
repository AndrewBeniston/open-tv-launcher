package app.opentvlauncher

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.opentvlauncher.home.HomeRepository
import app.opentvlauncher.home.HomeStateStore
import app.opentvlauncher.home.TitleChanges
import app.opentvlauncher.home.WatchlistUndo
import app.opentvlauncher.netflix.NetflixSource
import app.opentvlauncher.settings.LauncherSettings
import app.opentvlauncher.trakt.TraktApi
import app.opentvlauncher.trakt.TraktRowsSource
import app.opentvlauncher.trakt.TraktSession
import app.opentvlauncher.tv.TvChannels
import app.opentvlauncher.ui.art.ArtLoader
import app.opentvlauncher.ui.art.CoilArtLoader
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** The objects the whole app shares: one HTTP client, the Trakt API, the sign-in, the home data, the settings and the artwork. */
open class LauncherApp : Application() {
    /** Work that must outlive a screen, such as a watchlist removal after its 10 s undo. */
    val scope: CoroutineScope = MainScope()

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Open so that tests can use an API with no keys, which never reaches Trakt. */
    open val trakt: TraktApi by lazy { TraktApi(http, BuildConfig.TRAKT_CLIENT_ID, BuildConfig.TRAKT_CLIENT_SECRET) }

    /** The Trakt tokens. One store for each Application, so each Robolectric test gets its own file. */
    val session: TraktSession by lazy { TraktSession(preferences("trakt"), trakt) }

    /** The daily Netflix check. Open so that tests can keep it off the real Wikidata. */
    open val netflix: NetflixSource by lazy { NetflixSource(trakt, session, http) }

    /** The home screen's data, starting from the saved file. */
    val home: HomeRepository by lazy {
        HomeRepository(
            store = HomeStateStore(File(filesDir, "home-state.json")),
            trakt = TraktRowsSource(trakt, session),
            session = session,
            tvRows = { withContext(Dispatchers.IO) { TvChannels.read(this@LauncherApp) } },
            netflix = netflix,
        )
    }

    val settings: LauncherSettings by lazy { LauncherSettings(preferences("settings")) }

    /**
     * A preferences file that cannot be read is replaced with an empty one, so the launcher still
     * starts: the TV signs out of Trakt, or the settings go back to their defaults.
     */
    private fun preferences(name: String): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    ) { preferencesDataStoreFile(name) }

    val watchlistUndo: WatchlistUndo by lazy { WatchlistUndo(scope, home, session) }

    /** The Continue Watching menu's changes, which outlive the activity. */
    val titleChanges: TitleChanges by lazy { TitleChanges(scope, home) }

    /** Artwork through Coil, on the shared HTTP client, with up to 150 MB of images on disk. */
    val art: ArtLoader by lazy {
        val images = ImageLoader.Builder(this)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .memoryCache { null }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("art")).maxSizeBytes(150L * 1024 * 1024).build() }
            .build()
        CoilArtLoader(this, images)
    }
}

val Context.app: LauncherApp get() = applicationContext as LauncherApp
