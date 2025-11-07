package teamcode.robot.core.subsystem

import teamcode.robot.core.state.RobotStateMachine
import teamcode.robot.core.state.RobotState
import teamcode.threading.RobotThread
import kotlin.concurrent.Volatile

/**
 * Base class for all robot subsystems.
 * 
 * Design principles:
 * - Subsystems run on their own threads
 * - Commands are optional - subsystems can run continuously
 * - Thread-safe by default
 * - Clean separation of concerns
 * 
 * Architecture:
 * - Hardware management happens in periodic()
 * - Commands can override subsystem behavior when scheduled
 * - State machine integration for high-level coordination
 */
abstract class Subsystem(
    name: String,
    updateIntervalMs: Long = 20
) : RobotThread(name, updateIntervalMs) {
    
    init {
        // Automatically register with ThreadedOpMode if available
        teamcode.threading.ThreadedOpMode.registerThread(this)
    }
    
    /**
     * Current command scheduled to this subsystem.
     */
    @Volatile
    private var currentCommand: teamcode.robot.command.Command? = null
    
    /**
     * Get the current command running on this subsystem.
     */
    fun getCurrentCommand(): teamcode.robot.command.Command? = currentCommand
    
    /**
     * Check if this subsystem has a command scheduled.
     */
    fun hasCommand(): Boolean = currentCommand != null
    
    /**
     * Set the command for this subsystem (internal use only).
     */
    internal fun setCommand(command: teamcode.robot.command.Command?) {
        synchronized(this) {
            val previousCommand = currentCommand
            // Only set new command if previous command is not non-interruptible
            if (previousCommand != null && previousCommand.nonInterruptible && command != null) {
                // Cannot replace non-interruptible command
                return
            }
            currentCommand = command
            command?.let { it.setSubsystem(this) }
            
            if (previousCommand != null && previousCommand != command) {
                previousCommand.interrupt()
            }
        }
    }
    
    /**
     * Clear the current command (internal use only).
     */
    internal fun clearCommand() {
        synchronized(this) {
            currentCommand = null
        }
    }
    
    /**
     * Subsystem periodic update - always runs.
     * Override to implement hardware updates.
     */
    protected open fun periodic() {}
    
    /**
     * Subsystem initialization - called when thread starts.
     */
    protected open fun init() {}
    
    /**
     * Subsystem cleanup - called when thread stops.
     */
    protected open fun end() {}
    
    /**
     * Update telemetry for this subsystem.
     */
    protected open fun updateTelemetry() {
        telemetry.addData("Command", currentCommand?.javaClass?.simpleName ?: "None")
        telemetry.addData("State", RobotStateMachine.getState().name)
    }
    
    /**
     * Main thread loop.
     */
    final override fun runLoop() {
        // Execute command if scheduled
        currentCommand?.let { cmd ->
            if (cmd.isScheduled()) {
                cmd.runExecute()
            }
        }
        
        // Always run periodic updates
        periodic()
        
        // Update telemetry
        updateTelemetry()
    }
    
    override fun onStart() {
        init()
    }
    
    override fun onStop() {
        currentCommand?.cancel()
        currentCommand = null
        // Note: Do not call stop() here - the thread is already stopping gracefully
        // The run() loop will exit when isRunning becomes false
    }
    
    /**
     * Convenience methods for state machine access.
     */
    protected fun getRobotState(): RobotState = RobotStateMachine.getState()
    protected fun isRobotState(state: RobotState): Boolean = RobotStateMachine.isState(state)
    protected fun wasRobotState(state: RobotState): Boolean = RobotStateMachine.wasState(state)
    
    /**
     * Set default command for this subsystem.
     * Convenience method inspired by SolversLib.
     */
    fun setDefaultCommand(command: teamcode.robot.command.Command?) {
        try {
            teamcode.robot.control.CommandScheduler.getInstance().setDefaultCommand(this, command)
        } catch (e: Exception) {
            // Scheduler not initialized yet - will be set when ready
        }
    }
}

