package com.lifementor.app.llm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiOperationRegistryTest {
    @Test
    fun invalidationAdvancesStateResetVersion() {
        val before = AiOperationRegistry.resetVersion.value

        AiOperationRegistry.invalidateAndCancel()

        assertTrue(AiOperationRegistry.resetVersion.value > before)
    }

    @Test
    fun invalidateCancelsRunningJobAndRejectsItsWriteToken() = runTest {
        val started = CompletableDeferred<Long>()
        val operation = AiOperationRegistry.createJob(backgroundScope) { token ->
            started.complete(token)
            awaitCancellation()
        }

        operation.start()
        val token = started.await()
        assertTrue(AiOperationRegistry.isCurrent(token))

        AiOperationRegistry.invalidateAndCancel()

        operation.job.join()
        assertFalse(AiOperationRegistry.isCurrent(token))
    }
}
