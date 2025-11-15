package teamcode.robot.command

import teamcode.robot.core.subsystem.Subsystem

/**
 * A command that runs multiple commands in sequence (one after another).
 * 
 * Child commands are executed directly by the group (not via the scheduler) in order.
 * When one finishes, the next begins.
 * The group finishes when all commands have completed.
 * 
 * Requirements are the union of all child command requirements.
 * 
 * Example:
 * ```
 * // Run commands in sequence
 * SequentialCommandGroup(
 *     DriveForward(1.0),
 *     WaitCommand(0.5),
 *     TurnLeft(90.0),
 *     Shoot()
 * )
 * 
 * // Alternative construction
 * SequentialCommandGroup.of(
 *     command1,
 *     command2,
 *     command3
 * )
 * ```
 */
class SequentialCommandGroup : Command {
    private val commands: List<Command>
    private var currentIndex = 0
    private var currentCommand: Command? = null
    
    /**
     * Create a sequential command group.
     * 
     * @param commands Commands to run in sequence
     */
    constructor(vararg commands: Command) {
        this.commands = commands.toList()
        // Add all child requirements to this group's requirements
        for (command in commands) {
            this.requirements.addAll(command.requirements)
        }

        interruptible = commands.all { it.interruptible }

    }
    
    /**
     * Create a sequential command group from a list.
     * 
     * @param commands List of commands to run in sequence
     */
    constructor(commands: List<Command>) {
        this.commands = commands
        // Add all child requirements to this group's requirements
        for (command in commands) {
            this.requirements.addAll(command.requirements)
        }
    }
    
    override fun initialize() {
        currentIndex = 0
        currentCommand = null
        // Initialize first command directly
        if (commands.isNotEmpty()) {
            try {
                currentCommand = commands[0]
                currentCommand?.initialize()
            } catch (e: Exception) {
                System.err.println("Error initializing first command in SequentialCommandGroup: ${e.message}")
                e.printStackTrace()
                currentIndex++
                currentCommand = null
            }
        }
    }
    
    override fun periodic() {
        if (currentIndex >= commands.size) return
        
        val current = currentCommand
        if (current == null) {
            // Current command failed to initialize, move to next
            currentIndex++
            if (currentIndex < commands.size) {
                try {
                    currentCommand = commands[currentIndex]
                    currentCommand?.initialize()
                } catch (e: Exception) {
                    System.err.println("Error initializing next command in SequentialCommandGroup: ${e.message}")
                    e.printStackTrace()
                    currentIndex++
                    currentCommand = null
                }
            }
            return
        }
        
        try {
            // Execute the current command
            current.periodic()
            
            // Check if finished
            if (current.isFinishedInternal()) {
                // End the command normally
                try {
                    current.end(interrupted = false)
                } catch (e: Exception) {
                    System.err.println("Error ending command in SequentialCommandGroup: ${e.message}")
                    e.printStackTrace()
                }
                
                // Move to next command
                currentIndex++
                currentCommand = null
                
                // Initialize next command if available
                if (currentIndex < commands.size) {
                    try {
                        currentCommand = commands[currentIndex]
                        currentCommand?.initialize()
                    } catch (e: Exception) {
                        System.err.println("Error initializing next command in SequentialCommandGroup: ${e.message}")
                        e.printStackTrace()
                        currentIndex++
                        currentCommand = null
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("Error executing command in SequentialCommandGroup: ${e.message}")
            e.printStackTrace()
            // End current command on error and move to next
            try {
                current.end(interrupted = true)
            } catch (endError: Exception) {
                System.err.println("Error ending failed command: ${endError.message}")
            }
            currentIndex++
            currentCommand = null
        }
    }
    
    override fun isFinished(): Boolean {
        // Finished when we've gone through all commands
        return currentIndex >= commands.size
    }
    
    override fun end(interrupted: Boolean) {
        // End the current command if we were interrupted
        if (interrupted) {
            val current = currentCommand
            if (current != null) {
                try {
                    current.end(interrupted = true)
                } catch (e: Exception) {
                    System.err.println("Error ending current command in SequentialCommandGroup cleanup: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        currentCommand = null
    }
    
    companion object {
        /**
         * Create a sequential command group from multiple commands.
         * 
         * @param commands Commands to run in sequence
         */
        @JvmStatic
        fun of(vararg commands: Command): SequentialCommandGroup {
            return SequentialCommandGroup(*commands)
        }
    }
}

