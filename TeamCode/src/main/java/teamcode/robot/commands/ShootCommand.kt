package teamcode.robot.commands

import teamcode.robot.command.Command
import teamcode.robot.subsystems.ShooterSubsystem

/**
 * Command to shoot at a specific power for a duration.
 */
class ShootCommand(
    private val shooterSubsystem: ShooterSubsystem,
    private val power: Double = 1.0,
    private val durationMs: Long = 1000
) : Command() {
    
    private var startTime: Long = 0
    
    init {
        addRequirement(shooterSubsystem)
    }
    
    override fun initialize() {
        startTime = System.currentTimeMillis()
        shooterSubsystem.setPower(power)
    }
    
    override fun execute() {
        // Command runs until duration is reached
    }
    
    override fun isFinished(): Boolean {
        return System.currentTimeMillis() - startTime >= durationMs
    }
    
    override fun end(interrupted: Boolean) {
        shooterSubsystem.stopShooter()
        // State is managed by controller, not commands
    }
}
