package teamcode.robot.subsystems

import com.bylazar.configurables.annotations.Configurable
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.state.RobotState
import teamcode.robot.core.subsystem.Subsystem
import kotlin.concurrent.Volatile

@Configurable
object ShooterConfig {
    @JvmField
    var ShooterPower = 0.75
    @JvmField
    var ShooterIdlePower = 0.2
}

enum class  ShooterState {
    IDLE,
    SHOOTING_READY,
}

/**
 * Shooter subsystem.
 * Controls shooter motors for launching game pieces.
 */
class ShooterSubsystem : Subsystem("Shooter", 20) {

    @Volatile
    var currentState: ShooterState = ShooterState.IDLE
        private set

    override fun periodic() {


        // Control shooter motors based on robot state
        if (isRobotState(RobotState.SHOOTING)) {
            currentState= ShooterState.SHOOTING_READY
            RobotHardware.turretShooterLeftMotor.set(ShooterConfig.ShooterPower)
            RobotHardware.turretShooterRightMotor.set(ShooterConfig.ShooterPower)
        } else {
            currentState=ShooterState.IDLE
            RobotHardware.turretShooterLeftMotor.set(ShooterConfig.ShooterIdlePower)
            RobotHardware.turretShooterRightMotor.set(ShooterConfig.ShooterIdlePower)
        }
    }
    
    override fun updateTelemetry() {
        telemetry.addData("currentState", currentState)
        telemetry.addData("Left Motor", RobotHardware.turretShooterLeftMotor.get())
        telemetry.addData("Right Motor", RobotHardware.turretShooterRightMotor.get())
    }
}
