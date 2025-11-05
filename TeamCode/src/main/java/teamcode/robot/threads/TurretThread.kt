package teamcode.robot.threads

import teamcode.robot.core.PID
import teamcode.robot.core.RobotConfig
import teamcode.robot.core.RobotHardware
import teamcode.threading.RobotThread
import teamcode.threading.RobotThread.Companion.current
import kotlin.concurrent.Volatile

/**
 * Thread for controlling turret rotation using PID control based on vision data.
 * Uses targetX from VisionThread to keep the target centered.
 */
class TurretThread : RobotThread("TurretThread", 10) {
    private var turretPID: PID? = null
    private var visionThread: VisionThread? = null

    /**
     * Check if turret control is enabled
     */
    @Volatile
    var isEnabled: Boolean = false
        private set

    override fun onStart() {
        visionThread = VisionThread.current<VisionThread?>()
        turretPID = PID(RobotConfig.TurretPGain, RobotConfig.TurretIGain, RobotConfig.TurretDGain)
        turretPID!!.setOutputRange(
            -RobotConfig.TurretSpeedClamp,
            RobotConfig.TurretSpeedClamp
        ) // Motor power range
        turretPID!!.setIntegratorRange(-0.5, 0.5) // Prevent integral windup
        turretPID!!.setScaleFactor(RobotConfig.TurretScaleFactor)
        turretPID!!.reset()
        // Setpoint is 0.0 to center the target (targetX = 0 means centered)
        turretPID!!.setSetpoint(0.0)
    }

    override fun runLoop() {
        if (!this.isEnabled) {
            telemetry!!.addData("Status", "Disabled")
            telemetry!!.addData("Enabled", false)
            telemetry!!.addData("Motor Power", 0.0)
            return
        }


        // Only control if vision has targets detected
        if (!visionThread!!.hasTargets()) {
            RobotHardware.turretTurnMotor.power = 0.0
            telemetry!!.addData("Status", "No Targets")
            telemetry!!.addData("Enabled", true)
            telemetry!!.addData("Motor Power", 0.0)
            telemetry!!.addData("Target Found", false)
            return
        }


        // Get targetX from vision thread (horizontal offset from crosshair in degrees)
        // Negative targetX means target is to the left, positive means right
        val aprilTag = visionThread!!.getAprilTag(24)
        if (aprilTag == null) {
            RobotHardware.turretTurnMotor.power = 0.0
            telemetry!!.addData("Status", "No AprilTag 24")
            telemetry!!.addData("Enabled", true)
            telemetry!!.addData("Motor Power", 0.0)
            telemetry!!.addData("Target Found", false)
            return
        }


        // Calculate PID output based on vision data
        // Setpoint is 0.0 (centered), measurement is targetX
        val output = turretPID!!.calculate(aprilTag.xDegrees)


        // Apply output to motor
        RobotHardware.turretTurnMotor.power = output


        // Update telemetry - data persists until next update from this thread
        telemetry!!.addData("Status", "Tracking")
        telemetry!!.addData("Enabled", true)
        telemetry!!.addData("Target Found", true)
        telemetry!!.addData("Target ID", aprilTag.id)
        telemetry!!.addData("Target X", String.format("%.2f°", aprilTag.xDegrees))
        telemetry!!.addData("Target Y", String.format("%.2f°", aprilTag.yDegrees))
        telemetry!!.addData("Motor Power", String.format("%.2f", output))
        telemetry!!.addData("Motor Position", RobotHardware.turretTurnMotor.currentPosition)
        telemetry!!.addData("PID Error", String.format("%.2f°", aprilTag.xDegrees))
    }

    /**
     * Enable turret control
     */
    fun enable() {
        this.isEnabled = true
    }

    /**
     * Disable turret control (stops motor)
     */
    fun disable() {
        this.isEnabled = false
        if (RobotHardware.turretTurnMotor != null) {
            RobotHardware.turretTurnMotor.power = 0.0
        }
    }

    /**
     * Reset PID controller
     */
    fun resetPID() {
        if (turretPID != null) {
            turretPID!!.reset()
        }
    }

    /**
     * Set PID gains
     */
    fun setPIDGains(kP: Double, kI: Double, kD: Double) {
        if (turretPID != null) {
            turretPID!!.setGains(kP, kI, kD)
        }
    }

    override fun onStop() {
        disable()
    }
}

