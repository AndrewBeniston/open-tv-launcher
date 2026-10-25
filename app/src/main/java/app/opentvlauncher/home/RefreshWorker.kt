package app.opentvlauncher.home

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.opentvlauncher.app
import java.util.concurrent.TimeUnit

/**
 * The hourly background refresh (technical plan). It also renews the Trakt sign-in, because
 * a refresh asks the session for a token and the session renews it when it is due.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.app.home.refresh()
        return Result.success()
    }

    companion object {
        private const val NAME = "refresh"

        // Builds before the package rename (25 Oct 2026) saved their work under this name, with a
        // worker class name that no longer exists. WorkManager cannot start that work or change
        // its class, so it is cancelled.
        private const val OLD_NAME = "home-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            val work = WorkManager.getInstance(context)
            work.cancelUniqueWork(OLD_NAME)
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
