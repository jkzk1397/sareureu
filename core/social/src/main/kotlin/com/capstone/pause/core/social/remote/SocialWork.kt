package com.capstone.pause.core.social.remote

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.capstone.pause.core.social.PokeInbox
import com.capstone.pause.core.social.SharingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 하루 합계 올리기. 무료 한도 안에서 **하루 한 번**(예약) + 동의한 직후 한 번만 돈다.
 * 실패하면 한 번만 다시 해 보고 다음 날을 기다린다.
 */
@HiltWorker
class SocialUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sharingRepository: SharingRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when {
        sharingRepository.upload() -> Result.success()
        runAttemptCount < MAX_RETRY -> Result.retry()
        else -> Result.failure()
    }

    private companion object {
        const val MAX_RETRY = 1
    }
}

/** 친구가 맡긴 잔소리 받기(5-4). 12시간마다, 네트워크가 있을 때만. 앱이 화면에 있을 때도 따로 받는다. */
@HiltWorker
class SocialPokeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pokeInbox: PokeInbox,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { pokeInbox.fetch() }
        return Result.success()
    }
}

/** 철회 때 못 지운 서버 기록을 네트워크가 될 때 지운다(최대 몇 번만). */
@HiltWorker
internal class SocialDeleteWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sharing: RemoteSharingRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when {
        sharing.deleteRemote() -> Result.success()
        runAttemptCount < MAX_DELETE_RETRY -> Result.retry()
        else -> Result.failure()
    }

    private companion object {
        const val MAX_DELETE_RETRY = 5
    }
}

/** WorkManager 예약. 네트워크가 있을 때만. */
@Singleton
internal class WorkUploadScheduler @Inject constructor(@ApplicationContext private val context: Context) :
    UploadScheduler {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun schedule() {
        val work = WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork(
            DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SocialUploadWorker>(1, TimeUnit.DAYS).setConstraints(constraints).build(),
        )
        work.enqueueUniquePeriodicWork(
            POKES,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SocialPokeWorker>(
                POKE_HOURS,
                TimeUnit.HOURS,
            ).setConstraints(constraints).build(),
        )
        work.enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SocialUploadWorker>().setConstraints(constraints).build(),
        )
    }

    override fun cancel() {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(DAILY)
        work.cancelUniqueWork(NOW)
        work.cancelUniqueWork(POKES)
        work.cancelUniqueWork(DELETE)
    }

    override fun scheduleDelete() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            DELETE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SocialDeleteWorker>().setConstraints(constraints).build(),
        )
    }

    private companion object {
        const val DELETE = "social_delete"
        const val POKES = "social_pokes"
        const val POKE_HOURS = 12L
        const val DAILY = "social_daily_upload"
        const val NOW = "social_upload_now"
    }
}
