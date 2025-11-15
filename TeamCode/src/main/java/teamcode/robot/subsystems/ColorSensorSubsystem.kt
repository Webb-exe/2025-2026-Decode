package teamcode.robot.subsystems

import com.qualcomm.robotcore.hardware.ColorSensor
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.subsystem.Subsystem

enum class BallColors {
    PURPLE,
    GREEN,
    EMPTY
}

data class ColorSensorResult(
    val left: BallColors,
    val back: BallColors,
    val right: BallColors,
)

class ColorSensorSubsystem: Subsystem("ColorSensor", 10) {

    @Volatile
    final var isEnabled: Boolean = true
        private set

    @Volatile
    final var currentColors: ColorSensorResult = ColorSensorResult(
        BallColors.EMPTY,
        BallColors.EMPTY,
        BallColors.EMPTY
    )
        private set


    private fun getColor(colorSensor: ColorSensor): BallColors {
        val red = colorSensor.red()
        val rawOptiocal = colorSensor
        val green = colorSensor.green()
        val blue = colorSensor.blue()

        if (red > green && red > blue) {
            return BallColors.PURPLE
        } else if (green > red && green > blue) {
            return BallColors.GREEN
        } else {
            return BallColors.EMPTY
        }
    }

    override fun periodic() {
        if (!isEnabled) return

        val leftColor = getColor(RobotHardware.colorSensorLeft)
        val backColor = getColor(RobotHardware.colorSensorBack)
        val rightColor = getColor(RobotHardware.colorSensorRight)

        currentColors = ColorSensorResult(leftColor, backColor, rightColor)
    }

    override fun updateTelemetry() {
        telemetry.addData("Enabled", isEnabled)
        telemetry.addData("Left Color", currentColors.left.name)
        telemetry.addData("Back Color", currentColors.back.name)
        telemetry.addData("Right Color", currentColors.right.name)
        telemetry.addData("Left RGB", "R:${RobotHardware.colorSensorLeft.getDistance(DistanceUnit.CM)} G:${RobotHardware.colorSensorLeft.green()} B:${RobotHardware.colorSensorLeft.blue()}")
        telemetry.addData("Back RGB", "R:${RobotHardware.colorSensorBack.red()} G:${RobotHardware.colorSensorBack.green()} B:${RobotHardware.colorSensorBack.blue()}")
        telemetry.addData("Right RGB", "R:${RobotHardware.colorSensorRight.red()} G:${RobotHardware.colorSensorRight.green()} B:${RobotHardware.colorSensorRight.blue()}")
    }
}