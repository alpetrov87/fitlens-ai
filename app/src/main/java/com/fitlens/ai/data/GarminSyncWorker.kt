package com.fitlens.ai.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class GarminSyncWorker(appContext: Context, parameters: WorkerParameters) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val keyStore = GarminPersonalKeyStore(applicationContext)
        if (!keyStore.hasKey()) return Result.success()
        val repository = DefaultHealthRepository(
            EmptyActivitySource(), EmptyNutritionSource(), SharedPreferencesImportedMetricsStore(applicationContext),
            GarminPersonalApi(keyStore),
        )
        return try {
            repository.syncGarmin()
            Result.success()
        } catch (failure: Exception) {
            val permanent = failure is IllegalArgumentException ||
                failure.message.orEmpty().contains("отказа достъп", ignoreCase = true) ||
                failure.message.orEmpty().contains("повреден", ignoreCase = true)
            if (permanent) Result.failure() else Result.retry()
        }
    }
}

object GarminSyncScheduler {
    private const val UNIQUE_WORK = "fitlens_garmin_periodic_sync"

    fun reconcile(context: Context) = setEnabled(context, GarminPersonalKeyStore(context).hasKey())

    fun setEnabled(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            manager.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<GarminSyncWorker>(4, TimeUnit.HOURS)
            .setInitialDelay(4, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        manager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
