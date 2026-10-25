package app.opentvlauncher.screensaver

import android.service.dreams.DreamService
import android.view.KeyEvent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.opentvlauncher.app
import app.opentvlauncher.ui.theme.LauncherTheme

/**
 * The launcher's own screensaver: slowly moving, dimmed artwork of Top Picks for You, from the
 * saved Home rows (screensaverTitles).
 *
 * Google TV 14 accepts a screensaver that is not Google's (gate 1). Set it with the app id
 * (scripts/app-id.sh) and the full class name: adb shell settings put secure
 * screensaver_components <app id>/app.opentvlauncher.screensaver.ScreensaverService
 *
 * A DreamService is not a LifecycleOwner, so it gives Compose the owners it needs itself.
 */
class ScreensaverService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)
        setContentView(
            ComposeView(this).apply {
                setContent {
                    LauncherTheme(art = app.art) {
                        val state by app.home.state.collectAsState()
                        val titles = remember(state) { screensaverTitles(state, System.currentTimeMillis() / DAY_MILLIS) }
                        ScreensaverScreen(titles)
                    }
                }
            },
        )
    }

    /**
     * Any key closes the screensaver at once (24 Oct 2026). DreamService's own handler
     * asks the system to wake the TV gently, and Back was slow to close it that way. The key is
     * not passed on, so focus stays where it was.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) finish()
        return true
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
