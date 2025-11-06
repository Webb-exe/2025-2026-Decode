package teamcode.robot.control

import com.qualcomm.robotcore.hardware.Gamepad
import teamcode.robot.command.Command
import kotlin.concurrent.Volatile

/**
 * Extended gamepad wrapper that provides binding functionality.
 * Wraps the standard FTC Gamepad and adds command binding capabilities.
 * 
 * Usage:
 * ```
 * gamepad1Ex.rightTrigger.whileHeld().bind { ShootCommand() }
 * gamepad1Ex.a.whenPressed().bind { JumpCommand() }
 * ```
 */
class GamepadEx(
    private val gamepad: Gamepad
) {
    private val buttonBindingMap = mutableMapOf<String, AutoBinding?>()
    
    /**
     * Get the underlying gamepad object for direct access to raw properties.
     * Use this when you need to access Boolean button states or other raw gamepad properties.
     * 
     * Example:
     * ```
     * if (gamepad1Ex.getGamepad().a) { ... }
     * val triggerValue = gamepad1Ex.getGamepad().right_trigger
     * ```
     */
    fun getGamepad(): Gamepad = gamepad
    
    /**
     * Property access to the underlying gamepad value.
     * Shorthand for accessing the wrapped Gamepad object.
     * 
     * Example:
     * ```
     * if (gamepad1Ex.value.a) { ... }
     * val triggerValue = gamepad1Ex.value.right_trigger
     * ```
     */
    val value: Gamepad
        get() = gamepad
    
    /**
     * Property access to the original Gamepad object.
     * Returns the same underlying gamepad as `.value` but with a more explicit name.
     * 
     * Example:
     * ```
     * if (gamepad1Ex.`object`.a) { ... }
     * val triggerValue = gamepad1Ex.`object`.right_trigger
     * ```
     */
    val `object`: Gamepad
        get() = gamepad
    
    /**
     * Get button ID for tracking.
     */
    private fun getButtonId(button: String, action: String): String {
        return "${gamepad.hashCode()}_${button}_$action"
    }
    
    /**
     * Create and register a binding for a button.
     * Returns the binding if created, null if already exists and overwrite=false.
     */
    private fun createBinding(
        binding: AutoBinding, 
        buttonId: String, 
        overwrite: Boolean = true
    ): AutoBinding? {
        val existingBinding = buttonBindingMap[buttonId]
        
        if (existingBinding != null) {
            if (overwrite) {
                // Remove old binding
                existingBinding.unbind()
                buttonBindingMap[buttonId] = binding
                BindingManager.register(binding)
                return binding
            } else {
                // Don't create new binding if one already exists
                return null
            }
        } else {
            // No existing binding, create new one
            buttonBindingMap[buttonId] = binding
            BindingManager.register(binding)
            return binding
        }
    }
    
    /**
     * Remove all bindings for this gamepad.
     */
    fun unbindAll() {
        val bindings = buttonBindingMap.values.filterNotNull()
        BindingManager.unbindAll(bindings)
        buttonBindingMap.clear()
    }
    
    /**
     * Get all bindings for this gamepad.
     */
    fun getBindings(): List<AutoBinding> {
        // Clean up removed bindings
        buttonBindingMap.entries.removeAll { it.value?.isRegistered() != true }
        return buttonBindingMap.values.filterNotNull()
    }
    
    /**
     * Button trigger for gamepad buttons.
     */
    inner class ButtonTrigger(
        private val buttonName: String,
        private val button: () -> Boolean
    ) {
        private val state = object { @Volatile var wasPressed = false }
        
        /**
         * Get the current button state (Boolean).
         */
        fun getState(): Boolean = button()
        
        /**
         * Property access to the current button value (Boolean).
         * Shorthand for getting the button state.
         * 
         * Example:
         * ```
         * if (gamepad1Ex.a.value) { ... }
         * ```
         */
        val value: Boolean
            get() = button()
        
        /**
         * Property access to the original Gamepad object.
         * Allows access to all gamepad properties from the button trigger.
         * 
         * Example:
         * ```
         * val gamepad = gamepad1Ex.a.`object`
         * if (gamepad.a) { ... }
         * ```
         */
        val `object`: Gamepad
            get() = this@GamepadEx.gamepad
        
        /**
         * Create a bindable for whenPressed action.
         */
        fun whenPressed(overwrite: Boolean = true): Bindable {
            return Bindable(
                gamepadEx = this@GamepadEx,
                buttonName = buttonName,
                actionName = "whenPressed",
                condition = {
                    val isPressed = button()
                    val pressed = isPressed && !state.wasPressed
                    state.wasPressed = isPressed
                    pressed
                },
                overwrite = overwrite
            )
        }
        
        /**
         * Create a bindable for whileHeld action.
         */
        fun whileHeld(overwrite: Boolean = true): Bindable {
            return Bindable(
                gamepadEx = this@GamepadEx,
                buttonName = buttonName,
                actionName = "whileHeld",
                condition = { button() },
                overwrite = overwrite
            )
        }
        
        /**
         * Create a bindable for whenReleased action.
         */
        fun whenReleased(overwrite: Boolean = true): Bindable {
            return Bindable(
                gamepadEx = this@GamepadEx,
                buttonName = buttonName,
                actionName = "whenReleased",
                condition = {
                    val isPressed = button()
                    val released = !isPressed && state.wasPressed
                    state.wasPressed = isPressed
                    released
                },
                overwrite = overwrite
            )
        }
    }
    
    /**
     * Trigger binding for gamepad triggers.
     */
    inner class TriggerBinding(
        private val triggerName: String,
        private val triggerValue: () -> Double,
        private val threshold: Double = 0.1
    ) {

        /**
         * Property access to the current button value (Boolean).
         * Shorthand for getting the button state.
         *
         * Example:
         * ```
         * if (gamepad1Ex.a.value) { ... }
         * ```
         */
        val value
            get() = triggerValue()

        /**
         * Property access to the original Gamepad object.
         * Allows access to all gamepad properties from the button trigger.
         *
         * Example:
         * ```
         * val gamepad = gamepad1Ex.a.`object`
         * if (gamepad.a) { ... }
         * ```
         */
        val `object`: Gamepad
            get() = this@GamepadEx.gamepad

        /**
         * Create a bindable for whileHeld action.
         */
        fun whileHeld(overwrite: Boolean = true): Bindable {
            return Bindable(
                gamepadEx = this@GamepadEx,
                buttonName = triggerName,
                actionName = "whileHeld",
                condition = { triggerValue() > threshold },
                overwrite = overwrite
            )
        }
    }
    
    /**
     * Bindable object that can create a binding.
     */
    class Bindable(
        private val gamepadEx: GamepadEx,
        private val buttonName: String,
        private val actionName: String,
        private val condition: () -> Boolean,
        private val overwrite: Boolean = true
    ) {
        /**
         * Create and register the binding.
         * Returns the AutoBinding object if created, null if binding already exists and overwrite=false.
         * The binding is automatically registered in the global BindingManager.
         */
        fun bind(commandFactory: () -> Command): AutoBinding? {
            val binding = AutoBinding(
                condition = condition,
                commandFactory = commandFactory,
                cancelOnConditionFalse = actionName == "whileHeld"
            )
            
            val buttonId = gamepadEx.getButtonId(buttonName, actionName)
            return gamepadEx.createBinding(binding, buttonId, overwrite)
        }
    }
    
    // Button triggers - primary API for bindings
    val a: ButtonTrigger
        get() = ButtonTrigger("a") { gamepad.a }
    
    val b: ButtonTrigger
        get() = ButtonTrigger("b") { gamepad.b }
    
    val x: ButtonTrigger
        get() = ButtonTrigger("x") { gamepad.x }
    
    val y: ButtonTrigger
        get() = ButtonTrigger("y") { gamepad.y }
    
    val leftBumper: ButtonTrigger
        get() = ButtonTrigger("leftBumper") { gamepad.left_bumper }
    
    val rightBumper: ButtonTrigger
        get() = ButtonTrigger("rightBumper") { gamepad.right_bumper }
    
    val dpadUp: ButtonTrigger
        get() = ButtonTrigger("dpadUp") { gamepad.dpad_up }
    
    val dpadDown: ButtonTrigger
        get() = ButtonTrigger("dpadDown") { gamepad.dpad_down }
    
    val dpadLeft: ButtonTrigger
        get() = ButtonTrigger("dpadLeft") { gamepad.dpad_left }
    
    val dpadRight: ButtonTrigger
        get() = ButtonTrigger("dpadRight") { gamepad.dpad_right }
    
    val back: ButtonTrigger
        get() = ButtonTrigger("back") { gamepad.back }
    
    val guide: ButtonTrigger
        get() = ButtonTrigger("guide") { gamepad.guide }
    
    val start: ButtonTrigger
        get() = ButtonTrigger("start") { gamepad.start }
    
    // Trigger bindings
    val leftTrigger: TriggerBinding
        get() = TriggerBinding("leftTrigger", { gamepad.left_trigger.toDouble() })
    
    val rightTrigger: TriggerBinding
        get() = TriggerBinding("rightTrigger", { gamepad.right_trigger.toDouble() })
}

