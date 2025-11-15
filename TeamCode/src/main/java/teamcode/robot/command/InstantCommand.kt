package teamcode.robot.command

/**
 * A command that runs instantly and finishes immediately.
 * 
 * Useful for one-shot actions that don't need repeated execution.
 * 
 * Example:
 * ```
 * // Lambda style
 * InstantCommand { println("Hello!") }
 * 
 * // With subsystem access
 * InstantCommand {
 *     val drive = RobotThread.current<DriveSubsystem>()
 *     drive.resetEncoders()
 * }
 * ```
 */
class InstantCommand(private val action: () -> Unit) : Command() {
    
    override fun initialize() {
        action.invoke()
    }
    
    override fun isFinished(): Boolean = true
}

