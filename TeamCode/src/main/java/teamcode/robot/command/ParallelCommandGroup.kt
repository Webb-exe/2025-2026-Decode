package teamcode.robot.command

import teamcode.robot.core.subsystem.Subsystem

/**
 * A command that runs multiple commands in parallel.
 * 
 * All child commands are executed directly by the group (not via the scheduler).
 * The group finishes when all commands have finished (or when interrupted).
 * 
 * Requirements are the union of all child command requirements,
 * ensuring no conflicts occur.
 * 
 * Example:
 * ```
 * // Run two commands simultaneously
 * ParallelCommandGroup(
 *     SpinUpShooter(),
 *     RaiseElevator()
 * )
 * 
 * // Alternative construction
 * ParallelCommandGroup.of(
 *     command1,
 *     command2,
 *     command3
 * )
 * ```
 */
class ParallelCommandGroup : Command {
    private val commands: List<Command>
    private val finishedCommands = mutableSetOf<Command>()
    
    /**
     * Create a parallel command group.
     * 
     * @param commands Commands to run in parallel
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
     * Create a parallel command group from a list.
     * 
     * @param commands List of commands to run in parallel
     */
    constructor(commands: List<Command>) {
        this.commands = commands
        // Add all child requirements to this group's requirements
        for (command in commands) {
            if (command.requirements.any { it in requirements}){
                throw error("Cannot use the same subsystem in multiple parallel commands")
            }
            this.requirements.addAll(command.requirements)
        }
    }
    
    override fun initialize() {
        finishedCommands.clear()
        // Initialize all child commands directly (not via scheduler)
        for (command in commands) {
            try {
                command.initialize()
            } catch (e: Exception) {
                System.err.println("Error initializing command in ParallelCommandGroup: ${e.message}")
                e.printStackTrace()
                finishedCommands.add(command)
            }
        }
    }
    
    override fun periodic() {
        // Execute and check all active child commands directly
        for (command in commands) {
            if (command in finishedCommands) continue
            
            try {
                // Call periodic on the child command
                command.periodic()
                
                // Check if command finished
                if (command.isFinishedInternal()) {
                    // End the command normally
                    try {
                        command.end(interrupted = false)
                    } catch (e: Exception) {
                        System.err.println("Error ending command in ParallelCommandGroup: ${e.message}")
                        e.printStackTrace()
                    }
                    finishedCommands.add(command)
                }
            } catch (e: Exception) {
                System.err.println("Error executing command in ParallelCommandGroup: ${e.message}")
                e.printStackTrace()
                // Mark as finished on error
                try {
                    command.end(interrupted = true)
                } catch (endError: Exception) {
                    System.err.println("Error ending failed command: ${endError.message}")
                }
                finishedCommands.add(command)
            }
        }
    }
    
    override fun isFinished(): Boolean {
        // Finished when all commands are finished
        return finishedCommands.size == commands.size
    }
    
    override fun end(interrupted: Boolean) {
        // End all commands that haven't finished yet
        if (interrupted) {
            for (command in commands) {
                if (command !in finishedCommands) {
                    try {
                        command.end(interrupted = true)
                    } catch (e: Exception) {
                        System.err.println("Error ending command in ParallelCommandGroup cleanup: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }
        finishedCommands.clear()
    }
    
    companion object {
        /**
         * Create a parallel command group from multiple commands.
         * 
         * @param commands Commands to run in parallel
         */
        @JvmStatic
        fun of(vararg commands: Command): ParallelCommandGroup {
            return ParallelCommandGroup(*commands)
        }
    }
}

