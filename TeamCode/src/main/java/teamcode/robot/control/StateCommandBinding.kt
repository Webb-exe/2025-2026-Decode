package teamcode.robot.control

import teamcode.robot.command.Command
import teamcode.robot.command.execute
import teamcode.robot.core.state.RobotState
import teamcode.robot.core.state.StateTransitionRegistry

/**
 * State-based command binding.
 * Automatically schedules commands when robot enters specific states.
 * 
 * Usage:
 * ```
 * StateCommandBinding.onState(RobotState.SHOOTING) {
 *     ShootCommand(shooter)
 * }
 * ```
 */
object StateCommandBinding {
    
    /**
     * Bind a command to be scheduled when entering a state.
     * The command will be automatically scheduled when the robot transitions to the target state.
     * Uses the execute() extension function for auto-scheduling.
     * 
     * @param state The state to trigger on
     * @param commandFactory Function that creates the command to schedule
     */
    fun onState(state: RobotState, commandFactory: () -> Command) {
        StateTransitionRegistry.onEnter(state) { _, _ ->
            commandFactory().execute()  // Auto-schedules via execute()
        }
    }
    
    /**
     * Bind a command to be scheduled when exiting a state.
     * Uses the execute() extension function for auto-scheduling.
     * 
     * @param state The state to trigger on exit
     * @param commandFactory Function that creates the command to schedule
     */
    fun onStateExit(state: RobotState, commandFactory: () -> Command) {
        StateTransitionRegistry.onExit(state) { _, _ ->
            commandFactory().execute()  // Auto-schedules via execute()
        }
    }
    
    /**
     * Bind a command that only runs while in a specific state.
     * Command is scheduled when entering state, cancelled when exiting.
     * Uses the execute() extension function for auto-scheduling.
     * 
     * @param state The state that must be active
     * @param commandFactory Function that creates the command
     */
    fun whileInState(state: RobotState, commandFactory: () -> Command) {
        var currentCommand: Command? = null
        
        StateTransitionRegistry.onEnter(state) { _, _ ->
            currentCommand = commandFactory().execute()  // Auto-schedules
        }
        
        StateTransitionRegistry.onExit(state) { _, _ ->
            currentCommand?.let { cmd ->
                CommandScheduler.getInstance().cancel(cmd)
                currentCommand = null
            }
        }
    }
}

