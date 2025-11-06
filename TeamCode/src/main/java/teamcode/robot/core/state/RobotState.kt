package teamcode.robot.core.state

import kotlin.concurrent.Volatile

// Forward declaration to avoid circular dependency

/**
 * High-level robot operational states.
 * Represents what the robot is currently doing at a macro level.
 */
enum class RobotState {
    IDLE,
    INTAKING,
    SHOOTING,
    SORTING,
}

/**
 * Thread-safe state machine for managing robot operational state.
 * 
 * Design principles:
 * - State transitions are explicit and thread-safe
 * - State represents high-level robot behavior, not hardware state
 * - Commands can query and transition state as needed
 */
object RobotStateMachine {
    @Volatile
    private var currentState: RobotState = RobotState.IDLE
    
    @Volatile
    private var previousState: RobotState = RobotState.IDLE
    
    /**
     * Get the current robot state.
     * Thread-safe, can be called from any thread.
     */
    fun getState(): RobotState = currentState
    
    /**
     * Get the previous robot state.
     * Useful for transition logic and debugging.
     */
    fun getPreviousState(): RobotState = previousState
    
    /**
     * Transition to a new state.
     * Thread-safe state transition with history tracking.
     * Triggers state transition handlers.
     */
    fun transitionTo(newState: RobotState) {
        synchronized(this) {
            if (currentState != newState) {
                val oldState = currentState
                previousState = oldState
                currentState = newState
                
                // Notify transition handlers
                StateTransitionRegistry.notifyTransition(oldState, newState)
            }
        }
    }
    
    /**
     * Check if the robot is in a specific state.
     */
    fun isState(state: RobotState): Boolean = currentState == state
    
    /**
     * Check if the robot was previously in a specific state.
     */
    fun wasState(state: RobotState): Boolean = previousState == state
    
    /**
     * Reset to IDLE state.
     */
    fun reset() {
        transitionTo(RobotState.IDLE)
    }
    
    /**
     * Check if state transition is valid.
     * Can be overridden for custom validation logic.
     */
    fun canTransitionTo(newState: RobotState): Boolean {
        // Add validation logic here if needed
        return true
    }
}

