package teamcode.robot.command

import teamcode.robot.core.subsystem.Subsystem

/**
 * Command that executes an action once and immediately finishes.
 * Useful for simple one-time actions.
 */
class InstantCommand(
    private val subsystem: Subsystem,
    private val action: () -> Unit
) : Command() {
    
    init {
        addRequirement(subsystem)
    }
    
    override fun initialize() {
        action()
    }
    
    override fun execute() {
        // Action already executed in initialize()
    }
    
    override fun isFinished(): Boolean = true
}

/**
 * Instant command that doesn't require a subsystem.
 */
class InstantCommandNoRequirement(
    private val action: () -> Unit
) : Command() {
    
    override fun initialize() {
        action()
    }
    
    override fun execute() {
        // Action already executed
    }
    
    override fun isFinished(): Boolean = true
}
