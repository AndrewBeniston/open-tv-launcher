package app.opentvlauncher.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** What OK on a film or show does (design, "Settings, with the Selecting a Title row"). */
enum class TitleAction {
    /** The launcher's own page: artwork, logo, summary and Trakt rating. */
    ShowDetails,

    /** Stremio's title page, with the hand-off screen while Stremio opens. */
    OpenInStremio,
    ;

    /**
     * What OK does on this TV (25 Oct 2026): the setting, or Details when Stremio is not installed.
     * The setting itself stays, so it works again once Stremio is installed.
     */
    fun withStremio(installed: Boolean): TitleAction = if (installed) this else ShowDetails
}

/** The launcher's own settings, in DataStore. */
class LauncherSettings(private val store: DataStore<Preferences>) {
    // A file that cannot be read gives the defaults, so the launcher still starts.
    val titleAction: Flow<TitleAction> = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { prefs ->
        prefs[TITLE_ACTION]?.let { saved -> TitleAction.entries.firstOrNull { it.name == saved } } ?: TitleAction.ShowDetails
    }

    suspend fun setTitleAction(action: TitleAction) {
        store.edit { it[TITLE_ACTION] = action.name }
    }

    private companion object {
        val TITLE_ACTION = stringPreferencesKey("title_action")
    }
}
