package teamcode.robot.control

import java.lang.ref.WeakReference

/**
 * Container for managing multiple bindings as a group.
 * 
 * Allows you to unbind all bindings in a group at once.
 * Automatically tracks binding lifecycle - if a binding is removed elsewhere,
 * it's automatically removed from the group.
 * 
 * Usage:
 * ```
 * val group = BindingGroup(
 *     gamepad1.a.whenPressed { JumpCommand() },
 *     gamepad1.b.whenPressed { ShootCommand() }
 * )
 * 
 * // Later, unbind all at once
 * group.unbindAll()
 * ```
 */
class BindingGroup(
    vararg bindings: AutoBinding
) {
    /**
     * Weak references to bindings to allow garbage collection
     * and automatic cleanup when bindings are removed elsewhere.
     */
    private val bindingRefs = mutableListOf<WeakReference<AutoBinding>>()
    
    init {
        bindings.forEach { binding ->
            addBinding(binding)
        }
    }
    
    /**
     * Add a binding to this group.
     */
    fun addBinding(binding: AutoBinding) {
        val ref = WeakReference(binding)
        bindingRefs.add(ref)
        
        // Register callback to remove from group when binding is removed
        binding.onRemoved {
            removeBindingRef(ref)
        }
    }
    
    /**
     * Remove a binding reference from the group.
     */
    private fun removeBindingRef(ref: WeakReference<AutoBinding>) {
        bindingRefs.remove(ref)
    }
    
    /**
     * Get all active bindings in this group.
     * Filters out null references (bindings that were garbage collected).
     */
    fun getActiveBindings(): List<AutoBinding> {
        bindingRefs.removeAll { it.get() == null }
        return bindingRefs.mapNotNull { it.get() }
    }
    
    /**
     * Unbind all bindings in this group.
     */
    fun unbindAll() {
        val active = getActiveBindings()
        active.forEach { binding ->
            binding.unbind()
        }
        bindingRefs.clear()
    }
    
    /**
     * Check if the group has any active bindings.
     */
    fun hasActiveBindings(): Boolean {
        bindingRefs.removeAll { it.get() == null }
        return bindingRefs.isNotEmpty()
    }
    
    /**
     * Get the number of active bindings in this group.
     */
    fun size(): Int {
        bindingRefs.removeAll { it.get() == null }
        return bindingRefs.size
    }
}

