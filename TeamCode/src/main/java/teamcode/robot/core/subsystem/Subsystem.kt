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
 * - Commands can call subsystem methods directly (cross-thread)
 * - State machine integration for high-level coordination
 * - Subsystem methods must ensure thread-safety
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
        telemetry.addData("State", RobotStateMachine.getState().name)
    }
    
    /**
     * Main thread loop.
     * Runs periodic updates for this subsystem.
     */
    final override fun runLoop() {
        // Run periodic updates
        periodic()
        
        // Update telemetry
        updateTelemetry()
    }
    
    override fun onStart() {
        init()
    }
    
    override fun onStop() {
        // Note: Do not call stop() here - the thread is already stopping gracefully
        // The run() loop will exit when isRunning becomes false
    }
    
    /**
     * Convenience methods for state machine access.
     */
    protected fun getRobotState(): RobotState = RobotStateMachine.getState()
    protected fun isRobotState(state: RobotState): Boolean = RobotStateMachine.isState(state)
    protected fun wasRobotState(state: RobotState): Boolean = RobotStateMachine.wasState(state)

}

