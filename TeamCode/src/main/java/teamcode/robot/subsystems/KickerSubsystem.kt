package teamcode.robot.subsystems

import com.bylazar.configurables.annotations.Configurable
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.state.RobotState
import teamcode.robot.core.subsystem.Subsystem
import teamcode.robot.core.utils.Timer
import kotlin.concurrent.Volatile

@Configurable
object KickerConfig {
    @JvmField
    var KickerIdlePosition = 0.0
    @JvmField
    var KickerUpPosition = 0.2
    @JvmField
    var KickerDownDelayMillis = 300
}

enum class KickerState {
    IDLE,
    UP,
    DOWN,
}

/**
 * Kicker subsystem.
 * Controls the kicker servo for launching game pieces.
 */
class KickerSubsystem : Subsystem("Kicker", 20) {

    @Volatile
    var currentState: KickerState = KickerState.IDLE
        private set

    var timer = Timer()

    /**
     * Trigger the kicker mechanism
     * Called from teleop when button is pressed
     */

    fun kickerUp(){
        // Check if we're in shooting state and kicker is idle - start kicking
        if (currentState == KickerState.IDLE && isRobotState(RobotState.SHOOTING)) {
            currentState = KickerState.UP
            return
        }
    }

    fun kickerDown(){
        // If kicker is up, move it down
        if (currentState == KickerState.UP) {
            currentState = KickerState.DOWN
            timer.start()
            return
        }
    }

    fun triggerKicker() {
        // Check if spindexer is busy - don't shoot if it is
        if (current<SpindexerSubsystem>().currentState != SpindexerState.IDLE) {
            return
        }

        if (currentState == KickerState.IDLE && isRobotState(RobotState.SHOOTING)) {
            kickerUp()
            return
        }

        if (currentState == KickerState.UP) {
            kickerDown()
            return
        }
        

    }

    override fun periodic() {
        when (currentState) {
            KickerState.IDLE -> {
                RobotHardware.kickerServo.set(KickerConfig.KickerIdlePosition)
            }
            KickerState.UP -> {
                RobotHardware.kickerServo.set(KickerConfig.KickerUpPosition)
            }
            KickerState.DOWN -> {
                RobotHardware.kickerServo.set(KickerConfig.KickerIdlePosition)
                if (timer.elapsedMillis() > KickerConfig.KickerDownDelayMillis) {
                    currentState = KickerState.IDLE
                    timer.stop()
                }
            }
        }
    }
    
    override fun updateTelemetry() {
        telemetry.addData("currentState", currentState)
        telemetry.addData("Timer", timer.elapsedSeconds())
    }
}

