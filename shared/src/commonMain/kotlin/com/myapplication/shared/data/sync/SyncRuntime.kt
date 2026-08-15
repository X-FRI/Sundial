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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlin.time.Clock

internal class SyncRuntime internal constructor(
    internal val scope: CoroutineScope,
    internal val repository: SyncStore,
    internal val client: SyncClient,
    internal val config: SyncConfig,
    internal val clock: Clock,
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
    scope: CoroutineScope,
    repository: SyncStore,
    client: SyncClient,
    config: SyncConfig,
    clock: Clock,
): Resource<SyncRuntime> =
    resource(
        acquire = {
            val coordinator =
                when (config.mode) {
                    SyncMode.Local -> null
                    else -> SyncCoordinator(repository, client, config.deviceId)
                }
            SyncRuntime(scope, repository, client, config, clock, coordinator)
        },
        release = { runtime, _ ->
            runtime.cancelJobs()
            runtime.client.close()
        },
    )

@OptIn(DelicateCoroutinesApi::class)
internal suspend fun allocateSyncRuntime(
    scope: CoroutineScope,
    repository: SyncStore,
    client: SyncClient,
    config: SyncConfig,
    clock: Clock,
): SyncRuntimeLease {
    val (runtime, release) = syncRuntimeResource(scope, repository, client, config, clock).allocate()
    return SyncRuntimeLease(runtime, release)
}
