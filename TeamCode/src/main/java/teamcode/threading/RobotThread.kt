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
        val previous: RobotThread? = GLOBALS.putIfAbsent(javaClass, this)
        check(!(previous != null && previous !== this)) { "Multiple instances of " + javaClass.getSimpleName() + " are not allowed." }
    }

    /** Automatically unregister on stop  */
    private fun unregisterGlobal() {
        GLOBALS.compute(javaClass) { k: Class<out RobotThread?>?, v: RobotThread? -> if (v === this) null else v }
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
    @JvmField
    protected var telemetry: RobotTelemetry? = null

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

        if (telemetry != null) telemetry!!.setNamespace(getName())

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

                if (telemetry != null) telemetry!!.beginStaging()
                try {
                    runLoop()
                    if (telemetry != null) telemetry!!.commitStaging()
                } catch (e: Exception) {
                    if (telemetry != null) telemetry!!.discardStaging()
                    handleException(e)
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
    protected fun handleException(e: Exception) {
        e.printStackTrace()
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
     * Set the telemetry system for this thread.
     * Should be called before starting the thread.
     * @param telemetry The shared telemetry system
     */
    fun setTelemetry(telemetry: RobotTelemetry?) {
        this.telemetry = telemetry
        // Set namespace for this thread
        if (telemetry != null) {
            telemetry.setNamespace(getName())
        }
    }

    /**
     * Add telemetry data for this thread.
     * Uses the thread name as the namespace.
     * @param key The telemetry key
     * @param value The telemetry value
     */
    protected fun addTelemetry(key: String?, value: Any?) {
        if (telemetry != null) {
            // Ensure namespace is set for this thread
            telemetry!!.setNamespace(getName())
            telemetry!!.addData(key, value)
        }
    }

    /**
     * Add formatted telemetry data for this thread.
     * @param key The telemetry key
     * @param format Format string
     * @param args Format arguments
     */
    protected fun addTelemetry(key: String?, format: String?, vararg args: Any?) {
        if (telemetry != null) {
            // Ensure namespace is set for this thread
            telemetry!!.setNamespace(getName())
            telemetry!!.addData(key, format, *args)
        }
    }

    /**
     * Clear all telemetry data for this thread.
     */
    protected fun clearTelemetry() {
        if (telemetry != null) {
            telemetry!!.clearNamespace(getName())
        }
    }

    companion object {
        // Automatically tracks ONE instance per subclass
        private val GLOBALS = ConcurrentHashMap<Class<out RobotThread?>?, RobotThread?>()

        fun <T : RobotThread?> currentOf(type: Class<T?>): T? {
            val instance: RobotThread? = GLOBALS.get(type)
            checkNotNull(instance) { type.getSimpleName() + " has not been constructed yet." }
            return instance as T
        }

        fun <T : RobotThread?> current(type: Class<T?>): T? {
            val instance: RobotThread? = GLOBALS.get(type)
            checkNotNull(instance) { type.getSimpleName() + " has not started yet." }
            return instance as T
        }
    }
}

