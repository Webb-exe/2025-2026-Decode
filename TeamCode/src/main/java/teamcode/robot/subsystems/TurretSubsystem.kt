package teamcode.robot.subsystems

import com.bylazar.configurables.annotations.Configurable
import teamcode.robot.core.PID
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.subsystem.Subsystem
import teamcode.threading.RobotThread
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.pow

//@Configurable
//object TurretConfig {
//    @JvmField
//    var kP: Double = 4.0
//    @JvmField
//    var kI: Double = 0.0
//    @JvmField
//    var kD: Double = 0.1
//    @JvmField
//    var SpeedClamp: Double = 0.5
//    @JvmField
//    var ScaleFactor: Double = 0.01
//    @JvmField
//    var ManualTurnSpeed: Double = 0.4
//}

@Configurable
object TurretConfig {
    @JvmField
    var motorGear: Int = 1
    @JvmField
    var mainGear: Int =1;
    @JvmField
    var servoPosToDegree:Int = 60;
    @JvmField
    var maxMove: Double = 120.0
    @JvmField
    var kP: Double = 0.06  // Proportional gain
    @JvmField
    var kI: Double = 0.0   // Integral gain
    @JvmField
    var kD: Double = 0.001 // Derivative gain
}

enum class TurretState {
    AUTO,
    MANUAL,
}

/**
 * Turret subsystem for target tracking.
 * Uses vision data to maintain target alignment via PID control.
 */
class TurretSubsystem : Subsystem("Turret", 10) {
    private lateinit var vision: VisionSubsystem
    private val pid = PID(TurretConfig.kP, TurretConfig.kI, TurretConfig.kD)
    
    @Volatile
    final var isEnabled: Boolean = true
        private set

    @Volatile
    final var currentState: TurretState = TurretState.AUTO
        private set

    final var targetAngle: Double = 0.0
        private set
    
    override fun init() {
        // Wait for VisionSubsystem to be available (threads start concurrently)
        vision = current<VisionSubsystem>(10)
        
        // Configure PID
        pid.setpoint = 0.0  // Target is centered (0 degrees error)
        pid.setOutputRange(-1.0, 1.0)  // Limit correction speed
    }

    /**
     * Converts a desired mainGear degree to corresponding servo position.
     * Uses TurretConfig.servoPosToDegree, TurretConfig.motorGear, and TurretConfig.mainGear.
     * Maps degrees from [-maxMove/2, maxMove/2] to servo range [0, 1].
     * 
     * @param degree The desired angle in mainGear degrees (range: -maxMove/2 to maxMove/2).
     * @return The calculated servo position (range: 0.0 to 1.0).
     */
    fun convertDegreesToPos(degree: Double): Double {
        // Each output (mainGear) degree needs (motorGear/mainGear) ratio, then convert to servo position
        val gearRatio = TurretConfig.motorGear.toDouble() / TurretConfig.mainGear.toDouble()
        val motorDegrees = degree * gearRatio
        // Convert motor degrees to servo position, inverted so positive angles → 0, negative angles → 1
        val pos = 0.5 - (motorDegrees / TurretConfig.servoPosToDegree)
        return pos.coerceIn(0.0, 1.0)
    }

    
    override fun periodic() {
        if (!isEnabled) return

        if (currentState == TurretState.AUTO) {
            if (!vision.hasTargets()){
                return
            }

            var result = vision.targetX
        
        
        if (!vision.hasTargets()) {
            RobotHardware.turretTurnMotor.set(0.0)
            return
        }

        
        // Update PID gains from config (allows tuning without restart)
        pid.setGains(TurretConfig.kP, TurretConfig.kI, TurretConfig.kD)
        
        // PID controller: measurement is vision error, setpoint is 0 (centered)
        val correction = pid.calculate(result)

        
        // Apply correction to target angle
        targetAngle -= correction
        targetAngle = targetAngle.coerceIn(-TurretConfig.maxMove/2, TurretConfig.maxMove/2)
        }


        val servoPos = convertDegreesToPos(targetAngle)
        RobotHardware.turretTurnServo.set(servoPos)
    }
    
    fun enable() {
        isEnabled = true
        pid.reset()  // Reset PID state when re-enabling
    }
    
    fun disable() {
        isEnabled = false
        RobotHardware.turretTurnMotor.set(0.0)
        pid.reset()  // Reset PID state to prevent integral windup
    }

    fun enterAutoMode() {
        currentState = TurretState.AUTO
    }

    fun enterManualMode() {
        currentState = TurretState.MANUAL
    }

    fun triggerStates(){
        when (currentState) {
            TurretState.AUTO -> {
                enterManualMode()
            }
            TurretState.MANUAL -> {
                enterAutoMode()
            }
        }
    }
    
    override fun end() {
        disable()
    }
    
    override fun updateTelemetry() {
        super.updateTelemetry()
        telemetry.addData("Enabled", isEnabled)
        telemetry.addData("State", currentState)
        telemetry.addData("Target Angle", "%.2f°".format(targetAngle))
        telemetry.addData("Servo Pos", "%.3f".format(RobotHardware.turretTurnServo.get()))
        
        if (isEnabled && vision.hasTargets()) {
            val tag = vision.getAprilTag(24)
            if (tag != null) {
                telemetry.addData("Status", "Tracking")
                telemetry.addData("Target ID", tag.id)
                telemetry.addData("Vision Error", "%.2f°".format(tag.xDegrees))
                telemetry.addData("PID Error", "%.3f".format(pid.error))
                telemetry.addData("PID P", "%.3f".format(pid.pTerm))
                telemetry.addData("PID I", "%.3f".format(pid.iTerm))
                telemetry.addData("PID D", "%.3f".format(pid.dTerm))

            } else {
                telemetry.addData("Status", "No Tag")
            }
        } else {
            telemetry.addData("Status", "Disabled")
        }
    }
}
