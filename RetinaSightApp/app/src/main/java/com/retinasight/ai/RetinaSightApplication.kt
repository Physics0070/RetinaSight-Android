package com.retinasight.ai

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RetinaSightApplication : Application() {

    lateinit var container: AppContainer
        private set

    /**
     * Background work must never be able to kill the app.
     *
     * A SupervisorJob alone does not do this: an uncaught exception in a
     * launched coroutine still reaches the thread's default handler and
     * crashes the process - which is exactly how a sync bug took down
     * screening. The handler is what actually contains it.
     */
    private val appScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            CoroutineExceptionHandler { _, error ->
                Log.e(TAG, "background task failed; screening is unaffected", error)
            }
    )

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)

        // Load the model at launch so the first scan is not slowed by a cold
        // start. The phone then keeps it warm for the rest of the session.
        appScope.launch {
            runCatching { container.inferenceEngine.warmUp() }
        }
    }

    private companion object {
        const val TAG = "RetinaSightApp"
    }
}
