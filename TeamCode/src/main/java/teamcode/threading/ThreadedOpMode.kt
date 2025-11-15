package teamcode.threading

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.qualcomm.robotcore.util.ElapsedTime
import org.firstinspires.ftc.robotcore.external.Telemetry
import com.qualcomm.robotcore.hardware.Gamepad
import teamcode.robot.command.CommandScheduler
import teamcode.robot.control.GamepadEx
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
    protected lateinit var robotTelemetry: RobotTelemetry
    
    /**
     * Command scheduler - manages command lifecycle automatically.
     * Available after initialization for scheduling commands.
     */
    protected lateinit var commandScheduler: CommandScheduler

    /**
     * Get the original FTC telemetry object
     */
    // The original FTC telemetry (renamed from 'telemetry')
    var ftcTelemetry: Telemetry? = null
        protected set
    
    /**
     * Override parent's telemetry property to return ftcTelemetry.
     * This maintains compatibility with code expecting Telemetry type.
     * Use robotTelemetry for RobotTelemetry-specific functionality.
     * 
     * Note: This override prevents smart cast issues by ensuring telemetry
     * has a single, well-defined type (Telemetry) rather than being shadowed.
     */
     var telemetry: Telemetry
        get() = ftcTelemetry ?: error("Telemetry not initialized")
        set(value) {
            ftcTelemetry = value
        }
    
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
         * Global gamepad1Ex instance - accessible from anywhere.
         * Thread-safe access.
         */
        @Volatile
        private var globalGamepad1Ex: GamepadEx? = null
        
        /**
         * Global gamepad2Ex instance - accessible from anywhere.
         * Thread-safe access.
         */
        @Volatile
        private var globalGamepad2Ex: GamepadEx? = null
        
        /**
         * Get the global gamepad1Ex instance.
         * @return The GamepadEx instance for gamepad1
         * @throws IllegalStateException if the OpMode is not running
         */
        @JvmStatic
        @JvmName("getGlobalGamepad1Ex")
        fun getGamepad1Ex(): GamepadEx {
            return globalGamepad1Ex ?: error("Gamepad1Ex is not available. OpMode may not be running.")
        }
        
        /**
         * Get the global gamepad2Ex instance.
         * @return The GamepadEx instance for gamepad2
         * @throws IllegalStateException if the OpMode is not running
         */
        @JvmStatic
        @JvmName("getGlobalGamepad2Ex")
        fun getGamepad2Ex(): GamepadEx {
            return globalGamepad2Ex ?: error("Gamepad2Ex is not available. OpMode may not be running.")
        }
        
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
            // Update global gamepads when instance changes
            if (instance != null) {
                globalGamepad1Ex = instance.gamepad1Ex
                globalGamepad2Ex = instance.gamepad2Ex
            } else {
                globalGamepad1Ex = null
                globalGamepad2Ex = null
            }
        }
    }

    override fun runOpMode() {
        runtime = ElapsedTime()
        threadManager = ThreadManager()
        
        // Initialize gamepads (lazy properties) before setting as global
        // Access them to trigger lazy initialization
        val unused1 = gamepad1Ex
        val unused2 = gamepad2Ex
        
        // Set current instance for auto-registration (after threadManager is created)
        // This will also set the global gamepads
        setCurrentInstance(this)


        // Save reference to original FTC telemetry
        ftcTelemetry = super.telemetry


        // Create unified telemetry system
        robotTelemetry = RobotTelemetry(ftcTelemetry)
        robotTelemetry.namespace = "Main"


        // Initialize robot hardware
        RobotHardware.init(this, runtime)

        
        // Create and initialize command scheduler
        commandScheduler = CommandScheduler()
        CommandScheduler.setInstance(commandScheduler)
        threadManager.addThread(commandScheduler)


        // Initialize subsystems and command system
        // Subsystems will auto-register themselves during construction
        initOpMode()

        // Set telemetry for all threads
        for (thread in threadManager.getThreads()) {
            thread?.telemetry = robotTelemetry
        }

        robotTelemetry.addData("Status", "Initialized")
        robotTelemetry.update()


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
                robotTelemetry.namespace = "Main"
                robotTelemetry.beginStaging()

                try {
                    // Call user's loop method
                    mainLoop()


                    // Commit staging buffer atomically
                    robotTelemetry.commitStaging()
                } catch (e: Exception) {
                    // Discard staging on error
                    robotTelemetry.discardStaging()
                    throw e
                }


                // Update telemetry - displays all thread data + main loop data
                robotTelemetry.update()
            }
        } finally {
            // Ensure threads are stopped even if exception occurs
            threadManager.stopAll()
            cleanup()
            // Clear command scheduler instance
            CommandScheduler.setInstance(null)
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

/**
 * Global access to gamepad1Ex.
 * Convenience property for accessing the extended gamepad from anywhere.
 * 
 * Usage:
 * ```
 * if (gamepad1Ex.a.value) { ... }
 * gamepad1Ex.rightTrigger.whileHeld().bind { ShootCommand() }
 * ```
 */
val gamepad1Ex: GamepadEx
    get() = ThreadedOpMode.getGamepad1Ex()

/**
 * Global access to gamepad2Ex.
 * Convenience property for accessing the extended gamepad from anywhere.
 * 
 * Usage:
 * ```
 * if (gamepad2Ex.a.value) { ... }
 * gamepad2Ex.rightTrigger.whileHeld().bind { ShootCommand() }
 * ```
 */
val gamepad2Ex: GamepadEx
    get() = ThreadedOpMode.getGamepad2Ex()

