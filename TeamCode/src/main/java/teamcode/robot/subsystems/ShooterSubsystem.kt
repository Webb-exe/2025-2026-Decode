package teamcode.robot.subsystems

import com.bylazar.configurables.annotations.Configurable
import teamcode.robot.control.GamepadEx
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.state.RobotState
import teamcode.robot.core.state.RobotStateMachine
import teamcode.robot.core.subsystem.Subsystem
import teamcode.robot.core.utils.Timer
import kotlin.concurrent.Volatile

@Configurable
object ShooterConfig {
    @JvmField
    var ShooterPower = 0.9
    @JvmField
    var ShooterIdlePower = 0.2
    @JvmField
    var KickerIdlePosition = 0.0
    @JvmField
    var KickerUpPosition = 0.2
}

enum class  ShooterState {
    IDLE,
    SHOOTING,
}

enum class  KickerState {
    IDLE,
    UP,
    DOWN,
}

/**
 * Shooter subsystem.
 * Controls shooter motors for launching game pieces.
 */
class ShooterSubsystem : Subsystem("Shooter", 20) {

    @Volatile
    var currentState: ShooterState = ShooterState.IDLE
        private set

    @Volatile
    var kickerState : KickerState = KickerState.IDLE
        private set

    var timer = Timer()

    fun triggerKicker(){
        if (kickerState== KickerState.IDLE&& isRobotState(RobotState.SHOOTING)){
            kickerState=KickerState.UP
            return
        }

        if (kickerState== KickerState.UP){
            kickerState=KickerState.DOWN
            timer.start()
            return
        }
    }

    override fun periodic() {

        when (kickerState) {
            KickerState.IDLE -> {
                currentState = ShooterState.IDLE
                RobotHardware.kickerServo.set(ShooterConfig.KickerIdlePosition)
            }
            KickerState.UP -> {
                currentState = ShooterState.SHOOTING
                RobotHardware.kickerServo.set(ShooterConfig.KickerUpPosition)
            }
            KickerState.DOWN -> {
                currentState = ShooterState.SHOOTING
                RobotHardware.kickerServo.set(ShooterConfig.KickerIdlePosition)
                if (timer.elapsedMillis()>2000) {
                    kickerState = KickerState.IDLE
                    timer.stop()
                }
            }
        }

        if (isRobotState(RobotState.SHOOTING)) {
            RobotHardware.turretShooterLeftMotor.set(ShooterConfig.ShooterPower)
            RobotHardware.turretShooterRightMotor.set(ShooterConfig.ShooterPower)
        }else{
            RobotHardware.turretShooterLeftMotor.set(ShooterConfig.ShooterIdlePower)
            RobotHardware.turretShooterRightMotor.set(ShooterConfig.ShooterIdlePower)
        }

    }
    
    override fun updateTelemetry() {
        telemetry.addData("kickerState", kickerState)
        telemetry.addData("currentState",currentState)
        telemetry.addData("Left Motor", String.format("%.2f", RobotHardware.turretShooterLeftMotor.get()))
        telemetry.addData("Right Motor", String.format("%.2f", RobotHardware.turretShooterRightMotor.get()))
        telemetry.addData("Timer",timer.elapsedSeconds())
    }
}
