package teamcode.robot.commands

import teamcode.robot.command.Command
import teamcode.robot.subsystems.ShooterSubsystem

/**
 * Command that controls shooter power based on trigger input.
 */
class ShootWithTriggerCommand(
    private val shooterSubsystem: ShooterSubsystem,
    private val getTriggerValue: () -> Double
) : Command() {
    
    init {
        addRequirement(shooterSubsystem)
    }
    
    override fun initialize() {
        // State is managed by controller, not commands
    }
    
    override fun execute() {
        shooterSubsystem.setPower(getTriggerValue())
    }
    
    override fun isFinished(): Boolean {
        return getTriggerValue() <= 0.0
    }
    
    override fun end(interrupted: Boolean) {
        shooterSubsystem.stopShooter()
        // State is managed by controller, not commands
    }
}
