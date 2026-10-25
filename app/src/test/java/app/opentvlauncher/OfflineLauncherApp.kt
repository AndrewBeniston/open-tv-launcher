package app.opentvlauncher

import app.opentvlauncher.system.installHomeApps
import app.opentvlauncher.system.installTitleApps
import app.opentvlauncher.trakt.TraktApi
import okhttp3.OkHttpClient

/** The app with no Trakt keys: the sign-in screen says so, and no test reaches the real Trakt. */
class OfflineLauncherApp : LauncherApp() {
    override val trakt: TraktApi by lazy { TraktApi(OkHttpClient(), "", "") }

    override fun onCreate() {
        super.onCreate()
        // The apps row's apps are installed, as on the TVs.
        installTitleApps()
        installHomeApps()
    }
}
