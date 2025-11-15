package teamcode.robot.core

import com.qualcomm.hardware.limelightvision.Limelight3A
import com.qualcomm.hardware.rev.RevColorSensorV3
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import com.qualcomm.robotcore.hardware.ColorSensor
import com.qualcomm.robotcore.hardware.Servo
import com.qualcomm.robotcore.util.ElapsedTime
import com.seattlesolvers.solverslib.hardware.motors.CRServoEx
import com.seattlesolvers.solverslib.hardware.motors.Motor
import com.seattlesolvers.solverslib.hardware.motors.MotorEx
import com.seattlesolvers.solverslib.hardware.servos.ServoEx
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

    // Spindexter
    lateinit var spindexterMotor: MotorEx

    // Servo
    lateinit var kickerServo: ServoEx
    lateinit var turretTurnServo: ServoEx

    // Intake
    lateinit var intakeServoLeft: CRServoEx
    lateinit var intakeServoRight: CRServoEx

    // Limelight
    lateinit var limelight: Limelight3A

    // Color sensor
    lateinit var colorSensorBack: ColorSensor
    lateinit var colorSensorLeft: RevColorSensorV3
    lateinit var colorSensorRight: ColorSensor

    private fun setHardware() {
        leftFront = MotorEx(opMode.hardwareMap, "leftFrontDrive").apply {
            setZeroPowerBehavior(Motor.ZeroPowerBehavior.BRAKE)
        }
        leftBack = MotorEx(opMode.hardwareMap, "leftBackDrive").apply {
            setZeroPowerBehavior(Motor.ZeroPowerBehavior.BRAKE)
        }
        rightFront = MotorEx(opMode.hardwareMap, "rightFrontDrive").apply {
            setInverted(true)
            setZeroPowerBehavior(Motor.ZeroPowerBehavior.BRAKE)
        }
        rightBack = MotorEx(opMode.hardwareMap, "rightBackDrive").apply {
            setInverted(true)
            setZeroPowerBehavior(Motor.ZeroPowerBehavior.BRAKE)
        }

        turretTurnMotor = MotorEx(opMode.hardwareMap, "turretTurnMotor").apply {
            setInverted(true)
            setZeroPowerBehavior(Motor.ZeroPowerBehavior.BRAKE)
        }
        turretShooterLeftMotor = MotorEx(opMode.hardwareMap, "turretShooterLeftMotor").apply {
            setInverted(true)
            setZeroPowerBehavior(Motor.ZeroPowerBehavior.BRAKE)
        }
        turretShooterRightMotor = MotorEx(opMode.hardwareMap, "turretShooterRightMotor")

        spindexterMotor = MotorEx(opMode.hardwareMap, "spindexterMotor", Motor.GoBILDA.RPM_312).apply {
            setInverted(true)
            setZeroPowerBehavior(Motor.ZeroPowerBehavior.BRAKE)
            resetEncoder()
            setRunMode(Motor.RunMode.RawPower)
        }

        kickerServo = ServoEx(opMode.hardwareMap, "kickerServo")
        turretTurnServo = ServoEx(opMode.hardwareMap, "turretTurnServo")

        intakeServoLeft = CRServoEx(opMode.hardwareMap, "intakeServoLeft")
        intakeServoRight = CRServoEx(opMode.hardwareMap, "intakeServoRight")


        limelight = opMode.hardwareMap.get(Limelight3A::class.java, "limelight")

        colorSensorBack = opMode.hardwareMap.get(ColorSensor::class.java, "colorSensorBack")
        colorSensorLeft = opMode.hardwareMap.get(RevColorSensorV3::class.java, "colorSensorLeft")
        colorSensorRight = opMode.hardwareMap.get(ColorSensor::class.java, "colorSensorRight")
    }

    private fun initHardware() {
        kickerServo.set(0.0);
        turretTurnServo.set(0.5)
    }

    fun init(opModeInput: LinearOpMode, runtimeInput: ElapsedTime) {
        opMode = opModeInput
        runtime = runtimeInput
        setHardware()
        initHardware()
    }
}
