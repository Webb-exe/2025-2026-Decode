package teamcode.robot;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.hardware.limelightvision.Limelight3A;

import dev.nextftc.hardware.impl.MotorEx;

public class RobotHardware {
    public LinearOpMode myOpMode=null;
    // Movement Motors
    public static MotorEx leftFront=null;
    public static MotorEx leftBack=null;
    public static MotorEx rightFront=null;
    public static MotorEx rightBack=null;

    // Limelight
    public static Limelight3A limelight;

    private RobotHardware(){}

    public void init(LinearOpMode opmode) {
        myOpMode = opmode;
        leftFront = new MotorEx(myOpMode.hardwareMap.get(DcMotorEx.class, "leftFront"));
        leftBack = new MotorEx(myOpMode.hardwareMap.get(DcMotorEx.class, "leftBack"));
        rightFront = new MotorEx(myOpMode.hardwareMap.get(DcMotorEx.class, "rightFront"));
        rightBack = new MotorEx(myOpMode.hardwareMap.get(DcMotorEx.class, "rightBack"));

        limelight = myOpMode.hardwareMap.get(Limelight3A.class, "limelight");
    }
}
