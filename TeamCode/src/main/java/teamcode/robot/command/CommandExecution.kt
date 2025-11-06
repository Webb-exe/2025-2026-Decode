package teamcode.robot.command

import teamcode.robot.control.CommandScheduler

/**
 * Extension functions for command execution.
 * Provides auto-scheduling and instant execution capabilities.
 */

/**
 * Execute a command by automatically scheduling it.
 * Returns immediately (non-blocking).
 * Command runs on subsystem thread asynchronously.
 * 
 * Usage:
 * ```
 * ShootCommand(shooter).execute()  // Auto-schedules and returns
 * ```
 * 
 * @return The command instance (for chaining)
 */
fun Command.execute(): Command {
    CommandScheduler.getInstance().schedule(this)
    return this
}

/**
 * Execute a command synchronously in the current thread.
 * Blocks until command completes or is interrupted.
 * Useful for actions that must complete before continuing.
 * 
 * Note: This runs the command immediately without scheduling.
 * The command lifecycle is managed manually in this method.
 * 
 * Usage:
 * ```
 * ShootCommand(shooter).executeInstant()  // Blocks until complete
 * ```
 */
fun Command.executeInstant() {
    // Run the command lifecycle synchronously in the current thread
    // This bypasses the scheduler and runs the command immediately
    executeInstantInternal()
}

