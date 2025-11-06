package teamcode.robot.subsystems

import teamcode.robot.core.RobotHardware
import teamcode.robot.core.subsystem.Subsystem
import kotlin.concurrent.Volatile

/**
 * Shooter subsystem.
 * Controls shooter motors for launching game pieces.
 */
class ShooterSubsystem : Subsystem("Shooter", 20) {
    
    @Volatile
    private var targetPower: Double = 0.0
    
    fun setPower(power: Double) {
        targetPower = power.coerceIn(-1.0, 1.0)
    }
    
    fun getPower(): Double = targetPower
    
    fun stopShooter() {
        setPower(0.0)
    }
    
    override fun periodic() {
        RobotHardware.turretShooterLeftMotor.set(targetPower)
        RobotHardware.turretShooterRightMotor.set(targetPower)

    }
    
    override fun init() {
        stopShooter()
    }
    
    override fun end() {
        stopShooter()
    }
    
    override fun updateTelemetry() {
        super.updateTelemetry()
        telemetry.addData("Power", String.format("%.2f", targetPower))
        telemetry.addData("Left Motor", String.format("%.2f", RobotHardware.turretShooterLeftMotor.get()))
        telemetry.addData("Right Motor", String.format("%.2f", RobotHardware.turretShooterRightMotor.get()))
    }
}
