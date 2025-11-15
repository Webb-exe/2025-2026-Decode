package teamcode.commands

import teamcode.robot.command.Command
import teamcode.robot.command.SequentialCommandGroup
import teamcode.robot.command.WaitCommand
import teamcode.robot.command.WaitUntilCommand
import teamcode.robot.core.utils.Timer
import teamcode.robot.subsystems.KickerState
import teamcode.robot.subsystems.KickerSubsystem
import teamcode.robot.subsystems.SpindexerState
import teamcode.robot.subsystems.SpindexerSubsystem
import teamcode.threading.RobotThread

class kickerUp: Command(false){

    private var kicker = current<KickerSubsystem>()
    private var timer = Timer()

    override fun initialize() {
        kicker.kickerUp()
        timer.start()
    }

    override fun isFinished(): Boolean {
        return timer.hasElapsed(0.5)
    }
}

class kickerDown: Command(false){

    private var kicker = current<KickerSubsystem>()

    override fun initialize() {
        kicker.kickerDown()
    }

    override fun isFinished(): Boolean {
        return kicker.currentState === KickerState.IDLE
    }
}

/**
 * Shoot command - triggers the kicker twice with a delay.
 * This command properly schedules on the command scheduler.
 */
fun Shoot(): SequentialCommandGroup {
    if (RobotThread.current<SpindexerSubsystem>().currentState!== SpindexerState.IDLE){
        return SequentialCommandGroup()
    }
    return SequentialCommandGroup(
        kickerUp(),
        kickerDown()
    )
}