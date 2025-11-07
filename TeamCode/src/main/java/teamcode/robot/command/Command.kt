package teamcode.robot.command

import teamcode.robot.core.state.RobotStateMachine
import teamcode.robot.core.state.RobotState
import teamcode.robot.core.subsystem.Subsystem
import teamcode.threading.RobotThread
import kotlin.concurrent.Volatile
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

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
abstract class Command(
    /**
     * Whether this command can be interrupted by other commands.
     * If true, this command cannot be canceled or interrupted by conflicting commands.
     */
    val nonInterruptible: Boolean = false
) {
    
    /**
     * Subsystems required by this command.
     */
    private val requirements: MutableSet<Subsystem> = mutableSetOf()
    
    /**
     * Map of dependency names to Subsystem instances for easy access.
     * Automatically populated from constructor parameters.
     */
    private val dependencies: MutableMap<String, Subsystem> = mutableMapOf()
    
    @Volatile
    private var subsystem: Subsystem? = null
    
    @Volatile
    private var scheduled: Boolean = false
    
    @Volatile
    private var interrupted: Boolean = false
    
    @Volatile
    private var initialized: Boolean = false
    
    init {
        // Automatically detect and register Subsystem dependencies from constructor parameters
        // This runs after the subclass constructor completes, so all properties are initialized
        autoDetectDependencies()
    }
    
    /**
     * Manually register dependencies (optional - dependencies are auto-detected from constructor).
     * This is only needed if auto-detection fails or you want explicit control.
     */
    protected fun requires(vararg subsystems: Subsystem) {
        subsystems.forEach { subsystem ->
            addRequirement(subsystem)
            // Try to find the property name using reflection
            try {
                val kClass = this::class
                kClass.memberProperties.forEach { prop ->
                    prop.isAccessible = true
                    if (prop.call(this) == subsystem) {
                        dependencies[prop.name] = subsystem
                    }
                }
            } catch (e: Exception) {
                // Reflection failed, use class name as fallback
                dependencies[subsystem.javaClass.simpleName] = subsystem
            }
        }
    }
    
    /**
     * Get a dependency by property name. 
     * Example: val shooter = getDependency<ShooterSubsystem>("shooterSubsystem")
     */
    protected fun <T : Subsystem> getDependency(name: String): T? {
        @Suppress("UNCHECKED_CAST")
        return dependencies[name] as? T
    }
    
    /**
     * Automatically detect and register Subsystem dependencies from constructor parameters
     * and properties that might be accessed via RobotThread.current<T>().
     * This is called automatically in the Command constructor after the subclass is fully constructed.
     * 
     * Dependencies can be:
     * 1. Constructor parameters (automatically detected)
     * 2. Properties accessed via RobotThread.current<T>() inside the class
     * 
     * Example:
     * ```
     * class MyCommand(
     *     private val shooter: ShooterSubsystem  // Auto-detected from constructor
     * ) : Command() {
     *     // Properties accessed via RobotThread.current are also detected
     *     private val turret = RobotThread.current<TurretSubsystem>()
     * }
     * ```
     */
    private fun autoDetectDependencies() {
        try {
            val kClass = this::class
            val subsystemType = Subsystem::class.starProjectedType
            
            // Find all properties that are Subsystem instances (from constructor parameters or init blocks)
            kClass.memberProperties.forEach { prop ->
                if (prop.returnType.isSubtypeOf(subsystemType)) {
                    try {
                        prop.isAccessible = true
                        val subsystem = prop.call(this) as? Subsystem
                        if (subsystem != null && !requirements.contains(subsystem)) {
                            dependencies[prop.name] = subsystem
                            addRequirement(subsystem)
                        }
                    } catch (e: Exception) {
                        // Property might not be accessible or not initialized yet
                        // Try to get it via RobotThread.current if it's a Subsystem type
                        try {
                            val propertyType = prop.returnType.jvmErasure.java
                            if (Subsystem::class.java.isAssignableFrom(propertyType)) {
                                val subsystem = RobotThread.currentByClass(propertyType as Class<out RobotThread>) as? Subsystem
                                if (subsystem != null && !requirements.contains(subsystem)) {
                                    dependencies[prop.name] = subsystem
                                    addRequirement(subsystem)
                                }
                            }
                        } catch (e2: Exception) {
                            // RobotThread.current failed, subsystem not available yet or not a Subsystem
                            // Skip this property
                        }
                    }
                }
            }
            
            // Also check for any Subsystem types that might be accessed via RobotThread.current
            // by scanning all registered RobotThread instances
            // We check if any property types match registered subsystems
            try {
                // Get all registered RobotThread instances via reflection
                val companionClass = RobotThread::class.java.getDeclaredClasses()
                    .find { it.simpleName == "Companion" }
                
                companionClass?.let { companion ->
                    val globalsField = companion.getDeclaredField("globals")
                    globalsField.isAccessible = true
                    val globals = globalsField.get(null) as? java.util.Map<*, *>
                    
                    globals?.values()?.forEach { thread ->
                        if (thread is Subsystem && !requirements.contains(thread)) {
                            // Check if this subsystem type matches any property type in the command
                            val subsystemClass = thread.javaClass
                            val matchingProperty = kClass.memberProperties.find { prop ->
                                try {
                                    val propType = prop.returnType.jvmErasure.java
                                    // Check if property type matches subsystem class
                                    propType.isAssignableFrom(subsystemClass) || 
                                    subsystemClass.isAssignableFrom(propType) ||
                                    propType == subsystemClass
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            
                            if (matchingProperty != null) {
                                dependencies[matchingProperty.name] = thread
                                addRequirement(thread)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Reflection to access RobotThread globals failed, skip this check
                // This is expected if subsystems aren't registered yet or reflection fails
            }
        } catch (e: Exception) {
            // Reflection failed, dependencies will need to be registered manually via requires()
        }
    }
    
    /**
     * Internal method for CommandScheduler to re-detect dependencies if needed.
     */
    internal fun reDetectDependencies() {
        autoDetectDependencies()
    }
    
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
        if (scheduled && !nonInterruptible) {
            finish(true)
        }
    }
    
    internal fun interrupt() {
        if (!nonInterruptible) {
            cancel()
        }
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

