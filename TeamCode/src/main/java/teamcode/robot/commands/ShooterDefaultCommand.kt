package teamcode.robot.commands

import teamcode.robot.command.Command
import teamcode.robot.subsystems.ShooterSubsystem

/**
 * Default command for shooter - keeps it stopped.
 */
class ShooterDefaultCommand(
    private val shooterSubsystem: ShooterSubsystem
) : Command() {
    
    init {
        addRequirement(shooterSubsystem)
    }
    
    override fun execute() {
        shooterSubsystem.stopShooter()
    }
    
    override fun isFinished(): Boolean = false
}
