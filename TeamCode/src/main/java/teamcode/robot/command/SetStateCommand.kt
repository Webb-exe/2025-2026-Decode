package teamcode.robot.command

import teamcode.robot.core.state.RobotState
import teamcode.robot.core.state.RobotStateMachine

/**
 * Command that changes the robot's state.
 * 
 * This command has no subsystem requirements and finishes immediately.
 * It can be used with bindTriggers() to bind gamepad inputs to state changes.
 * 
 * Usage:
 * ```
 * bindTriggers(gamepad1) {
 *     rightTrigger.whileHeld {
 *         SetStateCommand(RobotState.SHOOTING)
 *     }
 *     a.whenPressed {
 *         SetStateCommand(RobotState.INTAKING)
 *     }
 *     b.whenReleased {
 *         SetStateCommand(RobotState.IDLE)
 *     }
 * }
 * ```
 */
class SetStateCommand(
    private val state: RobotState
) : Command() {
    
    override fun initialize() {
        RobotStateMachine.transitionTo(state)
    }
    
    override fun execute() {
        // State change happens in initialize()
    }
    
    override fun isFinished(): Boolean = true
}

