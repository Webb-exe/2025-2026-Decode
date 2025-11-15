package teamcode.robot.command

import teamcode.robot.core.subsystem.Subsystem
import teamcode.threading.RobotThread
import kotlin.concurrent.Volatile

/**
 * Command scheduler thread that manages command lifecycle.
 * 
 * The scheduler runs on its own thread and:
 * - Schedules commands and manages their lifecycle (initialize, execute, end)
 * - Enforces one command per subsystem rule
 * - Cancels conflicting commands when new ones are scheduled
 * - Handles timing, waiting, and isFinished() checks
 * - Provides thread-safe command scheduling
 * 
 * Commands execute their logic on the scheduler thread, but subsystem method
 * calls are transparently queued to subsystem threads via proxies.
 * 
 * Usage:
 * ```
 * // Get scheduler instance (available after ThreadedOpMode initialization)
 * val scheduler = CommandScheduler.getInstance()
 * 
 * // Schedule a command
 * scheduler.schedule(DriveForward(2.0))
 * 
 * // Cancel a specific command
 * scheduler.cancel(myCommand)
 * 
 * // Cancel all commands
 * scheduler.cancelAll()
 * ```
 */
class CommandScheduler : RobotThread("CommandScheduler", 20) {
    
    /**
     * List of currently active commands.
     */
    private val activeCommands = mutableListOf<Command>()
    
    /**
     * Map of subsystems to the command currently using them.
     * Used for conflict detection and resolution.
     */
    private val subsystemCommands = mutableMapOf<Class<out Subsystem>, Command>()
    
    /**
     * Commands pending scheduling (thread-safe queue).
     */
    private val pendingCommands = java.util.concurrent.ConcurrentLinkedQueue<Command>()
    
    /**
     * Commands pending cancellation (thread-safe queue).
     */
    private val pendingCancellations = java.util.concurrent.ConcurrentLinkedQueue<Command>()
    
    /**
     * Schedule a command to run.
     * Thread-safe - can be called from any thread.
     * 
     * When scheduled:
     * 1. Checks which subsystems the command requires
     * 2. Cancels any existing commands using those subsystems
     * 3. Calls command.initialize()
     * 4. Adds command to active list
     * 
     * @param command The command to schedule
     */
    fun schedule(command: Command) {
        pendingCommands.offer(command)
    }
    
    /**
     * Cancel a specific command.
     * Thread-safe - can be called from any thread.
     * 
     * @param command The command to cancel
     */
    fun cancel(command: Command) {
        pendingCancellations.offer(command)
    }
    
    /**
     * Cancel all active commands.
     * Thread-safe - can be called from any thread.
     */
    fun cancelAll() {
        synchronized(activeCommands) {
            activeCommands.toList().forEach { cancel(it) }
        }
    }
    
    /**
     * Main scheduler loop.
     * Executes on scheduler thread.
     */
    override fun runLoop() {
        // Process pending cancellations first
        processPendingCancellations()
        
        // Process pending command schedules
        processPendingSchedules()
        
        // Execute all active commands
        executeActiveCommands()
        
        // Update telemetry
        updateSchedulerTelemetry()
    }
    
    /**
     * Process commands pending cancellation.
     */
    private fun processPendingCancellations() {
        while (true) {
            val command = pendingCancellations.poll() ?: break
            cancelCommandInternal(command)
        }
    }
    
    /**
     * Process commands pending scheduling.
     */
    private fun processPendingSchedules() {
        while (true) {
            val command = pendingCommands.poll() ?: break
            scheduleCommandInternal(command)
        }
    }
    
    /**
     * Actually schedule a command (runs on scheduler thread).
     * Returns true if scheduled successfully, false if blocked by non-interruptible command.
     */
    private fun scheduleCommandInternal(command: Command): Boolean {
        try {
            // Get command's requirements
            val requirements = command.requirements
            
            // Check for non-interruptible conflicts
            val nonInterruptibleConflicts = mutableListOf<Command>()
            for (subsystemClass in requirements) {
                val existingCommand = subsystemCommands[subsystemClass]
                if (existingCommand != null && !existingCommand.interruptible) {
                    nonInterruptibleConflicts.add(existingCommand)
                }
            }
            
            // If any non-interruptible conflicts exist, cannot schedule
            if (nonInterruptibleConflicts.isNotEmpty()) {
                System.err.println(
                    "Cannot schedule ${command.javaClass.simpleName}: " +
                    "Blocked by non-interruptible command(s): " +
                    nonInterruptibleConflicts.joinToString { it.javaClass.simpleName }
                )
                return false
            }
            
            // Cancel any conflicting interruptible commands
            val conflictingCommands = mutableSetOf<Command>()
            for (subsystemClass in requirements) {
                val existingCommand = subsystemCommands[subsystemClass]
                if (existingCommand != null) {
                    conflictingCommands.add(existingCommand)
                }
            }
            
            // Cancel all conflicting commands (all are interruptible at this point)
            conflictingCommands.forEach { cancelCommandInternal(it) }
            
            // Initialize the command
            command.initialize()
            
            // Add to active commands
            activeCommands.add(command)
            
            // Update subsystem mappings
            for (subsystemClass in requirements) {
                subsystemCommands[subsystemClass] = command
            }
            
            return true
            
        } catch (e: Exception) {
            System.err.println("Error scheduling command: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Cancel a command (runs on scheduler thread).
     */
    private fun cancelCommandInternal(command: Command) {
        if (!activeCommands.contains(command)) {
            return
        }
        
        try {
            // Call end with interrupted = true
            command.end(interrupted = true)
        } catch (e: Exception) {
            System.err.println("Error ending command: ${e.message}")
            e.printStackTrace()
        } finally {
            // Remove from active commands
            activeCommands.remove(command)
            
            // Remove from subsystem mappings
            val requirements = command.requirements
            for (subsystemClass in requirements) {
                if (subsystemCommands[subsystemClass] == command) {
                    subsystemCommands.remove(subsystemClass)
                }
            }
        }
    }
    
    /**
     * Execute all active commands.
     */
    private fun executeActiveCommands() {
        // Create a copy to avoid concurrent modification
        val commandsToExecute = activeCommands.toList()
        
        for (command in commandsToExecute) {
            try {
                // Execute the command
                command.periodic()
                
                // Check if finished
                if (command.isFinishedInternal()) {
                    // End normally
                    try {
                        command.end(interrupted = false)
                    } catch (e: Exception) {
                        System.err.println("Error ending command: ${e.message}")
                        e.printStackTrace()
                    }
                    
                    // Remove from active commands
                    activeCommands.remove(command)
                    
                    // Remove from subsystem mappings
                    val requirements = command.requirements
                    for (subsystemClass in requirements) {
                        if (subsystemCommands[subsystemClass] == command) {
                            subsystemCommands.remove(subsystemClass)
                        }
                    }
                }
            } catch (e: Exception) {
                // Command threw exception - cancel it
                System.err.println("Command threw exception, canceling: ${e.message}")
                e.printStackTrace()
                cancelCommandInternal(command)
            }
        }
    }
    
    /**
     * Update telemetry for the scheduler.
     */
    private fun updateSchedulerTelemetry() {
        telemetry.addData("Active Commands", activeCommands.size)
        telemetry.addData("Busy Subsystems", subsystemCommands.size)
        
        if (activeCommands.isNotEmpty()) {
            telemetry.addData("Commands", activeCommands.joinToString(", ") { 
                val name = it.javaClass.simpleName
                if (!it.interruptible) "$name [NON-INTERRUPTIBLE]" else name
            })
        }
    }
    
    /**
     * Get the number of active commands.
     */
    fun getActiveCommandCount(): Int = activeCommands.size
    
    /**
     * Check if a specific command is active.
     */
    fun isCommandActive(command: Command): Boolean = activeCommands.contains(command)
    
    companion object {
        @Volatile
        private var instance: CommandScheduler? = null
        
        /**
         * Get the singleton CommandScheduler instance.
         * Thread-safe singleton access.
         */
        @JvmStatic
        fun getInstance(): CommandScheduler {
            return instance ?: throw IllegalStateException(
                "CommandScheduler not initialized. Make sure ThreadedOpMode has started."
            )
        }
        
        /**
         * Set the singleton instance (called by ThreadedOpMode).
         */
        internal fun setInstance(scheduler: CommandScheduler?) {
            instance = scheduler
        }
    }
}

