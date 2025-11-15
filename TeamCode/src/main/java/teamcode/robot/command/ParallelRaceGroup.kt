package teamcode.robot.command

/**
 * A parallel command group that finishes when ANY command finishes.
 * 
 * All child commands are executed directly by the group (not via the scheduler).
 * The group ends as soon as any one command completes.
 * All other commands are interrupted.
 * 
 * Useful for timeouts or "whichever finishes first" scenarios.
 * 
 * Example:
 * ```
 * // Drive forward until wall detected OR 5 seconds elapsed
 * ParallelRaceGroup(
 *     DriveForwardContinuous(),  // Runs forever
 *     WaitUntilCommand { wallSensor.isPressed() }  // Or this finishes
 * )
 * 
 * // Shoot with timeout
 * ParallelRaceGroup(
 *     ShootSequence(),      // Main action
 *     WaitCommand(3.0)      // Timeout
 * )
 * ```
 */
class ParallelRaceGroup : Command {
    private val commands: List<Command>
    private val finishedCommands = mutableSetOf<Command>()
    
    /**
     * Create a parallel race group.
     * 
     * @param commands Commands to run in parallel (finishes when first completes)
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
     * Create a parallel race group from a list.
     * 
     * @param commands List of commands to run in parallel
     */
    constructor(commands: List<Command>) {
        this.commands = commands
        // Add all child requirements to this group's requirements
        for (command in commands) {
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
                System.err.println("Error initializing command in ParallelRaceGroup: ${e.message}")
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
                    // In race mode, first to finish wins - end it normally
                    try {
                        command.end(interrupted = false)
                    } catch (e: Exception) {
                        System.err.println("Error ending command in ParallelRaceGroup: ${e.message}")
                        e.printStackTrace()
                    }
                    finishedCommands.add(command)
                    return  // Stop checking others - race is over
                }
            } catch (e: Exception) {
                System.err.println("Error executing command in ParallelRaceGroup: ${e.message}")
                e.printStackTrace()
                // Mark as finished on error
                try {
                    command.end(interrupted = true)
                } catch (endError: Exception) {
                    System.err.println("Error ending failed command: ${endError.message}")
                }
                finishedCommands.add(command)
                return  // Race over due to error
            }
        }
    }
    
    override fun isFinished(): Boolean {
        // Finished when ANY command is finished (race condition)
        return finishedCommands.isNotEmpty()
    }
    
    override fun end(interrupted: Boolean) {
        // End all commands that haven't finished yet
        for (command in commands) {
            if (command !in finishedCommands) {
                try {
                    command.end(interrupted = true)
                } catch (e: Exception) {
                    System.err.println("Error ending command in ParallelRaceGroup cleanup: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        finishedCommands.clear()
    }
    
    companion object {
        /**
         * Create a parallel race group from multiple commands.
         * 
         * @param commands Commands to run in parallel (finishes when first completes)
         */
        @JvmStatic
        fun of(vararg commands: Command): ParallelRaceGroup {
            return ParallelRaceGroup(*commands)
        }
    }
}

