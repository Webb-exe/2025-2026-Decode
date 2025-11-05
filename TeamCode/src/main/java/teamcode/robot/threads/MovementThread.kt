package teamcode.robot.threads

import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.Gamepad
import dev.nextftc.hardware.powerable.SetPower
import teamcode.robot.core.RobotConfig
import teamcode.robot.core.RobotHardware
import teamcode.threading.RobotThread
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.withSign

/**
 * Thread for controlling mecanum drive movement.
 * Handles gamepad input and applies mecanum kinematics to control the robot's movement.
 */
class MovementThread : RobotThread("MovementThread", 5) {
    // Movement control variables
    @Volatile
    private var driveY = 0.0 // Forward/backward

    @Volatile
    private var driveX = 0.0 // Strafe left/right

    @Volatile
    private var driveRot = 0.0 // Rotation

    @Volatile
    private var speedMultiplier = 1.0

    /**
     * Check if drive control is enabled
     */
    @Volatile
    var isEnabled: Boolean = true
        private set

    /**
     * Check if field-centric mode is enabled
     */
    /**
     * Enable field-centric drive mode
     * Requires IMU heading to be updated via setRobotHeading()
     */
    // Field-centric mode
    @Volatile
    var isFieldCentric: Boolean = false

    @Volatile
    private var robotHeading = 0.0 // In radians

    override fun onStart() {
        // Initialize drive motors to brake mode (optional)
        RobotHardware.leftFront.zeroPowerBehavior = DcMotor.ZeroPowerBehavior.BRAKE
        RobotHardware.leftBack.zeroPowerBehavior = DcMotor.ZeroPowerBehavior.BRAKE
        RobotHardware.rightFront.zeroPowerBehavior = DcMotor.ZeroPowerBehavior.BRAKE
        RobotHardware.rightBack.zeroPowerBehavior = DcMotor.ZeroPowerBehavior.BRAKE

    }

    override fun runLoop() {
        if (!this.isEnabled) {
            // Update telemetry even when disabled
            telemetry.addData("Status", "Disabled")
            telemetry.addData("Enabled", false)
            return
        }


        // Get current drive values (thread-safe with volatile)
        var y = driveY
        var x = driveX
        val rot = driveRot


        // Apply field-centric transformation if enabled
        if (this.isFieldCentric) {
            val heading = robotHeading
            val temp = y * cos(-heading) - x * sin(-heading)
            x = y * sin(-heading) + x * cos(-heading)
            y = temp
        }


        // Scaled Mecanum kinematics with theta+turn scaling
        val turn = rot * RobotConfig.TurnScale

        val theta = atan2(y, x)
        val power = hypot(x, y) * RobotConfig.PowerScale

        val sin = sin(theta - Math.PI / 4)
        val cos = cos(theta - Math.PI / 4)
        val max = max(abs(sin), abs(cos))

        var leftFrontPower = power * cos / max + turn
        var rightFrontPower = power * sin / max - turn
        var leftBackPower = power * sin / max + turn
        var rightBackPower = power * cos / max - turn

        // Normalize speeds if total demand is over 1.0
        if ((power + abs(turn)) > 1) {
            leftFrontPower /= (power + abs(turn))
            rightFrontPower /= (power + abs(turn))
            leftBackPower /= (power + abs(turn))
            rightBackPower /= (power + abs(turn))
        }


        // Apply speed multiplier
        leftFrontPower *= speedMultiplier
        rightFrontPower *= speedMultiplier
        leftBackPower *= speedMultiplier
        rightBackPower *= speedMultiplier


        // Set motor powers
        SetPower(RobotHardware.leftFront, leftFrontPower)
        RobotHardware.rightFront.power = rightFrontPower
        RobotHardware.leftBack.power = leftBackPower
        RobotHardware.rightBack.power = rightBackPower


        // Update telemetry - data persists until next update from this thread
        telemetry.addData("Status", "Running")
        telemetry.addData("Enabled", true)
        telemetry.addData("Speed Multiplier", String.format("%.1f%%", speedMultiplier * 100))
        telemetry.addData("Field Centric", this.isFieldCentric)
        telemetry.addData("Drive Y", String.format("%.2f", driveY))
        telemetry.addData("Drive X", String.format("%.2f", driveX))
        telemetry.addData("Drive Rot", String.format("%.2f", driveRot))
        telemetry.addData("LF Power", String.format("%.2f", leftFrontPower))
        telemetry.addData("RF Power", String.format("%.2f", rightFrontPower))
        telemetry.addData("LB Power", String.format("%.2f", leftBackPower))
        telemetry.addData("RB Power", String.format("%.2f", rightBackPower))
    }

    /**
     * Update drive values from gamepad input
     * Call this from your main OpMode thread
     */
    fun setDriveInput(gamepad: Gamepad?) {
        if (gamepad == null) return


        // Standard gamepad controls:
        // Left stick Y = forward/backward
        // Left stick X = strafe left/right
        // Right stick X = rotation
        this.setDriveValues(
            -gamepad.left_stick_y.toDouble(),
            gamepad.left_stick_x.toDouble(),
            gamepad.right_stick_x.toDouble()
        )
    }

    /**
     * Manually set drive values (for autonomous or custom control)
     * Applies cubic-style scaling for smoother control, but keeps sign and zero checks clear.
     * Uses the same power function for all axes and short-circuits for zeros.
     */
    fun setDriveValues(y: Double, x: Double, rotation: Double) {
        this.driveY = scaleInput(y, 1.8, 1.8)
        this.driveX = scaleInput(x, 1.8, 1.8)
        this.driveRot = scaleInput(rotation, 2.0, 1.2)
    }

    /**
     * Scales joystick input while preserving the sign.
     * For zero input, returns zero directly.
     */
    private fun scaleInput(input: Double, exponent: Double, linear: Double): Double {
        if (input == 0.0) return 0.0
        return abs(input).pow(exponent).withSign(input) * linear
    }

    /**
     * Set speed multiplier (0.0 to 1.0)
     * Useful for precision mode vs speed mode
     */
    fun setSpeedMultiplier(multiplier: Double) {
        this.speedMultiplier = max(0.0, min(1.0, multiplier))
    }

    /**
     * Get current speed multiplier
     */
    fun getSpeedMultiplier(): Double {
        return speedMultiplier
    }

    /**
     * Update robot heading for field-centric drive
     * @param heading Robot heading in radians
     */
    fun setRobotHeading(heading: Double) {
        this.robotHeading = heading
    }

    /**
     * Enable drive control
     */
    fun enable() {
        this.isEnabled = true
    }

    /**
     * Disable drive control (stops all motors)
     */
    fun disable() {
        this.isEnabled = false
        stopMotors()
    }

    /**
     * Stop all drive motors
     */
    private fun stopMotors() {
        if (RobotHardware.leftFront != null) {
            RobotHardware.leftFront.power = 0.0
            RobotHardware.rightFront.power = 0.0
            RobotHardware.leftBack.power = 0.0
            RobotHardware.rightBack.power = 0.0
        }
    }

    override fun onStop() {
        stopMotors()
    }
}
