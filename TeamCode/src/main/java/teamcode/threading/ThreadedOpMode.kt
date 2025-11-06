package teamcode.threading

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.qualcomm.robotcore.util.ElapsedTime
import org.firstinspires.ftc.robotcore.external.Telemetry
import com.qualcomm.robotcore.hardware.Gamepad
import teamcode.robot.control.CommandScheduler
import teamcode.robot.control.GamepadEx
import teamcode.robot.control.StateDefaultCommands
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.state.RobotState
import teamcode.robot.core.state.RobotStateMachine
import teamcode.telemetry.RobotTelemetry
import teamcode.threading.RobotThread
import kotlin.concurrent.Volatile
import kotlin.lazy

/**
 * Base class for OpModes that use threading.
 * Extends LinearOpMode and provides thread management functionality.
 * 
 * Automatically includes command system integration:
 * - CommandSchedulerThread is created and managed automatically
 * - Access via getCommandScheduler() or CommandSystem utility
 */
abstract class ThreadedOpMode : LinearOpMode() {

    protected lateinit var threadManager: ThreadManager

    protected lateinit var runtime: ElapsedTime

    // Unified telemetry system - use this everywhere!
    protected lateinit var telemetry: RobotTelemetry
    
    /**
     * Command scheduler for managing commands.
     * Automatically created and managed by ThreadedOpMode.
     */
    private lateinit var commandScheduler: CommandScheduler

    /**
     * Get the original FTC telemetry object
     */
    // The original FTC telemetry (renamed from 'telemetry')
    var ftcTelemetry: Telemetry? = null
        protected set
    
    /**
     * Gamepad 1 - extended gamepad with binding functionality.
     * Wraps the standard FTC gamepad1 and adds command binding capabilities.
     * Use this in initOpMode() and other methods to create gamepad bindings.
     */
    protected val gamepad1Ex: GamepadEx by lazy { GamepadEx(super.gamepad1) }
    
    /**
     * Gamepad 2 - extended gamepad with binding functionality.
     * Wraps the standard FTC gamepad2 and adds command binding capabilities.
     * Use this in initOpMode() and other methods to create gamepad bindings.
     */
    protected val gamepad2Ex: GamepadEx by lazy { GamepadEx(super.gamepad2) }
    
    companion object {
        /**
         * Current ThreadedOpMode instance for auto-registration.
         * Thread-safe access.
         */
        @Volatile
        private var currentInstance: ThreadedOpMode? = null
        
        /**
         * Register a subsystem/thread to be automatically added to the thread manager.
         * Called automatically by Subsystem constructor.
         */
        internal fun registerThread(thread: RobotThread) {
            currentInstance?.threadManager?.addThread(thread)
        }
        
        /**
         * Set the current instance (called by runOpMode).
         */
        internal fun setCurrentInstance(instance: ThreadedOpMode?) {
            currentInstance = instance
        }
    }

    override fun runOpMode() {
        runtime = ElapsedTime()
        threadManager = ThreadManager()
        
        // Set current instance for auto-registration (after threadManager is created)
        setCurrentInstance(this)


        // Save reference to original FTC telemetry
        ftcTelemetry = super.telemetry


        // Create unified telemetry system
        telemetry = RobotTelemetry(ftcTelemetry)
        telemetry.namespace = "Main"


        // Initialize robot hardware
        RobotHardware.init(this, runtime)


        // Initialize subsystems and command system
        // Subsystems will auto-register themselves during construction
        initOpMode()
        
        // Create and initialize command scheduler
        commandScheduler = CommandScheduler(20)
        threadManager.addThread(commandScheduler)
        commandScheduler.telemetry = telemetry
        CommandScheduler.register(commandScheduler)
        
        // Automatically initialize StateDefaultCommands
        StateDefaultCommands.initialize()

        // Set telemetry for all threads
        for (thread in threadManager.getThreads()) {
            thread?.telemetry = telemetry
        }

        telemetry.addData("Status", "Initialized")
        telemetry.update()


        // Wait for the game to start (driver presses PLAY)
        waitForStart()
        runtime.reset()


        // Start all threads
        threadManager.startAll()


        // Call onStart hook for user initialization after threads start
        onStart()

        try {
            // Run the main opmode loop with automatic telemetry staging
            while (opModeIsActive()) {
                // Use atomic staging pattern (same as threads) to prevent flickering
                telemetry.namespace = "Main"
                telemetry.beginStaging()

                try {
                    // Call user's loop method
                    mainLoop()


                    // Commit staging buffer atomically
                    telemetry.commitStaging()
                } catch (e: Exception) {
                    // Discard staging on error
                    telemetry.discardStaging()
                    throw e
                }


                // Update telemetry - displays all thread data + main loop data
                telemetry.update()
            }
        } finally {
            // Ensure threads are stopped even if exception occurs
            threadManager.stopAll()
            // Cancel all commands before cleanup
            commandScheduler.cancelAll()
            cleanup()
            // Clear current instance
            setCurrentInstance(null)
        }
    }

    /**
     * Override this method to initialize your OpMode.
     * Called once when INIT is pressed, before threads are started.
     * 
     * Use this method to:
     * - Create and add subsystems to threadManager
     * - Set up commands, bindings, and state handlers
     * - Do any initialization that needs to happen before the game starts
     * 
     * Example:
     * ```
     * override fun initOpMode() {
     *     // Create subsystems (auto-register with threadManager)
     *     movement = MovementSubsystem()
     *     shooter = Shooter()
     *     
     *     // StateDefaultCommands.initialize() is called automatically!
     *     shooter.setDefaultCommand(ShooterDefaultCommand(shooter))
     *     
     *     // Bind gamepad triggers to commands and state changes
     *     gamepad1.bind {
     *         rightTrigger.whileHeld {
     *             ShootWithTriggerCommand(shooter) { gamepad1.right_trigger.toDouble() }
     *         }
     *         rightTrigger.whileHeld {
     *             SetStateCommand(RobotState.SHOOTING)
     *         }
     *         a.whenPressed {
     *             SetStateCommand(RobotState.INTAKING)
     *         }
     *     }
     *     
     *     // Add more bindings without overwriting (overwrite = false)
     *     // gamepad1.bind(overwrite = false) {
     *     //     x.whenPressed { ShootCommand(shooter) }
     *     // }
     *     
     *     // Remove all bindings for a gamepad
     *     // gamepad1.unbind()
     *     
     *     // Set up state handlers
     *     StateCommandBinding.onState(RobotState.SHOOTING) {
     *         InstantCommandNoRequirement { turret.enable() }
     *     }
     * }
     * ```
     */
    abstract open fun initOpMode()

    /**
     * Override this method for any setup after threads start but before the main loop begins.
     * Called once after waitForStart() and after threads are started.
     * 
     * Use this for:
     * - Enabling subsystems
     * - Any setup that needs to happen after threads are running
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
     * Get the command scheduler instance.
     * The scheduler is automatically created and managed by ThreadedOpMode.
     * 
     * @return The CommandScheduler instance
     */
    fun getCommandScheduler(): CommandScheduler {
        return commandScheduler ?: error("Command scheduler not initialized. This should not happen.")
    }

    val runtimeTime: ElapsedTime
        /**
         * Get the runtime timer
         */
        get() = runtime
    
    /**
     * Set the robot state.
     * Controller-driven state management - only the OpMode should change state.
     * State changes trigger handlers that can schedule commands automatically.
     * 
     * Usage:
     * ```
     * override fun mainLoop() {
     *     if (gamepad1.right_trigger > 0.1) {
     *         setState(RobotState.SHOOTING)  // Triggers state handlers
     *     } else {
     *         setState(RobotState.IDLE)
     *     }
     * }
     * ```
     * 
     * @param state The new robot state
     */
    protected fun setState(state: RobotState) {
        RobotStateMachine.transitionTo(state)
    }
    
    /**
     * Get the current robot state.
     * 
     * @return The current robot state
     */
    protected fun getState(): RobotState {
        return RobotStateMachine.getState()
    }
    
    /**
     * Check if the robot is in a specific state.
     * 
     * @param state The state to check
     * @return true if the robot is in the specified state
     */
    protected fun isState(state: RobotState): Boolean {
        return RobotStateMachine.isState(state)
    }
}

