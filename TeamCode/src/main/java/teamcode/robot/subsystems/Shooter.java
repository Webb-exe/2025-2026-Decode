package teamcode.robot.subsystems;

import dev.nextftc.core.commands.Command;
import dev.nextftc.core.commands.groups.ParallelGroup;
import dev.nextftc.core.subsystems.Subsystem;
import dev.nextftc.hardware.powerable.SetPower;
import teamcode.robot.core.RobotConfig;
import teamcode.robot.core.RobotHardware;

public class Shooter implements Subsystem {
    public Command activateShooter() {
        return new ParallelGroup(
                new SetPower(RobotHardware.turretShooterLeftMotor,RobotConfig.ShooterPower),
                new SetPower(RobotHardware.turretShooterRightMotor,RobotConfig.ShooterPower)
        );
    }

    public Command deactivateShooter() {
        return new ParallelGroup(
                new SetPower(RobotHardware.turretShooterLeftMotor,RobotConfig.ShooterIdlePower),
                new SetPower(RobotHardware.turretShooterRightMotor,RobotConfig.ShooterIdlePower)
        );
    }
}
