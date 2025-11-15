package teamcode.robot.command

import teamcode.robot.core.utils.Timer

/**
 * A command that waits for a specified duration.
 * 
 * Useful for adding delays in command sequences or timing operations.
 * 
 * Example:
 * ```
 * // Wait for 2 seconds
 * WaitCommand(2.0)
 * 
 * // Wait for 500 milliseconds
 * WaitCommand.millis(500)
 * ```
 */
class WaitCommand : Command {
    private val timer = Timer()
    private val durationSeconds: Double
    
    /**
     * Create a wait command with a duration in seconds.
     * 
     * @param seconds Duration to wait in seconds
     */
    constructor(seconds: Double) {
        this.durationSeconds = seconds
    }
    
    override fun initialize() {
        timer.start()
    }
    
    override fun isFinished(): Boolean {
        return timer.elapsedSeconds() >= durationSeconds
    }
    
    override fun end(interrupted: Boolean) {
        timer.stop()
    }
    
    companion object {
        /**
         * Create a wait command with a duration in milliseconds.
         * 
         * @param millis Duration to wait in milliseconds
         */
        @JvmStatic
        fun millis(millis: Long): WaitCommand {
            return WaitCommand(millis / 1000.0)
        }
    }
}

