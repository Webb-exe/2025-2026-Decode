package teamcode.robot.commands

import teamcode.robot.command.Command
import teamcode.robot.subsystems.ShooterSubsystem

/**
 * Command to shoot at a specific power for a duration.
 * 
 * Dependencies are automatically detected from constructor parameters.
 * The shooterSubsystem parameter is automatically added as a requirement - no init block needed!
 */
class ShootCommand(
    private val shooterSubsystem: ShooterSubsystem,
    private val power: Double = 1.0,
    private val durationMs: Long = 1000
) : Command() {
    
    private var startTime: Long = 0
    
    // No init block needed! shooterSubsystem is automatically detected and registered
    
    override fun initialize() {
        startTime = System.currentTimeMillis()
        // Access dependency directly via property name
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
