package teamcode.robot;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.robotcore.util.ElapsedTime;

import dev.nextftc.hardware.impl.MotorEx;

public class RobotHardware {
    public static LinearOpMode opMode=null;
    public static ElapsedTime runtime=null;
    // Movement Motors
    public static MotorEx leftFront=null;
    public static MotorEx leftBack=null;
    public static MotorEx rightFront=null;
    public static MotorEx rightBack=null;

    // Turret
    public static MotorEx turretTurnMotor=null;
    public static MotorEx turretShooterLeftMotor=null;
    public static MotorEx turretShooterRightMotor=null;

    // Limelight
    public static Limelight3A limelight;

    private RobotHardware(){}

    public static void init(LinearOpMode OpModeInput, ElapsedTime runtimeInput) {
        opMode = OpModeInput;
        runtime = runtimeInput;
        leftFront = new MotorEx(opMode.hardwareMap.get(DcMotorEx.class, "leftFrontDrive"));
        leftBack = new MotorEx(opMode.hardwareMap.get(DcMotorEx.class, "leftBackDrive"));
        rightFront = new MotorEx(opMode.hardwareMap.get(DcMotorEx.class, "rightFrontDrive"));
        rightBack = new MotorEx(opMode.hardwareMap.get(DcMotorEx.class, "rightBackDrive"));

        turretTurnMotor = new MotorEx(opMode.hardwareMap.get(DcMotorEx.class, "turretTurnMotor")).reversed();
        turretShooterLeftMotor = new MotorEx(opMode.hardwareMap.get(DcMotorEx.class, "turretShooterLeftMotor"));
        turretShooterRightMotor = new MotorEx(opMode.hardwareMap.get(DcMotorEx.class, "turretShooterRightMotor"));

        limelight = opMode.hardwareMap.get(Limelight3A.class, "limelight");
    }
}
