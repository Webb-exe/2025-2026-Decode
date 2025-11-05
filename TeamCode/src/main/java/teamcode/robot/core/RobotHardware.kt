package teamcode.robot.core

import com.qualcomm.hardware.limelightvision.Limelight3A
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.qualcomm.robotcore.hardware.DcMotorEx
import com.qualcomm.robotcore.hardware.Servo
import com.qualcomm.robotcore.util.ElapsedTime
import dev.nextftc.hardware.impl.MotorEx
import dev.nextftc.hardware.impl.ServoEx

object RobotHardware {
    var opMode: LinearOpMode? = null
    var runtime: ElapsedTime? = null

    // Movement Motors
    var leftFront: MotorEx? = null
    var leftBack: MotorEx? = null
    var rightFront: MotorEx? = null
    var rightBack: MotorEx? = null

    // Turret
    var turretTurnMotor: MotorEx? = null
    var turretShooterLeftMotor: MotorEx? = null
    var turretShooterRightMotor: MotorEx? = null

    // Servo
    var kickerServo: ServoEx? = null

    // Limelight
    var limelight: Limelight3A? = null

    // State
    var alliance: Alliance? = Alliance.RED


    private fun setHardware() {
        leftFront = MotorEx(
            opMode!!.hardwareMap.get<DcMotorEx?>(
                DcMotorEx::class.java,
                "leftFrontDrive"
            )
        ).reversed()
        leftBack = MotorEx(
            opMode!!.hardwareMap.get<DcMotorEx?>(
                DcMotorEx::class.java,
                "leftBackDrive"
            )
        ).reversed()
        rightFront =
            MotorEx(opMode!!.hardwareMap.get<DcMotorEx?>(DcMotorEx::class.java, "rightFrontDrive"))
        rightBack =
            MotorEx(opMode!!.hardwareMap.get<DcMotorEx?>(DcMotorEx::class.java, "rightBackDrive"))

        turretTurnMotor = MotorEx(
            opMode!!.hardwareMap.get<DcMotorEx?>(
                DcMotorEx::class.java,
                "turretTurnMotor"
            )
        ).reversed()
        turretShooterLeftMotor = MotorEx(
            opMode!!.hardwareMap.get<DcMotorEx?>(
                DcMotorEx::class.java,
                "turretShooterLeftMotor"
            )
        ).reversed()
        turretShooterRightMotor = MotorEx(
            opMode!!.hardwareMap.get<DcMotorEx?>(
                DcMotorEx::class.java,
                "turretShooterRightMotor"
            )
        )

        kickerServo = ServoEx(opMode!!.hardwareMap.get<Servo?>(Servo::class.java, "kickerServo"))

        limelight = opMode!!.hardwareMap.get<Limelight3A?>(Limelight3A::class.java, "limelight")
    }

    private fun initHardware() {
        kickerServo!!.position = 0
    }

    fun setAlliance(allianceInput: Alliance?) {
        alliance = allianceInput
    }

    fun init(OpModeInput: LinearOpMode?, runtimeInput: ElapsedTime?) {
        opMode = OpModeInput
        runtime = runtimeInput
        setHardware()
        initHardware()
    }
}
