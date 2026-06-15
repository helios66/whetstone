package com.unpopulardev.whetstone.sample

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unpopulardev.whetstone.ForScope
import com.unpopulardev.whetstone.sample.library.MainDependency
import com.unpopulardev.whetstone.worker.ContributesWorker
import com.unpopulardev.whetstone.worker.WorkerScope
import javax.inject.Inject

@ContributesWorker
class MainWorker @Inject constructor(
    @ForScope(WorkerScope::class) context: Context,
    workerParameters: WorkerParameters,
    private val dependency: MainDependency
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        Log.i("Worker", "${dependency.getMessage("MainWorker")} on ${Thread.currentThread().name}")
        // Extra traced work on the worker's coroutine-dispatcher thread.
        var total = 0
        repeat(10) { i -> total += dependency.getMessage("work-extra-$i").length }
        Log.i("Worker", "extra work total=$total")
        return Result.success()
    }
}
