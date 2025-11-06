package teamcode.robot.command

import teamcode.robot.core.state.RobotState
import teamcode.robot.core.state.RobotStateMachine
import teamcode.robot.core.subsystem.Subsystem

/**
 * Command that only runs when robot is in a specific state.
 * Automatically finishes when state changes.
 * 
 * Useful for state-dependent actions.
 */
abstract class StateCommand(
    private val requiredState: RobotState,
    vararg subsystems: Subsystem
) : Command() {
    
    init {
        addRequirements(*subsystems)
    }
    
    override fun initialize() {
        // Verify we're in the correct state
        // State should be set by controller before this command is scheduled
        // If state doesn't match, command will finish immediately
    }
    
    override fun isFinished(): Boolean {
        // Command finishes if state changes away from required state
        return !RobotStateMachine.isState(requiredState)
    }
}

/**
 * Command that requires a specific state to be active.
 * Will not start if state is not active.
 */
abstract class ConditionalStateCommand(
    private val requiredState: RobotState,
    vararg subsystems: Subsystem
) : Command() {
    
    init {
        addRequirements(*subsystems)
    }
    
    override fun initialize() {
        // Check if we can run
        if (!RobotStateMachine.isState(requiredState)) {
            // Command will finish immediately
            return
        }
    }
    
    override fun isFinished(): Boolean {
        // Finish if state changes
        return !RobotStateMachine.isState(requiredState)
    }
}

