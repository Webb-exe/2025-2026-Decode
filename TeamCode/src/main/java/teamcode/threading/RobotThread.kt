package teamcode.threading

import com.qualcomm.robotcore.util.ElapsedTime
import teamcode.telemetry.RobotTelemetry
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.Volatile

/**
 * Base class for robot subsystem threads.
 * Provides common functionality for thread lifecycle management and synchronization.
 */
abstract class RobotThread : Thread {
    /** Automatically register this instance as the singleton for its class  */
    private fun registerAsGlobal() {
        register(this)
    }

    /** Automatically unregister on stop  */
    private fun unregisterGlobal() {
        unregister(this)
    }

    /**
     * Check if thread is running
     */
    @Volatile
    var isRunning: Boolean = false
        protected set

    /**
     * Check if thread is paused
     */
    @Volatile
    var isPaused: Boolean = false
        protected set
    protected var runtime: ElapsedTime? = null
    protected val lock: Any = Any()
    lateinit var telemetry: RobotTelemetry


    @get:JvmName("getTelemetryOrThrow")
    val telemetrySafe get() = telemetry

    /**
     * Get the update interval in milliseconds
     */
    /**
     * Set the update interval in milliseconds
     */
    var updateIntervalMs: Long = 20 // Default 20ms update rate (50Hz)

    constructor(name: String) : super(name)

    constructor(name: String, updateIntervalMs: Long) : super(name) {
        this.updateIntervalMs = updateIntervalMs
    }

    /**
     * Initialize the thread - called before start()
     */
    fun initialize() {
        runtime = ElapsedTime()
    }

    /**
     * Main thread loop - subclasses should override runLoop() instead of run()
     */
    override fun run() {
        this.isRunning = true
        runtime!!.reset()

        registerAsGlobal()

        telemetry.namespace = name

        onStart()

        try {
            while (this.isRunning && !isInterrupted()) {
                synchronized(lock) {
                    while (this.isPaused && this.isRunning) {
                        try {
                            (lock as Object).wait()
                        } catch (e: InterruptedException) {
                            currentThread().interrupt()
                            return
                        }
                    }
                }

                if (!this.isRunning) break

                telemetry.beginStaging()
                try {
                    runLoop()
                    telemetry.commitStaging()
                } catch (t: Throwable) {
                    telemetry.discardStaging()
                    handleException(t)
                }

                try {
                    sleep(updateIntervalMs)
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                    return
                }
            }
        } finally {
            try {
                onStop()
            } finally {
                // ✅ UNREGISTER on shutdown
                unregisterGlobal()
            }
        }
    }

    /**
     * Override this method to implement the main logic of the thread
     */
    protected abstract fun runLoop()

    /**
     * Called when thread starts - override for initialization
     */
    protected open fun onStart() {}

    /**
     * Called when thread stops - override for cleanup
     */
    protected open fun onStop() {}

    /**
     * Handle exceptions during thread execution
     */
    protected open fun handleException(t: Throwable) {
        t.printStackTrace()
    }

    /**
     * Start the thread
     */
    @Synchronized
    override fun start() {
        if (!this.isRunning) {
            initialize()
            super.start()
        }
    }

    /**
     * Stop the thread gracefully
     */
    fun stopThread() {
        this.isRunning = false
        resumeThread() // Wake up if paused
        interrupt()
    }

    /**
     * Pause the thread
     */
    fun pauseThread() {
        synchronized(lock) {
            this.isPaused = true
        }
    }

    /**
     * Resume the thread
     */
    fun resumeThread() {
        synchronized(lock) {
            this.isPaused = false
            (lock as Object).notify()
        }
    }

    /**
     * Wait for thread to finish
     */
    @Throws(InterruptedException::class)
    fun joinThread() {
        join()
    }

    /**
     * Add telemetry data for this thread.
     * Uses the thread name as the namespace.
     * @param key The telemetry key
     * @param value The telemetry value
     */
    protected fun addTelemetry(key: String, value: Any) {
            // Ensure namespace is set for this thread
        telemetry.namespace = getName()
        telemetry.addData(key, value)
    }

    /**
     * Add formatted telemetry data for this thread.
     * @param key The telemetry key
     * @param format Format string
     * @param args Format arguments
     */
    protected fun addTelemetry(key: String, format: String, vararg args: Any) {
        // Ensure namespace is set for this thread
        telemetry.namespace = getName()
        telemetry.addData(key, format, *args)

    }

    /**
     * Clear all telemetry data for this thread.
     */
    protected fun clearTelemetry() {
        telemetry.clearNamespace(getName())

    }

    companion object {
        // Automatically tracks ONE instance per subclass
        private val globals = ConcurrentHashMap<Class<out RobotThread>, RobotThread>()

        // Kotlin callers: zero-arg, type-inferred
        @JvmStatic
        inline fun <reified T : RobotThread> current(): T =
            currentByClass(T::class.java)

        // Java callers: pass the Class<T>
        @JvmStatic
        fun <T : RobotThread> currentByClass(type: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return (globals[type] as? T)
                ?: error("${type.simpleName} is not running yet")
        }


        /**
        * Wait for a thread of the specified type to become available.
        * Useful when threads start concurrently and one depends on another.
        *
        * @param timeoutMs Maximum time to wait in milliseconds (default: 1000ms)
        * @param pollIntervalMs Time between checks in milliseconds (default: 10ms)
        * @return The thread instance when it becomes available
        * @throws IllegalStateException if the thread doesn't become available within the timeout
        *
        * Example usage:
        * ```
        * val vision = RobotThread.waitFor<VisionSubsystem>()
        * ```
        */
        @JvmStatic
        inline fun <reified T : RobotThread> waitFor(pollIntervalMs: Long = 10): T =
            waitForByClass(T::class.java, pollIntervalMs)

        /**
         * Wait for a thread of the specified type to become available.
         * Useful when threads start concurrently and one depends on another.
         * 
         * @param timeoutMs Maximum time to wait in milliseconds (default: 1000ms)
         * @param pollIntervalMs Time between checks in milliseconds (default: 10ms)
         * @return The thread instance when it becomes available
         * @throws IllegalStateException if the thread doesn't become available within the timeout
         * 
         * Example usage:
         * ```
         * val vision = RobotThread.waitFor<VisionSubsystem>()
         * ```
         */
        @JvmStatic
        inline fun <reified T : RobotThread> waitFor(timeoutMs: Long = 1000, pollIntervalMs: Long = 10): T =
            waitForByClass(T::class.java, timeoutMs, pollIntervalMs)

        /**
         * Wait for a thread of the specified type to become available (Java-compatible version).
         * 
         * @param type The class of the thread to wait for
         * @param timeoutMs Maximum time to wait in milliseconds (default: 1000ms)
         * @param pollIntervalMs Time between checks in milliseconds (default: 10ms)
         * @return The thread instance when it becomes available
         * @throws IllegalStateException if the thread doesn't become available within the timeout
         */
        @JvmStatic
        fun <T : RobotThread> waitForByClass(
            type: Class<T>,
            timeoutMs: Long = 1000,
            pollIntervalMs: Long = 10
        ): T {
            val maxRetries = (timeoutMs / pollIntervalMs).toInt().coerceAtLeast(1)
            var retries = maxRetries
            
            while (retries > 0) {
                @Suppress("UNCHECKED_CAST")
                val instance = globals[type] as? T
                if (instance != null) {
                    return instance
                }
                
                retries--
                if (retries > 0) {
                    try {
                        Thread.sleep(pollIntervalMs)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IllegalStateException("Interrupted while waiting for ${type.simpleName}", e)
                    }
                }
            }
            
            throw IllegalStateException(
                "${type.simpleName} did not become available within ${timeoutMs}ms"
            )
        }

        @JvmStatic
        fun <T : RobotThread> waitForByClass(
            type: Class<T>,
            pollIntervalMs: Long = 10
        ): T {
            while (true) {
                @Suppress("UNCHECKED_CAST")
                val instance = globals[type] as? T
                if (instance != null) {
                    return instance
                }

                    try {
                        Thread.sleep(pollIntervalMs)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IllegalStateException("Interrupted while waiting for ${type.simpleName}", e)
                    }

            }

        }

        @JvmStatic
        internal fun register(instance: RobotThread) {
            val prev = globals.putIfAbsent(instance.javaClass, instance)
            require(prev == null || prev === instance) {
                "Multiple ${instance.javaClass.simpleName} instances are not allowed"
            }
        }

        @JvmStatic
        internal fun unregister(instance: RobotThread) {
            globals.compute(instance.javaClass) { _, v -> if (v === instance) null else v }
        }

        // Legacy methods for backward compatibility
        @JvmStatic
        fun <T : RobotThread?> currentOf(type: Class<T?>): T? {
            val instance: RobotThread? = globals.get(type)
            checkNotNull(instance) { type.simpleName + " has not been constructed yet." }
            @Suppress("UNCHECKED_CAST")
            return instance as T
        }

        @JvmStatic
        fun <T : RobotThread?> current(type: Class<T?>): T? {
            val instance: RobotThread? = globals.get(type)
            checkNotNull(instance) { type.simpleName + " has not started yet." }
            @Suppress("UNCHECKED_CAST")
            return instance as T
        }
    }
}

