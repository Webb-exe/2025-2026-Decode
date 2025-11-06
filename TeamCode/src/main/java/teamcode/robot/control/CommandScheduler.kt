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
            command.cancel()
        }
    }
    
    /**
     * Cancel all commands for a subsystem.
     */
    fun cancelAll(subsystem: Subsystem) {
        synchronized(this) {
            scheduledCommands[subsystem]?.cancel()
            scheduledCommands.remove(subsystem)
            
            defaultCommands[subsystem]?.let { scheduleCommand(it) }
        }
    }
    
    /**
     * Cancel all commands.
     */
    fun cancelAll() {
        synchronized(this) {
            scheduledCommands.values.forEach { it.cancel() }
            scheduledCommands.clear()
            
            defaultCommands.forEach { (_, cmd) -> scheduleCommand(cmd) }
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
            val finished = scheduledCommands.filter { (_, cmd) -> !cmd.isScheduled() }
            
            finished.forEach { (subsystem, _) ->
                subsystem.clearCommand()
                scheduledCommands.remove(subsystem)
                
                defaultCommands[subsystem]?.let { cmd ->
                    if (!cmd.isScheduled()) {
                        scheduleCommand(cmd)
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
        val requirements = command.getRequirements()
        if (requirements.isEmpty()) return
        
        command.start()
        val primarySubsystem = requirements.first()
        primarySubsystem.setCommand(command)
        scheduledCommands[primarySubsystem] = command
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


