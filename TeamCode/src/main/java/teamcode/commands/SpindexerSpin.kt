package teamcode.commands

import teamcode.robot.command.Command
import teamcode.robot.subsystems.SpindexerState
import teamcode.robot.subsystems.SpindexerSubsystem

/**
 * Command to spin the spindexer by a position offset.
 * Calls subsystem methods directly (cross-thread).
 */
class SpindexerSpin(private val offset: Int = 1): Command(false) {

    // Get subsystem reference - automatically adds to requirements
    private val spindexer = current<SpindexerSubsystem>()


    override fun initialize() {
        spindexer.changeTargetPositionByOffset(offset)
    }

    override fun periodic() {

    }

    override fun isFinished(): Boolean {
        return true
    }
}

class SpindexerSpinWait(private val offset: Int = 1): Command(false) {

    // Get subsystem reference - automatically adds to requirements
    private val spindexer = current<SpindexerSubsystem>()

    private var initiated = false

    override fun initialize() {
        spindexer.changeTargetPositionByOffset(offset)
    }

    override fun periodic() {

    }

    override fun isFinished(): Boolean {
        return spindexer.currentState== SpindexerState.IDLE
    }
}