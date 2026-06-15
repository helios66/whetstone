package com.unpopulardev.whetstone.worker

import androidx.work.ListenableWorker
import com.unpopulardev.whetstone.InternalWhetstoneApi
import com.unpopulardev.whetstone.meta.AutoInstanceBinding

/**
 * Marker annotation signalling that the compiler should generate necessary instance
 * bindings for the annotated worker.
 *
 * For example:
 * Given this annotated worker
 * ```
 * @ContributesWorker
 * class MyWorker @Inject constructor(parameters: WorkerParameters) : Worker()
 * ```
 * a complementary Metro contribution will be generated
 * ```
 * @ContributesTo(WorkerScope::class)
 * interface MyWorker_WhetstoneModule {
 *     @Binds
 *     @IntoMap
 *     @ClassKey(MyWorker::class)
 *     val MyWorker.bindMyWorker: ListenableWorker
 * }
 * ```
 */
@OptIn(InternalWhetstoneApi::class)
@AutoInstanceBinding(base = ListenableWorker::class, scope = WorkerScope::class)
public annotation class ContributesWorker
