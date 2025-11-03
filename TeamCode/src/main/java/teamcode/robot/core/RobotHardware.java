package teamcode.robot.core;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.util.ElapsedTime;

import dev.nextftc.hardware.impl.MotorEx;
import dev.nextftc.hardware.impl.ServoEx;

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

    // Servo
    public static ServoEx kickerServo=null;

    // Limelight
    public static Limelight3A limelight=null;

    // State
    public static Alliance alliance=Alliance.RED;

    private RobotHardware(){}

    private static void setHardware(){
        leftFront = new MotorEx(opMode.hardwareMap.get(DcMotorEx.class, "leftFrontDrive")).reversed();
        leftBack = new MotorEx(opMode.hardwareMap.get(DcMotorEx.class, "leftBackDrive")).reversed();
        rightFront = new MotorEx(opMode.hardwareMap.get(DcMotorEx.class, "rightFrontDrive"));
        rightBack = new MotorEx(opMode.hardwareMap.get(DcMotorEx.class, "rightBackDrive"));

        turretTurnMotor = new MotorEx(opMode.hardwareMap.get(DcMotorEx.class, "turretTurnMotor")).reversed();
        turretShooterLeftMotor = new MotorEx(opMode.hardwareMap.get(DcMotorEx.class, "turretShooterLeftMotor")).reversed();
        turretShooterRightMotor = new MotorEx(opMode.hardwareMap.get(DcMotorEx.class, "turretShooterRightMotor"));

        kickerServo = new ServoEx(opMode.hardwareMap.get(Servo.class,"kickerServo"));

        limelight = opMode.hardwareMap.get(Limelight3A.class, "limelight");
    }

    private static void initHardware(){
        kickerServo.setPosition(0);
    }

    public static void setAlliance(Alliance allianceInput){
        alliance = allianceInput;
    }

    public static void init(LinearOpMode OpModeInput, ElapsedTime runtimeInput) {
        opMode = OpModeInput;
        runtime = runtimeInput;
        setHardware();
        initHardware();
    }
}
