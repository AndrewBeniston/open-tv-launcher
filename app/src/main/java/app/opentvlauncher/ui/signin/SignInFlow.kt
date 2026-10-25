package app.opentvlauncher.ui.signin

import app.opentvlauncher.trakt.DevicePoll
import app.opentvlauncher.trakt.TraktApi
import app.opentvlauncher.trakt.TraktSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/** What the sign-in screen shows. */
sealed interface SignInState {
    data object Loading : SignInState

    /** [userCode] and [verificationUrl] come from Trakt's device response. */
    data class Code(val userCode: String, val verificationUrl: String, val expiresAt: Long) : SignInState

    data object Expired : SignInState
    data object Denied : SignInState
    data object Offline : SignInState

    /** Trakt answered with something the launcher cannot read. */
    data object Failed : SignInState
    data object NotConfigured : SignInState
    data object Approved : SignInState
}

/**
 * Trakt's device-code sign-in: get a code, then poll at Trakt's interval until the user
 * approves it on a phone, the code expires, or the user refuses it.
 *
 * An expired code is replaced by a new one, [renewals] times (about an hour with Trakt's
 * 10-minute codes), so a slow first sign-in does not need "Get a New Code". The screen collects
 * this only while it is on screen, so a TV left on this screen stops polling when it sleeps.
 */
class SignInFlow(
    private val api: TraktApi,
    private val session: TraktSession,
    private val renewals: Int = 5,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun states(): Flow<SignInState> = flow {
        if (!api.configured) {
            emit(SignInState.NotConfigured)
            return@flow
        }
        emit(SignInState.Loading)
        repeat(renewals + 1) {
            val code = try {
                api.deviceCode()
            } catch (_: TraktApi.BadResponse) {
                emit(SignInState.Failed)
                return@flow
            } catch (_: IOException) {
                emit(SignInState.Offline)
                return@flow
            }
            val expiresAt = now() + code.expiresIn * 1000L
            emit(SignInState.Code(code.userCode, code.verificationUrl, expiresAt))
            var interval = code.interval.coerceAtLeast(1) * 1000L
            while (now() < expiresAt) {
                delay(interval)
                // A network error during polling is treated as "not yet": the next poll tries again.
                // An answer that cannot be read ends the attempt: Trakt may have used the code.
                val poll = try {
                    api.pollDeviceToken(code.deviceCode)
                } catch (_: TraktApi.BadResponse) {
                    emit(SignInState.Failed)
                    return@flow
                } catch (_: IOException) {
                    DevicePoll.Pending
                }
                when (poll) {
                    is DevicePoll.Approved -> {
                        session.save(poll.tokens)
                        emit(SignInState.Approved)
                        return@flow
                    }
                    DevicePoll.Pending -> Unit
                    DevicePoll.SlowDown -> interval += 1000L
                    DevicePoll.Expired -> break
                    DevicePoll.Denied -> {
                        emit(SignInState.Denied)
                        return@flow
                    }
                }
            }
        }
        emit(SignInState.Expired)
    }
}

/** "8F3A21C7" as "8F3A 21C7", as the design shows the code. */
fun formatUserCode(code: String): String =
    if (code.length == 8 && ' ' !in code) code.substring(0, 4) + " " + code.substring(4) else code
