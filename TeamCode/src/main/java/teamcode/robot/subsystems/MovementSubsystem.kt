package teamcode.robot.subsystems

import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.Gamepad
import dev.nextftc.hardware.powerable.SetPower
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.subsystem.Subsystem
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

object MovementConfig{
    @JvmField
    var TurnScale: Double = 0.5
    @JvmField
    var PowerScale: Double = 0.5
}

/**
 * Movement subsystem for mecanum drive.
 * Runs continuously without commands.
 */
class MovementSubsystem : Subsystem("Movement", 5) {
    
    @Volatile
    private var driveY = 0.0
    
    @Volatile
    private var driveX = 0.0
    
    @Volatile
    private var driveRot = 0.0
    
    @Volatile
    private var speedMultiplier = 1.0
    
    @Volatile
    var isEnabled: Boolean = true
        private set
    
    @Volatile
    var isFieldCentric: Boolean = false
    
    @Volatile
    private var robotHeading = 0.0
    
    fun setDriveInput(gamepad: Gamepad) {
        setDriveValues(
            gamepad.left_stick_y.toDouble(),
            gamepad.left_stick_x.toDouble(),
            gamepad.right_stick_x.toDouble()
        )
    }
    
    fun setDriveValues(y: Double, x: Double, rotation: Double) {
        driveY = scaleInput(y, 1.8, 1.8)
        driveX = scaleInput(x, 1.8, 1.8)
        driveRot = scaleInput(rotation, 2.0, 1.2)
    }
    
    private fun scaleInput(input: Double, exponent: Double, linear: Double): Double {
        if (input == 0.0) return 0.0
        return abs(input).pow(exponent).withSign(input) * linear
    }
    
    fun setSpeedMultiplier(multiplier: Double) {
        speedMultiplier = max(0.0, min(1.0, multiplier))
    }
    
    fun getSpeedMultiplier(): Double = speedMultiplier
    
    fun setRobotHeading(heading: Double) {
        robotHeading = heading
    }
    
    fun enable() {
        isEnabled = true
    }
    
    fun disable() {
        isEnabled = false
        stopMotors()
    }
    
    private fun stopMotors() {
        RobotHardware.leftFront.set(0.0)
        RobotHardware.rightFront.set(0.0)
        RobotHardware.leftBack.set(0.0)
        RobotHardware.rightBack.set(0.0)
    }
    
    override fun init() {

    }
    
    override fun periodic() {
        if (!isEnabled) return
        
        var y = driveY
        var x = driveX
        val rot = driveRot
        
        if (isFieldCentric) {
            val heading = robotHeading
            val temp = y * cos(-heading) - x * sin(-heading)
            x = y * sin(-heading) + x * cos(-heading)
            y = temp
        }
        
        val turn = rot * MovementConfig.TurnScale
        val theta = atan2(y, x)
        val power = hypot(x, y) * MovementConfig.PowerScale
        
        val sin = sin(theta - Math.PI / 4)
        val cos = cos(theta - Math.PI / 4)
        val max = max(abs(sin), abs(cos))
        
        var lf = power * cos / max + turn
        var rf = power * sin / max - turn
        var lb = power * sin / max + turn
        var rb = power * cos / max - turn
        
        if ((power + abs(turn)) > 1) {
            lf /= (power + abs(turn))
            rf /= (power + abs(turn))
            lb /= (power + abs(turn))
            rb /= (power + abs(turn))
        }
        
        lf *= speedMultiplier
        rf *= speedMultiplier
        lb *= speedMultiplier
        rb *= speedMultiplier
        
        RobotHardware.leftFront.set(lf)
        RobotHardware.rightFront.set(rf)
        RobotHardware.leftBack.set(lb)
        RobotHardware.rightBack.set(rb)
    }
    
    override fun end() {
        stopMotors()
    }
    
    override fun updateTelemetry() {
        super.updateTelemetry()
        telemetry.addData("Enabled", isEnabled)
        telemetry.addData("Speed", String.format("%.1f%%", speedMultiplier * 100))
        telemetry.addData("Field Centric", isFieldCentric)
    }
}
