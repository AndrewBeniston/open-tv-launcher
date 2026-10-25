package app.opentvlauncher.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Continue Watching menu changes on their way to Trakt (HomeRepository.change). They run in the
 * app's [scope] and outlive the activity, so what is sending is kept here too, not in the
 * activity. A menu opened again, even in an activity Android has recreated, shows the change as
 * sending and cannot send it twice. One change at a time for each sign-in and title. [scope] must
 * run on one thread, as MainScope does. After an episode is marked watched, a refresh brings in
 * the show's next episode, up next (25 Oct 2026).
 */
class TitleChanges(private val scope: CoroutineScope, private val home: HomeRepository) {
    /** A title in one sign-in: its Trakt key, or its name for a title without one. */
    data class Key(val generation: Long, val title: Any)

    data class Result(val key: Key, val change: TitleChange, val result: ChangeResult)

    private val sendingNow = MutableStateFlow<Map<Key, TitleChange>>(emptyMap())
    val sending: StateFlow<Map<Key, TitleChange>> = sendingNow.asStateFlow()

    private val answers = MutableSharedFlow<Result>(extraBufferCapacity = 8)

    /** Each change's answer, once, for a menu that shows its title. */
    val results: SharedFlow<Result> = answers.asSharedFlow()

    fun key(title: Title, generation: Long) = Key(generation, title.key ?: title.title)

    /** Sends [change] for [title] in sign-in [generation]. False, and nothing sent, while a change for that title is sending. */
    fun send(title: Title, change: TitleChange, generation: Long): Boolean {
        val key = key(title, generation)
        if (key in sendingNow.value) return false
        sendingNow.value += key to change
        scope.launch {
            val result = try {
                home.change(title, change, generation)
            } finally {
                sendingNow.value -= key
            }
            answers.emit(Result(key, change, result))
            if (change == TitleChange.MarkWatched && result == ChangeResult.Done && title.episode != null) home.refresh()
        }
        return true
    }
}
