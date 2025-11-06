package teamcode.robot.subsystems

import teamcode.robot.core.PID
import teamcode.robot.core.RobotConfig
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.subsystem.Subsystem
import teamcode.threading.RobotThread
import kotlin.concurrent.Volatile

/**
 * Turret subsystem for target tracking.
 * Uses vision data to maintain target alignment via PID control.
 */
class TurretSubsystem : Subsystem("Turret", 10) {
    
    private lateinit var turretPID: PID
    private lateinit var vision: VisionSubsystem
    
    @Volatile
    var isEnabled: Boolean = false
        private set
    
    override fun init() {
        // Wait for VisionSubsystem to be available (threads start concurrently)
        vision = waitFor<VisionSubsystem>()
        
        turretPID = PID(RobotConfig.TurretPGain, RobotConfig.TurretIGain, RobotConfig.TurretDGain)
        turretPID.setOutputRange(-RobotConfig.TurretSpeedClamp, RobotConfig.TurretSpeedClamp)
        turretPID.setIntegratorRange(-0.5, 0.5)
        turretPID.setScaleFactor(RobotConfig.TurretScaleFactor)
        turretPID.reset()
        turretPID.setpoint = 0.0
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
