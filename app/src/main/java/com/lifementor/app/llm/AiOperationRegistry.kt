package com.lifementor.app.llm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks AI jobs so a full data wipe can cancel in-flight requests and reject
 * writes that were started before the wipe.
 */
object AiOperationRegistry {
    private val lock = Any()
    private var generation = 0L
    private val jobs = mutableSetOf<Job>()
    private val _resetVersion = MutableStateFlow(0L)
    val resetVersion: StateFlow<Long> = _resetVersion.asStateFlow()

    class RegisteredJob internal constructor(
        val token: Long,
        val job: Job,
    ) {
        fun start(): Job = job.apply { start() }
    }

    fun createJob(
        scope: CoroutineScope,
        block: suspend CoroutineScope.(token: Long) -> Unit,
    ): RegisteredJob {
        val token = synchronized(lock) { generation }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block(token)
            } finally {
                synchronized(lock) { jobs.remove(coroutineContext.job) }
            }
        }
        synchronized(lock) {
            if (generation == token) jobs.add(job) else job.cancel()
        }
        return RegisteredJob(token, job)
    }

    fun launch(
        scope: CoroutineScope,
        block: suspend CoroutineScope.(token: Long) -> Unit,
    ): Job = createJob(scope, block).start()

    fun isCurrent(token: Long): Boolean = synchronized(lock) { generation == token }

    fun invalidateAndCancel() {
        val running = synchronized(lock) {
            generation++
            _resetVersion.value = generation
            jobs.toList().also { jobs.clear() }
        }
        running.forEach { it.cancel() }
    }
}
