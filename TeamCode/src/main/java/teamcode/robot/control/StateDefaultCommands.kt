package teamcode.robot.control

import teamcode.robot.command.Command
import teamcode.robot.core.state.RobotState
import teamcode.robot.core.state.RobotStateMachine
import teamcode.robot.core.state.StateTransitionRegistry
import teamcode.robot.core.subsystem.Subsystem

/**
 * State-specific default commands.
 * Allows different default commands for different robot states.
 * 
 * Usage:
 * ```
 * StateDefaultCommands.set(shooter, RobotState.IDLE, ShooterIdleCommand(shooter))
 * StateDefaultCommands.set(shooter, RobotState.SHOOTING, ShooterActiveCommand(shooter))
 * ```
 */
object StateDefaultCommands {
    
    private val stateDefaults = mutableMapOf<Subsystem, MutableMap<RobotState, Command>>()
    private val currentDefaults = mutableMapOf<Subsystem, Command?>()
    
    /**
     * Set a default command for a subsystem when in a specific state.
     * 
     * @param subsystem The subsystem
     * @param state The state this default applies to
     * @param command The default command (null to clear)
     */
    fun set(subsystem: Subsystem, state: RobotState, command: Command?) {
        val subsystemDefaults = stateDefaults.getOrPut(subsystem) { mutableMapOf() }
        
        if (command == null) {
            subsystemDefaults.remove(state)
        } else {
            command.addRequirement(subsystem)
            subsystemDefaults[state] = command
        }
        
        // Update current default if we're in this state
        if (RobotStateMachine.getState() == state) {
            updateDefaultForState(subsystem, state)
        }
    }
    
    /**
     * Initialize state default commands.
     * Should be called in initializeCommandSystem().
     */
    fun initialize() {
        // Register transition handler to update defaults on state changes
        StateTransitionRegistry.onTransition { _, toState ->
            updateAllDefaultsForState(toState)
        }
        
        // Set initial defaults for current state
        val currentState = RobotStateMachine.getState()
        updateAllDefaultsForState(currentState)
    }
    
    /**
     * Update default command for a subsystem based on current state.
     */
    private fun updateDefaultForState(subsystem: Subsystem, state: RobotState) {
        val scheduler = CommandScheduler.getInstance()
        val subsystemDefaults = stateDefaults[subsystem]
        
        // Cancel current default if it exists
        currentDefaults[subsystem]?.let { currentCmd ->
            if (currentCmd.isScheduled()) {
                scheduler.cancel(currentCmd)
            }
        }
        
        // Set new default for this state
        val newDefault = subsystemDefaults?.get(state)
        currentDefaults[subsystem] = newDefault
        
        if (newDefault != null) {
            // Only set if no other command is using the subsystem
            if (subsystem.getCurrentCommand() == null) {
                scheduler.setDefaultCommand(subsystem, newDefault)
            }
        } else {
            scheduler.setDefaultCommand(subsystem, null)
        }
    }
    
    /**
     * Update all subsystem defaults for a state.
     */
    private fun updateAllDefaultsForState(state: RobotState) {
        stateDefaults.keys.forEach { subsystem ->
            updateDefaultForState(subsystem, state)
        }
    }
    
    /**
     * Clear all state defaults.
     */
    fun clear() {
        stateDefaults.clear()
        currentDefaults.clear()
    }
}

