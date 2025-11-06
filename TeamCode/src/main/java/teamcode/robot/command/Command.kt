package teamcode.robot.command

import teamcode.robot.core.state.RobotStateMachine
import teamcode.robot.core.state.RobotState
import teamcode.robot.core.subsystem.Subsystem
import kotlin.concurrent.Volatile

/**
 * Base class for all commands.
 * 
 * Design principles:
 * - Commands encapsulate discrete robot actions
 * - Commands declare subsystem requirements
 * - Lifecycle is managed by the scheduler
 * - Thread-safe execution
 * 
 * Command lifecycle:
 * 1. initialize() - called once when command starts
 * 2. execute() - called repeatedly while command runs
 * 3. isFinished() - checked after each execute()
 * 4. end() - called when command finishes (interrupted or completed)
 */
abstract class Command {
    
    /**
     * Subsystems required by this command.
     */
    private val requirements: MutableSet<Subsystem> = mutableSetOf()
    
    @Volatile
    private var subsystem: Subsystem? = null
    
    @Volatile
    private var scheduled: Boolean = false
    
    @Volatile
    private var interrupted: Boolean = false
    
    @Volatile
    private var initialized: Boolean = false
    
    /**
     * Get the subsystem this command is running on.
     */
    protected fun getSubsystem(): Subsystem? = subsystem
    
    /**
     * Check if this command is currently scheduled.
     */
    fun isScheduled(): Boolean = scheduled
    
    /**
     * Check if this command was interrupted.
     */
    fun wasInterrupted(): Boolean = interrupted
    
    /**
     * Add subsystem requirements.
     * Internal visibility allows CommandScheduler and other system components to set requirements.
     */
    internal fun addRequirements(vararg subsystems: Subsystem) {
        requirements.addAll(subsystems)
    }
    
    /**
     * Add a single subsystem requirement.
     * Internal visibility allows CommandScheduler and other system components to set requirements.
     */
    internal fun addRequirement(subsystem: Subsystem) {
        requirements.add(subsystem)
    }
    
    /**
     * Get all requirements.
     */
    fun getRequirements(): Set<Subsystem> = requirements.toSet()
    
    /**
     * Initialize the command - override for one-time setup.
     */
    protected open fun initialize() {}
    
    /**
     * Execute the command - override for command behavior.
     */
    protected abstract fun execute()
    
    /**
     * End the command - override for cleanup.
     */
    protected open fun end(interrupted: Boolean) {}
    
    /**
     * Check if command is finished - override to control duration.
     */
    protected open fun isFinished(): Boolean = false
    
    /**
     * Internal methods - managed by scheduler.
     */
    internal fun setSubsystem(subsystem: Subsystem) {
        this.subsystem = subsystem
    }
    
    internal fun start() {
        if (scheduled) return
        scheduled = true
        interrupted = false
        initialized = false
    }
    
    internal fun runExecute() {
        if (!scheduled) return
        
        if (!initialized) {
            initialize()
            initialized = true
        }
        
        if (isFinished()) {
            finish(false)
            return
        }
        
        execute()
    }
    
    internal fun finish(wasInterrupted: Boolean) {
        if (!scheduled) return
        interrupted = wasInterrupted
        scheduled = false
        end(wasInterrupted)
    }
    
    /**
     * Execute command synchronously for instant execution.
     * Internal method for executeInstant() extension function.
     */
    internal fun executeInstantInternal() {
        try {
            start()
            initialize()
            
            // Execute until finished
            while (!isFinished() && isScheduled()) {
                execute()
                Thread.sleep(10)  // Small delay to prevent tight loop
            }
            
            end(false)
        } catch (e: Exception) {
            end(true)
            throw e
        } finally {
            finish(false)
        }
    }
    
    fun cancel() {
        if (scheduled) {
            finish(true)
        }
    }
    
    internal fun interrupt() {
        cancel()
    }
    
    /**
     * Convenience methods for state machine access (read-only).
     * Commands can check state but cannot change it - state changes are controller-driven.
     */
    protected fun getState(): RobotState = RobotStateMachine.getState()
    protected fun isState(state: RobotState): Boolean = RobotStateMachine.isState(state)
    protected fun wasState(state: RobotState): Boolean = RobotStateMachine.wasState(state)
    
    override fun toString(): String {
        return "${javaClass.simpleName}(requirements=${requirements.map { it.name }})"
    }
}

