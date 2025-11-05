package teamcode.robot.core

import com.qualcomm.hardware.limelightvision.Limelight3A
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.Servo
import com.qualcomm.robotcore.util.ElapsedTime
import dev.nextftc.hardware.impl.MotorEx
import dev.nextftc.hardware.impl.ServoEx
import dev.nextftc.hardware.positionable.SetPosition
import kotlin.concurrent.Volatile

object RobotHardware {
    lateinit var opMode: LinearOpMode
    lateinit var runtime: ElapsedTime

    // Movement Motors
    lateinit var leftFront: MotorEx
    lateinit var leftBack: MotorEx
    lateinit var rightFront: MotorEx
    lateinit var rightBack: MotorEx

    // Turret
    lateinit var turretTurnMotor: MotorEx
    lateinit var turretShooterLeftMotor: MotorEx
    lateinit var turretShooterRightMotor: MotorEx

    // Servo
    lateinit var kickerServo: ServoEx

    // Limelight
    lateinit var limelight: Limelight3A

    // State
    var alliance: Alliance = Alliance.RED
    @Volatile
    var DriverState: RobotStateEnum = RobotStateEnum.IDLE


    private fun setHardware() {
        leftFront = MotorEx(
            opMode.hardwareMap.get(
                DcMotorEx::class.java,
                "leftFrontDrive"
            )
        ).reversed()
        leftBack = MotorEx(
            opMode.hardwareMap.get(
                DcMotorEx::class.java,
                "leftBackDrive"
            )
        ).reversed()
        rightFront =
            MotorEx(opMode.hardwareMap.get(DcMotorEx::class.java, "rightFrontDrive"))
        rightBack =
            MotorEx(opMode.hardwareMap.get(DcMotorEx::class.java, "rightBackDrive"))

        turretTurnMotor = MotorEx(
            opMode.hardwareMap.get(
                DcMotorEx::class.java,
                "turretTurnMotor"
            )
        ).reversed()
        turretShooterLeftMotor = MotorEx(
            opMode.hardwareMap.get(
                DcMotorEx::class.java,
                "turretShooterLeftMotor"
            )
        ).reversed()
        turretShooterRightMotor = MotorEx(
            opMode.hardwareMap.get(
                DcMotorEx::class.java,
                "turretShooterRightMotor"
            )
        )

        kickerServo = ServoEx(opMode.hardwareMap.get(Servo::class.java, "kickerServo"))

        limelight = opMode.hardwareMap.get(Limelight3A::class.java, "limelight")
    }

    private fun initHardware() {
        SetPosition(kickerServo, 0.0)
    }

    fun init(OpModeInput: LinearOpMode, runtimeInput: ElapsedTime) {
        opMode = OpModeInput
        runtime = runtimeInput
        setHardware()
        initHardware()
    }
}
