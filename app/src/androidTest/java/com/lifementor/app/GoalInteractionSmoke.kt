package com.lifementor.app

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Goal
import com.lifementor.app.data.TaskItem
import java.io.File

/** Uses isolated files and negative fixture IDs; never resets user reminders or user data. */
internal object GoalInteractionSmoke {
    fun run(instrumentation: Instrumentation) {
        val targetContext = instrumentation.targetContext
        val directory = File(targetContext.cacheDir, "goal-smoke-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
        var failure: Throwable? = null
        var checks = 0
        fun verify(condition: Boolean, description: String) {
            check(condition) { description }
            checks++
        }
        instrumentation.runOnMainSync {
            try {
                AppStorage.init(context)
                val goal = Goal(-100, "Isolated goal", "pathfinder", decomposed = true)
                val task = TaskItem(-101, goal.id, null, 3, "Previously completed action",
                    done = true, doneAt = 1000, xpGiven = true, dueDate = 1794700800000L)
                GameStore.saveGoals(listOf(goal))
                GameStore.saveTasks(listOf(task))
                GameStore.addXP(10, task.title, goal.domain)
                val xpBefore = GameStore.loadXP()
                GameStore.setGoalDone(goal.id)
                verify(GameStore.loadGoals().single().status == "done", "Goal completion persists")
                GameStore.reopenGoal(goal.id)
                verify(GameStore.loadGoals().single() == goal, "Undo restores active goal and keeps decomposition state")
                verify(GameStore.loadTasks().single() == task, "Undo preserves completion, hierarchy and task date")
                verify(GameStore.loadXP() == xpBefore, "Undo does not alter rewards")
                GameStore.reopenGoal(goal.id)
                verify(GameStore.loadXP() == xpBefore, "Repeated undo does not duplicate rewards")
                verify(GameStore.loadTasks().single() == task, "Repeated undo preserves task progress")
            } catch (error: Throwable) {
                failure = error
            } finally {
                AppStorage.init(targetContext)
                directory.deleteRecursively()
            }
        }
        instrumentation.finish(if (failure == null) -1 else 0, Bundle().apply {
            putString("stream", failure?.let { "FAIL after $checks checks: ${it.stackTraceToString()}" }
                ?: "PASS: $checks isolated goal undo checks")
        })
    }
}
