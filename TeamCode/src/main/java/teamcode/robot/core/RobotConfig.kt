package teamcode.robot.core

import com.bylazar.configurables.annotations.Configurable

@Configurable
object RobotConfig {
    var TurretPGain: Double = 4.0
    var TurretIGain: Double = 0.0
    var TurretDGain: Double = 0.1
    var TurretSpeedClamp: Double = 0.5
    var TurretScaleFactor: Double = 0.01
    var ShooterPower: Double = 0.8
    var ShooterIdlePower: Double = 0.2
    var TurnScale: Double = 0.5
    var PowerScale: Double = 0.5
}
