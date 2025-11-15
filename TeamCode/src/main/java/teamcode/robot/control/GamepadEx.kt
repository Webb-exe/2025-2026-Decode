package teamcode.robot.control

import com.qualcomm.robotcore.hardware.Gamepad
import kotlin.concurrent.Volatile

/**
 * Extended gamepad wrapper that provides convenient access to gamepad state.
 * Wraps the standard FTC Gamepad and adds state tracking for buttons.
 *
 * Usage:
 * ```
 * val gamepad1Ex = GamepadEx(gamepad1)
 * 
 * // Direct boolean access
 * if (gamepad1Ex.a.value) { ... }
 * if (gamepad1Ex.rightTrigger.value > 0.5) { ... }
 * 
 * // State tracking
 * if (gamepad1Ex.a.wasPressed()) { ... }
 * 
 * // Direct gamepad access
 * val rawGamepad = gamepad1Ex.getGamepad()
 * ```
 */
class GamepadEx(
    private val gamepad: Gamepad
) {
    
    // Cache button triggers to preserve state between calls
    private val _a = ButtonTrigger("a") { gamepad.a }
    private val _b = ButtonTrigger("b") { gamepad.b }
    private val _x = ButtonTrigger("x") { gamepad.x }
    private val _y = ButtonTrigger("y") { gamepad.y }
    private val _leftBumper = ButtonTrigger("leftBumper") { gamepad.left_bumper }
    private val _rightBumper = ButtonTrigger("rightBumper") { gamepad.right_bumper }
    private val _dpadUp = ButtonTrigger("dpadUp") { gamepad.dpad_up }
    private val _dpadDown = ButtonTrigger("dpadDown") { gamepad.dpad_down }
    private val _dpadLeft = ButtonTrigger("dpadLeft") { gamepad.dpad_left }
    private val _dpadRight = ButtonTrigger("dpadRight") { gamepad.dpad_right }
    private val _back = ButtonTrigger("back") { gamepad.back }
    private val _guide = ButtonTrigger("guide") { gamepad.guide }
    private val _start = ButtonTrigger("start") { gamepad.start }
    private val _leftTrigger = TriggerBinding("leftTrigger") { gamepad.left_trigger.toDouble() }
    private val _rightTrigger = TriggerBinding("rightTrigger") { gamepad.right_trigger.toDouble() }
    private val _leftStickButton = ButtonTrigger("leftStickButton") { gamepad.left_stick_button }
    private val _rightStickButton = ButtonTrigger("rightStickButton") { gamepad.right_stick_button }

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
     * Button trigger for gamepad buttons with state tracking.
     */
    inner class ButtonTrigger(
        private val buttonName: String,
        private val button: () -> Boolean
    ) {
        private val state = object {
            @Volatile
            var wasPressed = false
        }

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
         * Check if the button was just pressed (rising edge detection).
         * Returns true only on the frame when the button transitions from not pressed to pressed.
         * 
         * Example:
         * ```
         * if (gamepad1Ex.a.wasPressed()) {
         *     // Execute once per button press
         * }
         * ```
         */
        fun wasPressed(): Boolean {
            val currentState = button()
            val pressed = currentState && !state.wasPressed
            state.wasPressed = currentState
            return pressed
        }

        /**
         * Check if the button was just released (falling edge detection).
         * Returns true only on the frame when the button transitions from pressed to not pressed.
         * 
         * Example:
         * ```
         * if (gamepad1Ex.a.wasReleased()) {
         *     // Execute once per button release
         * }
         * ```
         */
        fun wasReleased(): Boolean {
            val currentState = button()
            val released = !currentState && state.wasPressed
            state.wasPressed = currentState
            return released
        }

        /**
         * Reset the internal state tracking.
         * Useful if you need to clear the pressed state.
         */
        fun resetState() {
            state.wasPressed = false
        }
    }

    /**
     * Trigger binding for gamepad analog triggers.
     */
    inner class TriggerBinding(
        private val triggerName: String,
        private val triggerValue: () -> Double
    ) {
        /**
         * Property access to the current trigger value (Double, 0.0 to 1.0).
         * 
         * Example:
         * ```
         * val speed = gamepad1Ex.rightTrigger.value
         * ```
         */
        val value: Double
            get() = triggerValue()

        /**
         * Property access to the original Gamepad object.
         * Allows access to all gamepad properties from the trigger.
         *
         * Example:
         * ```
         * val gamepad = gamepad1Ex.rightTrigger.`object`
         * if (gamepad.a) { ... }
         * ```
         */
        val `object`: Gamepad
            get() = this@GamepadEx.gamepad

        /**
         * Check if the trigger is pressed beyond a threshold.
         * 
         * @param threshold The threshold value (default 0.1)
         * @return true if trigger value > threshold
         */
        fun isPressed(threshold: Double = 0.1): Boolean {
            return triggerValue() > threshold
        }
    }

    // Button triggers - primary API
    val a: ButtonTrigger
        get() = _a

    val b: ButtonTrigger
        get() = _b

    val x: ButtonTrigger
        get() = _x

    val y: ButtonTrigger
        get() = _y

    val leftBumper: ButtonTrigger
        get() = _leftBumper

    val rightBumper: ButtonTrigger
        get() = _rightBumper

    val dpadUp: ButtonTrigger
        get() = _dpadUp

    val dpadDown: ButtonTrigger
        get() = _dpadDown

    val dpadLeft: ButtonTrigger
        get() = _dpadLeft

    val dpadRight: ButtonTrigger
        get() = _dpadRight

    val back: ButtonTrigger
        get() = _back

    val guide: ButtonTrigger
        get() = _guide

    val start: ButtonTrigger
        get() = _start

    val leftStickButton: ButtonTrigger
        get() = _leftStickButton

    val rightStickButton: ButtonTrigger
        get() = _rightStickButton

    // Trigger bindings
    val leftTrigger: TriggerBinding
        get() = _leftTrigger

    val rightTrigger: TriggerBinding
        get() = _rightTrigger

    // Direct access to analog stick values
    val leftStickX: Double
        get() = gamepad.left_stick_x.toDouble()

    val leftStickY: Double
        get() = gamepad.left_stick_y.toDouble()

    val rightStickX: Double
        get() = gamepad.right_stick_x.toDouble()

    val rightStickY: Double
        get() = gamepad.right_stick_y.toDouble()
}


