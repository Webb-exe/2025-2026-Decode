package teamcode.robot.control

import teamcode.robot.command.Command
import kotlin.concurrent.Volatile
import java.lang.ref.WeakReference

/**
 * Auto-binding that automatically schedules commands based on conditions.
 * 
 * Used for trigger-based and conditional command scheduling.
 * Eliminates the need for manual scheduling in main loops.
 */
class AutoBinding(
    private val condition: () -> Boolean,
    private val commandFactory: () -> Command,
    private val cancelOnConditionFalse: Boolean = true
) {
    @Volatile
    private var currentCommand: Command? = null
    
    @Volatile
    private var conditionWasTrue: Boolean = false
    
    /**
     * Callbacks for lifecycle events.
     * Using weak references to prevent memory leaks.
     */
    private val onRemovedCallbacks = mutableListOf<WeakReference<() -> Unit>>()
    
    /**
     * Register a callback to be called when this binding is removed.
     */
    internal fun onRemoved(callback: () -> Unit) {
        onRemovedCallbacks.add(WeakReference(callback))
    }
    
    /**
     * Notify all registered callbacks that this binding is being removed.
     */
    private fun notifyRemoved() {
        onRemovedCallbacks.removeAll { it.get() == null }
        onRemovedCallbacks.forEach { it.get()?.invoke() }
        onRemovedCallbacks.clear()
    }
    
    /**
     * Update the binding - called by scheduler.
     */
    internal fun update() {
        val conditionTrue = condition()
        
        when {
            // Condition just became true
            conditionTrue && !conditionWasTrue -> {
                if (currentCommand == null || !currentCommand!!.isScheduled()) {
                    currentCommand = commandFactory()
                    CommandScheduler.getInstance().schedule(currentCommand!!)
                    conditionWasTrue = true
                }
            }
            
            // Condition is false and we should cancel
            !conditionTrue && conditionWasTrue && cancelOnConditionFalse -> {
                currentCommand?.let { cmd ->
                    if (cmd.isScheduled()) {
                        CommandScheduler.getInstance().cancel(cmd)
                        currentCommand = null
                    }
                }
                conditionWasTrue = false
            }
            
            // Condition is false but command finished naturally
            !conditionTrue && conditionWasTrue && !cancelOnConditionFalse -> {
                currentCommand?.let { cmd ->
                    if (!cmd.isScheduled()) {
                        currentCommand = null
                        conditionWasTrue = false
                    }
                }
            }
            
            // Condition still true, check if command finished
            conditionTrue && conditionWasTrue && currentCommand != null -> {
                if (!currentCommand!!.isScheduled()) {
                    currentCommand = null
                    conditionWasTrue = false
                }
            }
        }
    }
    
    /**
     * Cancel the current command.
     */
    fun cancel() {
        currentCommand?.let { cmd ->
            if (cmd.isScheduled()) {
                CommandScheduler.getInstance().cancel(cmd)
            }
        }
        currentCommand = null
        conditionWasTrue = false
    }
    
    /**
     * Unbind this binding - removes it from the global manager and notifies callbacks.
     */
    fun unbind() {
        cancel()
        BindingManager.unregister(this)
        notifyRemoved()
    }
    
    /**
     * Check if binding is active.
     */
    fun isActive(): Boolean = currentCommand?.isScheduled() == true
    
    /**
     * Check if binding is registered with the scheduler.
     */
    fun isRegistered(): Boolean {
        return CommandScheduler.getInstance().isBindingRegistered(this)
    }
}


