package teamcode.robot.control

import teamcode.robot.command.Command
import teamcode.robot.core.state.RobotStateMachine
import teamcode.robot.core.subsystem.Subsystem
import teamcode.threading.RobotThread
import kotlin.concurrent.Volatile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Command scheduler that manages command execution across subsystems.
 * 
 * Responsibilities:
 * - Schedule commands to subsystems
 * - Manage resource conflicts (subsystem requirements)
 * - Handle command lifecycle
 * - Manage default commands
 * - Handle auto-command bindings
 * 
 * Runs on its own thread for automatic command management.
 */
class CommandScheduler(
    updateIntervalMs: Long = 20
) : RobotThread("CommandScheduler", updateIntervalMs) {
    
    private val scheduledCommands: MutableMap<Subsystem, Command> = ConcurrentHashMap()
    private val defaultCommands: MutableMap<Subsystem, Command> = ConcurrentHashMap()
    private val commandQueue: MutableList<Command> = CopyOnWriteArrayList()
    private val autoBindings: MutableList<teamcode.robot.control.AutoBinding> = CopyOnWriteArrayList()
    
    @Volatile
    var isEnabled: Boolean = true
        private set
    
    companion object {
        @Volatile
        private var instance: CommandScheduler? = null
        
        /**
         * Get the singleton command scheduler instance.
         */
        fun getInstance(): CommandScheduler {
            return instance ?: error("CommandScheduler not initialized. Use ThreadedOpMode.")
        }
        
        /**
         * Register the scheduler instance (called by ThreadedOpMode).
         */
        internal fun register(scheduler: CommandScheduler) {
            instance = scheduler
        }
    }
    
    /**
     * Schedule a command.
     */
    fun schedule(command: Command): Boolean {
        if (!isEnabled) return false
        
        synchronized(this) {
            val conflicts = findConflicts(command)
            
            // Check if any conflicts are non-interruptible
            val nonInterruptibleConflicts = conflicts.filter { it.nonInterruptible }
            if (nonInterruptibleConflicts.isNotEmpty()) {
                // Cannot schedule if there are non-interruptible conflicts
                return false
            }
            
            if (conflicts.isEmpty()) {
                scheduleCommand(command)
                return true
            } else {
                conflicts.forEach { it.cancel() }
                scheduleCommand(command)
                return true
            }
        }
    }
    
    /**
     * Set a default command for a subsystem.
     */
    fun setDefaultCommand(subsystem: Subsystem, command: Command?) {
        synchronized(this) {
            if (command == null) {
                defaultCommands.remove(subsystem)
            } else {
                command.addRequirements(subsystem)
                defaultCommands[subsystem] = command
                
                if (!scheduledCommands.containsKey(subsystem)) {
                    scheduleCommand(command)
                }
            }
        }
    }
    
    /**
     * Cancel a command.
     */
    fun cancel(command: Command) {
        synchronized(this) {
            if (!command.nonInterruptible) {
                command.cancel()
            }
        }
    }
    
    /**
     * Cancel all commands for a subsystem.
     */
    fun cancelAll(subsystem: Subsystem) {
        synchronized(this) {
            scheduledCommands[subsystem]?.let { cmd ->
                if (!cmd.nonInterruptible) {
                    // Cancel the command (this will mark it as finished)
                    cmd.cancel()
                    
                    // Clear the command from all subsystems it was scheduled on
                    val subsystemsToClear = scheduledCommands.filter { (_, scheduledCmd) -> scheduledCmd == cmd }
                        .keys
                    
                    subsystemsToClear.forEach { sub ->
                        sub.clearCommand()
                        scheduledCommands.remove(sub)
                    }
                }
            }
            
            if (!scheduledCommands.containsKey(subsystem)) {
                defaultCommands[subsystem]?.let { scheduleCommand(it) }
            }
        }
    }
    
    /**
     * Cancel all commands.
     */
    fun cancelAll() {
        synchronized(this) {
            // Get all unique commands (a command may be on multiple subsystems)
            val allCommands = scheduledCommands.values.distinct()
            
            allCommands.forEach { cmd ->
                if (!cmd.nonInterruptible) {
                    cmd.cancel()
                }
            }
            
            // Clear all non-interruptible commands from all subsystems
            scheduledCommands.entries.removeAll { (_, cmd) -> !cmd.nonInterruptible }
            
            // Clear commands from subsystems
            scheduledCommands.keys.forEach { subsystem ->
                subsystem.clearCommand()
            }
            
            // Restore default commands
            defaultCommands.forEach { (subsystem, cmd) ->
                if (!scheduledCommands.containsKey(subsystem)) {
                    scheduleCommand(cmd)
                }
            }
        }
    }
    
    /**
     * Register an auto-binding.
     */
    fun registerBinding(binding: teamcode.robot.control.AutoBinding) {
        synchronized(this) {
            autoBindings.add(binding)
        }
    }
    
    /**
     * Unregister an auto-binding.
     */
    fun unregisterBinding(binding: teamcode.robot.control.AutoBinding) {
        synchronized(this) {
            binding.cancel()
            autoBindings.remove(binding)
        }
    }
    
    /**
     * Check if a binding is registered.
     */
    fun isBindingRegistered(binding: teamcode.robot.control.AutoBinding): Boolean {
        synchronized(this) {
            return autoBindings.contains(binding)
        }
    }
    
    /**
     * Main scheduler loop.
     */
    override fun runLoop() {
        if (!isEnabled) {
            telemetry.addData("Status", "Disabled")
            return
        }
        
        synchronized(this) {
            // Remove finished commands
            // Find all unique finished commands (a command may be on multiple subsystems)
            val finishedCommands = scheduledCommands.values
                .filter { !it.isScheduled() }
                .distinct()
            
            // For each finished command, clear it from all subsystems
            finishedCommands.forEach { cmd ->
                val subsystemsToClear = scheduledCommands.filter { (_, scheduledCmd) -> scheduledCmd == cmd }
                    .keys
                
                subsystemsToClear.forEach { subsystem ->
                    subsystem.clearCommand()
                    scheduledCommands.remove(subsystem)
                    
                    // Restore default command if available
                    defaultCommands[subsystem]?.let { defaultCmd ->
                        if (!defaultCmd.isScheduled()) {
                            scheduleCommand(defaultCmd)
                        }
                    }
                }
            }
            
            // Process queued commands
            val queued = commandQueue.toList()
            commandQueue.clear()
            queued.forEach { schedule(it) }
            
            // Update auto-bindings
            autoBindings.forEach { it.update() }
        }
        
        updateTelemetry()
    }
    
    private fun findConflicts(command: Command): List<Command> {
        val conflicts = mutableListOf<Command>()
        val commandReqs = command.getRequirements()
        
        scheduledCommands.values.forEach { scheduledCmd ->
            val scheduledReqs = scheduledCmd.getRequirements()
            if (commandReqs.intersect(scheduledReqs).isNotEmpty()) {
                conflicts.add(scheduledCmd)
            }
        }
        
        return conflicts
    }
    
    private fun scheduleCommand(command: Command) {
        // Re-detect dependencies if command has no requirements yet (fallback)
        if (command.getRequirements().isEmpty()) {
            command.reDetectDependencies()
        }
        
        val requirements = command.getRequirements()
        if (requirements.isEmpty()) return
        
        command.start()
        
        // Set the command on ALL required subsystems, not just the first one
        // This allows the command to run on all subsystem threads
        requirements.forEach { subsystem ->
            subsystem.setCommand(command)
            scheduledCommands[subsystem] = command
        }
        
        commandQueue.remove(command)
    }
    
    private fun updateTelemetry() {
        telemetry.addData("Status", "Running")
        telemetry.addData("Enabled", isEnabled)
        telemetry.addData("Scheduled", scheduledCommands.size)
        telemetry.addData("Defaults", defaultCommands.size)
        telemetry.addData("Bindings", autoBindings.size)
        telemetry.addData("Robot State", RobotStateMachine.getState().name)
    }
    
    override fun onStop() {
        autoBindings.forEach { it.cancel() }
        autoBindings.clear()
        cancelAll()
    }
}


