package teamcode.robot.subsystems

import dev.nextftc.core.commands.Command
import dev.nextftc.core.commands.groups.ParallelGroup
import dev.nextftc.core.subsystems.Subsystem
import dev.nextftc.hardware.powerable.SetPower
import teamcode.robot.core.RobotConfig
import teamcode.robot.core.RobotHardware

class Shooter : Subsystem {
    override fun initialize(){}
    fun activateShooter(): Command {
        return ParallelGroup(
            SetPower(RobotHardware.turretShooterLeftMotor, RobotConfig.ShooterPower),
            SetPower(RobotHardware.turretShooterRightMotor, RobotConfig.ShooterPower)
        )
    }

    fun deactivateShooter(): Command {
        return ParallelGroup(
            SetPower(RobotHardware.turretShooterLeftMotor, RobotConfig.ShooterIdlePower),
            SetPower(RobotHardware.turretShooterRightMotor, RobotConfig.ShooterIdlePower)
        )
    }
}
