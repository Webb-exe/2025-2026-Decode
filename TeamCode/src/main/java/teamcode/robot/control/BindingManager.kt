package teamcode.robot.control

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Global manager for all bindings.
 * Tracks all bindings across all gamepads and provides methods to manage them.
 */
object BindingManager {
    private val allBindings = ConcurrentHashMap.newKeySet<AutoBinding>()
    
    /**
     * Register a binding globally.
     * Called automatically when a binding is created.
     */
    internal fun register(binding: AutoBinding) {
        allBindings.add(binding)
        // Also register with CommandScheduler
        CommandScheduler.getInstance().registerBinding(binding)
    }
    
    /**
     * Unregister a binding globally.
     * Called automatically when a binding is removed.
     */
    internal fun unregister(binding: AutoBinding) {
        allBindings.remove(binding)
        // Also unregister from CommandScheduler
        CommandScheduler.getInstance().unregisterBinding(binding)
    }
    
    /**
     * Unbind all bindings in a list.
     */
    fun unbindAll(bindings: List<AutoBinding?>) {
        bindings.filterNotNull().forEach { it.unbind() }
    }
    
    /**
     * Unbind all bindings globally.
     */
    fun unbindAll() {
        val bindings = allBindings.toList()
        bindings.forEach { it.unbind() }
    }
    
    /**
     * Get all active bindings.
     */
    fun getAllBindings(): List<AutoBinding> {
        // Clean up removed bindings
        allBindings.removeAll { !it.isRegistered() }
        return allBindings.toList()
    }
    
    /**
     * Get count of active bindings.
     */
    fun getBindingCount(): Int {
        allBindings.removeAll { !it.isRegistered() }
        return allBindings.size
    }
}

