package teamcode.commands

import teamcode.robot.command.Command
import teamcode.robot.command.SequentialCommandGroup
import teamcode.robot.command.WaitCommand
import teamcode.commands.Shoot
import teamcode.robot.subsystems.KickerState
import teamcode.robot.subsystems.KickerSubsystem
import teamcode.robot.subsystems.ShooterState
import teamcode.robot.subsystems.ShooterSubsystem
import teamcode.robot.subsystems.SpindexerState
import teamcode.robot.subsystems.SpindexerSubsystem
import teamcode.threading.RobotThread

fun ShootAndTurn(): SequentialCommandGroup {
    if (RobotThread.current<SpindexerSubsystem>().currentState!==SpindexerState.IDLE ||
        RobotThread.current<ShooterSubsystem>().currentState!== ShooterState.SHOOTING_READY
    ){
        return SequentialCommandGroup()
    }
    return SequentialCommandGroup(
        Shoot(),
        SpindexerSpin(1),
    )
}