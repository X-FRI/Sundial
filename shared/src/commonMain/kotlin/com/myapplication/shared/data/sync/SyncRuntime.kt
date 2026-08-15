package com.myapplication.shared.data.sync

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.allocate
import arrow.fx.coroutines.resource
import com.myapplication.shared.domain.repository.SyncStore
import com.myapplication.shared.domain.sync.SyncClient
import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncCoordinator
import com.myapplication.shared.domain.sync.SyncMode
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

internal class SyncRuntime internal constructor(
    internal val client: SyncClient,
    internal val coordinator: SyncCoordinator?,
) {
    private val jobs = mutableListOf<Job>()

    internal val trackedJobCount: Int get() = jobs.size

    internal fun track(job: Job): Job {
        jobs += job
        job.invokeOnCompletion { jobs -= job }
        return job
    }

    internal suspend fun cancelJobs() {
        jobs.toList().forEach { it.cancelAndJoin() }
        jobs.clear()
    }
}

internal class SyncRuntimeLease internal constructor(
    internal val runtime: SyncRuntime,
    private val releaseResource: suspend (ExitCase) -> Unit,
) {
    private var released = false

    internal suspend fun release(exitCase: ExitCase = ExitCase.Completed) {
        if (released) return
        released = true
        releaseResource(exitCase)
    }
}

internal fun syncRuntimeResource(
    repository: SyncStore,
    client: SyncClient,
    config: SyncConfig,
): Resource<SyncRuntime> =
    resource(
        acquire = {
            val coordinator =
                when (config.mode) {
                    SyncMode.Local -> null
                    else -> SyncCoordinator(repository, client, config.deviceId)
                }
            SyncRuntime(client, coordinator)
        },
        release = { runtime, _ ->
            runtime.cancelJobs()
            runtime.client.close()
        },
    )

@OptIn(DelicateCoroutinesApi::class)
internal suspend fun allocateSyncRuntime(
    repository: SyncStore,
    client: SyncClient,
    config: SyncConfig,
): SyncRuntimeLease {
    val (runtime, release) = syncRuntimeResource(repository, client, config).allocate()
    return SyncRuntimeLease(runtime, release)
}
