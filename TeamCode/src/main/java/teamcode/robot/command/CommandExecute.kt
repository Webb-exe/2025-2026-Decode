package teamcode.robot.command

/**
 * Extension function to schedule a command on the CommandScheduler.
 * 
 * This provides a convenient way to schedule commands using .execute() syntax,
 * which actually schedules them on the CommandScheduler rather than executing directly.
 * 
 * Usage:
 * ```
 * myCommand.execute()  // Schedules on CommandScheduler
 * ```
 */
fun Command.execute() {
    CommandScheduler.getInstance().schedule(this)
}