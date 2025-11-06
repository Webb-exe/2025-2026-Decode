package teamcode.robot.core.state

/**
 * Handler function for state transitions.
 * Called when entering or exiting a state.
 */
typealias StateTransitionHandler = (fromState: RobotState, toState: RobotState) -> Unit

/**
 * State transition handler registry.
 * Allows registering callbacks for state transitions.
 * 
 * Useful for:
 * - Logging state changes
 * - Triggering actions on state transitions
 * - Coordinating subsystems on state changes
 */
object StateTransitionRegistry {
    private val enterHandlers = mutableMapOf<RobotState, MutableList<StateTransitionHandler>>()
    private val exitHandlers = mutableMapOf<RobotState, MutableList<StateTransitionHandler>>()
    private val transitionHandlers = mutableListOf<StateTransitionHandler>()
    
    /**
     * Register a handler for when entering a specific state.
     */
    fun onEnter(state: RobotState, handler: StateTransitionHandler) {
        enterHandlers.getOrPut(state) { mutableListOf() }.add(handler)
    }
    
    /**
     * Register a handler for when exiting a specific state.
     */
    fun onExit(state: RobotState, handler: StateTransitionHandler) {
        exitHandlers.getOrPut(state) { mutableListOf() }.add(handler)
    }
    
    /**
     * Register a handler for any state transition.
     */
    fun onTransition(handler: StateTransitionHandler) {
        transitionHandlers.add(handler)
    }
    
    /**
     * Trigger handlers for a state transition.
     * Called internally by RobotStateMachine.
     */
    internal fun notifyTransition(fromState: RobotState, toState: RobotState) {
        // Call exit handlers for the old state
        exitHandlers[fromState]?.forEach { it(fromState, toState) }
        
        // Call enter handlers for the new state
        enterHandlers[toState]?.forEach { it(fromState, toState) }
        
        // Call general transition handlers
        transitionHandlers.forEach { it(fromState, toState) }
    }
    
    /**
     * Clear all handlers.
     */
    fun clear() {
        enterHandlers.clear()
        exitHandlers.clear()
        transitionHandlers.clear()
    }
}


