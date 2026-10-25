package app.opentvlauncher.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import app.opentvlauncher.home.Title
import app.opentvlauncher.system.TitleApp
import app.opentvlauncher.system.TitleAppIcons
import app.opentvlauncher.ui.focus.PillButton

/**
 * A title's app buttons, [apps] in order (TitleApp.forTitle): each shows the app's mark and name
 * (25 Oct 2026). [modifier] goes on the first button, and [each] on each app's own, so
 * focus can come back to the button that was pressed. Banner and details page.
 *
 * Each button is keyed by its app. Unkeyed, a list that changed under a held OK, such as the
 * first Stremio button replaced by Netflix when the installed apps are read, kept the press and
 * gave it the new app's action: the release opened Netflix (merge gate V1).
 */
@Composable
fun TitleAppButtons(
    title: Title,
    apps: List<TitleApp>,
    icons: TitleAppIcons,
    onOpen: (TitleApp) -> Unit,
    modifier: Modifier = Modifier,
    each: (TitleApp) -> Modifier = { Modifier },
) {
    apps.forEachIndexed { index, app ->
        key(app) {
            PillButton(
                label = TitleText.appLabel(title, app),
                onClick = { onOpen(app) },
                modifier = (if (index == 0) modifier else Modifier).then(each(app)),
                icon = icons.icons[app],
            )
        }
    }
}
