package teamcode.robot.command

import teamcode.robot.core.utils.Timer

/**
 * A command that waits until a condition becomes true.
 * 
 * Optionally supports a timeout to prevent infinite waiting.
 * 
 * Example:
 * ```
 * // Wait until sensor detects something
 * WaitUntilCommand { colorSensor.hasDetected() }
 * 
 * // Wait with timeout (max 5 seconds)
 * WaitUntilCommand(
 *     condition = { shooter.isReady() },
 *     timeoutSeconds = 5.0
 * )
 * ```
 */
class WaitUntilCommand : Command {
    private val condition: () -> Boolean
    private val timeoutSeconds: Double?
    private val timer = Timer()
    
    /**
     * Create a wait command that waits until a condition is true.
     * 
     * @param condition Function that returns true when condition is met
     * @param timeoutSeconds Optional timeout in seconds (null = no timeout)
     */
    constructor(condition: () -> Boolean, timeoutSeconds: Double? = null) {
        this.condition = condition
        this.timeoutSeconds = timeoutSeconds
    }
    
    override fun initialize() {
        if (timeoutSeconds != null) {
            timer.start()
        }
    }
    
    override fun isFinished(): Boolean {
        // Check timeout first if specified
        if (timeoutSeconds != null && timer.elapsedSeconds() >= timeoutSeconds) {
            return true
        }
        
        // Check condition
        return try {
            condition.invoke()
        } catch (e: Exception) {
            System.err.println("Error evaluating WaitUntilCommand condition: ${e.message}")
            true // Finish on error to prevent infinite loop
        }
    }
    
    override fun end(interrupted: Boolean) {
        timer.stop()
    }
}

