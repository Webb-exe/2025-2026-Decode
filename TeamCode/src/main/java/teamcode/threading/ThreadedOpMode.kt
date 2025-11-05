package teamcode.threading

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.qualcomm.robotcore.util.ElapsedTime
import org.firstinspires.ftc.robotcore.external.Telemetry
import teamcode.robot.core.RobotHardware
import teamcode.telemetry.RobotTelemetry

/**
 * Base class for OpModes that use threading.
 * Extends LinearOpMode and provides thread management functionality.
 */
abstract class ThreadedOpMode : LinearOpMode() {
    protected var threadManager: ThreadManager? = null
    protected var runtime: ElapsedTime? = null

    // Unified telemetry system - use this everywhere!
    protected var telemetry: RobotTelemetry? = null

    /**
     * Get the original FTC telemetry object
     */
    // The original FTC telemetry (renamed from 'telemetry')
    var ftcTelemetry: Telemetry? = null
        protected set

    override fun runOpMode() {
        runtime = ElapsedTime()
        threadManager = ThreadManager()


        // Save reference to original FTC telemetry
        ftcTelemetry = super.telemetry


        // Create unified telemetry system
        telemetry = RobotTelemetry(ftcTelemetry)
        telemetry!!.namespace = "Main"


        // Initialize robot hardware
        RobotHardware.init(this, runtime!!)


        // Initialize threads
        initializeThreads()


        // Set telemetry for all threads
        for (thread in threadManager!!.getThreads()) {
            thread.telemetry = telemetry!!
        }

        telemetry!!.addData("Status", "Initialized")
        runInit()
        telemetry!!.update()


        // Wait for the game to start (driver presses PLAY)
        waitForStart()
        runtime!!.reset()


        // Start all threads
        threadManager!!.startAll()


        // Call onStart hook for user initialization after threads start
        onStart()

        try {
            // Run the main opmode loop with automatic telemetry staging
            while (opModeIsActive()) {
                // Use atomic staging pattern (same as threads) to prevent flickering
                telemetry!!.namespace = "Main"
                telemetry!!.beginStaging()

                try {
                    // Call user's loop method
                    mainLoop()


                    // Commit staging buffer atomically
                    telemetry!!.commitStaging()
                } catch (e: Exception) {
                    // Discard staging on error
                    telemetry!!.discardStaging()
                    throw e
                }


                // Update telemetry - displays all thread data + main loop data
                telemetry!!.update()
            }
        } finally {
            // Ensure threads are stopped even if exception occurs
            threadManager!!.stopAll()
            cleanup()
        }
    }

    /**
     * Override this method to implement your OpMode initialization logic.
     * Called once when INIT is pressed, before threads are started.
     */
    protected abstract fun runInit()

    /**
     * Override this method to initialize and add threads to the thread manager.
     * Called before waitForStart().
     */
    protected abstract fun initializeThreads()

    /**
     * Override this method for any setup after threads start but before the main loop begins.
     * Called once after waitForStart() and after threads are started.
     */
    protected open fun onStart() {}

    /**
     * Override this method to implement your main loop logic.
     * This is called repeatedly while the OpMode is active.
     * Telemetry staging and updates are handled automatically.
     */
    protected abstract fun mainLoop()

    /**
     * Override this method for cleanup operations.
     * Called after threads are stopped.
     */
    protected open fun cleanup() {}

    /**
     * Get the thread manager instance
     */
    fun getThreadManager(): ThreadManager {
        return threadManager!!
    }

    val runtimeTime: ElapsedTime
        /**
         * Get the runtime timer
         */
        get() = runtime!!

    /**
     * Get the unified telemetry system
     */
    fun getTelemetry(): RobotTelemetry {
        return telemetry!!
    }
}

