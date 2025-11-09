package teamcode.robot.subsystems

import com.bylazar.configurables.annotations.Configurable
import teamcode.robot.core.PID
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.subsystem.Subsystem
import teamcode.threading.RobotThread
import kotlin.concurrent.Volatile

@Configurable
object TurretConfig {
    @JvmField
    var kP: Double = 4.0
    @JvmField
    var kI: Double = 0.0
    @JvmField
    var kD: Double = 0.1
    @JvmField
    var SpeedClamp: Double = 0.5
    @JvmField
    var ScaleFactor: Double = 0.01
    @JvmField
    var ManualTurnSpeed: Double = 0.4
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
    
    private lateinit var turretPID: PID
    private lateinit var vision: VisionSubsystem
    
    @Volatile
    var isEnabled: Boolean = true
        private set

    @Volatile
    var currentState: TurretState = TurretState.AUTO
        private set
    
    override fun init() {
        // Wait for VisionSubsystem to be available (threads start concurrently)
        vision = current<VisionSubsystem>(10)
        
        turretPID = PID(TurretConfig.kP, TurretConfig.kI, TurretConfig.kD)
        turretPID.setOutputRange(-TurretConfig.SpeedClamp, TurretConfig.SpeedClamp)
        turretPID.setIntegratorRange(-0.5, 0.5)
        turretPID.setScaleFactor(TurretConfig.ScaleFactor)
        turretPID.reset()
        turretPID.setpoint = 0.0

        RobotHardware.turretTurnMotor.set(0.0)
    }

    
    override fun periodic() {
        if (!isEnabled) return
        
        if (!vision.hasTargets()) {
            RobotHardware.turretTurnMotor.set(0.0)
            return
        }
        
        val aprilTag = vision.getAprilTag(24)
        if (aprilTag == null) {
            RobotHardware.turretTurnMotor.set(0.0)
            return
        }
        
        val output = turretPID.calculate(aprilTag.xDegrees)
        RobotHardware.turretTurnMotor.set(output)
    }
    
    fun enable() {
        isEnabled = true
    }
    
    fun disable() {
        isEnabled = false
        RobotHardware.turretTurnMotor.set(0.0)
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

    fun setManualTurnSpeed(speed: Double) {
        val clampedSpeed = speed.coerceIn(-1.0, 1.0) * TurretConfig.ManualTurnSpeed
        RobotHardware.turretTurnMotor.set(clampedSpeed)
    }
    
    fun resetPID() {
        if (::turretPID.isInitialized) {
            turretPID.reset()
        }
    }
    
    fun setPIDGains(kP: Double, kI: Double, kD: Double) {
        if (::turretPID.isInitialized) {
            turretPID.setGains(kP, kI, kD)
        }
    }
    
    override fun end() {
        disable()
    }
    
    override fun updateTelemetry() {
        super.updateTelemetry()
        telemetry.addData("Enabled", isEnabled)
        telemetry.addData("State", currentState)
        
        if (isEnabled && vision.hasTargets()) {
            val tag = vision.getAprilTag(24)
            if (tag != null) {
                telemetry.addData("Status", "Tracking")
                telemetry.addData("Target ID", tag.id)
                telemetry.addData("Target X", String.format("%.2f°", tag.xDegrees))
                telemetry.addData("Motor Power", String.format("%.2f", RobotHardware.turretTurnMotor.get()))
            } else {
                telemetry.addData("Status", "No Tag")
            }
        } else {
            telemetry.addData("Status", "Disabled")
        }
    }
}
