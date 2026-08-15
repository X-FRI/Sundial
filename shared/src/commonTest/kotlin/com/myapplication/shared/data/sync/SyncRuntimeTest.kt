package com.myapplication.shared.data.sync

import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.test.FakeSyncClient
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SyncRuntimeTest {
    private fun supabaseConfig(deviceId: String = "device-a") =
        SyncConfig(
            SyncMode.Supabase,
            supabaseUrl = "https://example.com",
            supabaseKey = "test-key",
            deviceId = deviceId,
        )

    @Test
    fun completedTrackedJobIsRemovedFromRuntime() =
        runTest {
            val lease =
                allocateSyncRuntime(
                    repository = FakeTodoRepository(),
                    client = FakeSyncClient(),
                    config = supabaseConfig(),
                )
            val job = backgroundScope.launch(start = CoroutineStart.LAZY) {}

            lease.runtime.track(job)

            assertEquals(1, lease.runtime.trackedJobCount)
            job.start()
            runCurrent()
            assertEquals(0, lease.runtime.trackedJobCount)
            lease.release()
        }

    @Test
    fun releaseCancelsTrackedJobsAndClosesClientOnce() =
        runTest {
            val client = FakeSyncClient()
            var cancelled = false
            val lease =
                allocateSyncRuntime(
                    repository = FakeTodoRepository(),
                    client = client,
                    config = supabaseConfig(),
                )
            val job =
                backgroundScope.launch {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled = true
                    }
                }

            lease.runtime.track(job)
            runCurrent()

            assertTrue(job.isActive)
            lease.release()
            runCurrent()

            assertFalse(job.isActive)
            assertTrue(cancelled)
            assertEquals(0, lease.runtime.trackedJobCount)
            assertEquals(1, client.closeAttempts)

            lease.release()

            assertEquals(1, client.closeAttempts)
        }
}
