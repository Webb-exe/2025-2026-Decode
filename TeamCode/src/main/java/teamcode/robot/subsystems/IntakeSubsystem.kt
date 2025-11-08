package teamcode.robot.subsystems

import teamcode.robot.core.RobotHardware
import teamcode.robot.core.state.RobotState
import teamcode.robot.core.state.RobotStateMachine
import teamcode.robot.core.subsystem.Subsystem

class IntakeSubsystem: Subsystem("Intake",10) {
    override fun periodic() {
        if(RobotStateMachine.isState(RobotState.INTAKING)){
            RobotHardware.intakeServoLeft.set(-1.0);
            RobotHardware.intakeServoRight.set(1.0);

        }else{
            RobotHardware.intakeServoLeft.set(0.0);

            RobotHardware.intakeServoRight.set(0.0);

        }
    }
}