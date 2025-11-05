package teamcode.robot.core;

import com.bylazar.configurables.annotations.Configurable;

@Configurable
public class RobotConfig {
    public static double TurretPGain= 4;
    public static double TurretIGain= 0;
    public static double TurretDGain= 0.1;
    public static double TurretSpeedClamp =0.5;
    public static double TurretScaleFactor =0.01;
    public static double ShooterPower=0.8;
    public static double ShooterIdlePower=0.2;
    public static double TurnScale=0.5;
    public static double PowerScale=0.5;
}
