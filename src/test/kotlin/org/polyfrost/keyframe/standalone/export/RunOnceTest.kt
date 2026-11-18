package org.polyfrost.keyframe.standalone.export

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RunOnceTest {
	private val second = 1_000_000_000L

	@Test
	fun resubmitsTasksTheExecutorDropped() {
		val submissions = AtomicInteger()
		val runs = AtomicInteger()
		val worker = Executors.newSingleThreadExecutor()
		runOnce({ if (submissions.incrementAndGet() > 3) worker.execute(it) }, second) { runs.incrementAndGet() }
		worker.shutdown()
		assertEquals(1, runs.get())
	}

	@Test
	fun slowTaskRunsOnlyOnce() {
		val runs = AtomicInteger()
		val pool = Executors.newCachedThreadPool()
		runOnce(pool, second) {
			runs.incrementAndGet()
			Thread.sleep(300)
		}
		pool.shutdown()
		assertEquals(1, runs.get())
	}

	@Test
	fun givesUpWhenNothingRuns() {
		assertFailsWith<IllegalStateException> { runOnce({}, second / 5) {} }
	}

	@Test
	fun rethrowsTaskFailure() {
		val pool = Executors.newSingleThreadExecutor()
		assertFailsWith<UnsupportedOperationException> { runOnce(pool, second) { throw UnsupportedOperationException() } }
		pool.shutdown()
	}
}
