package teamcode.commands

import teamcode.robot.command.Command
import teamcode.robot.subsystems.KickerState
import teamcode.robot.subsystems.KickerSubsystem

/**
 * Command to trigger the kicker mechanism.
 * Calls subsystem methods directly (cross-thread).
 */
class TriggerKicker: Command(false) {
    
    // Get subsystem reference - automatically adds to requirements
    private val kicker = current<KickerSubsystem>()
    
    private var triggered = false

    override fun initialize() {
        kicker.triggerKicker()
    }

    override fun periodic() {
    }

    override fun isFinished(): Boolean {
        return true
    }
}