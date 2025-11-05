package teamcode.teleop

import com.qualcomm.robotcore.eventloop.opmode.TeleOp
import dev.nextftc.hardware.positionable.SetPosition
import teamcode.robot.core.Alliance
import teamcode.robot.core.RobotHardware
import teamcode.robot.subsystems.Shooter
import teamcode.robot.threads.MovementThread
import teamcode.robot.threads.TurretThread
import teamcode.robot.threads.VisionThread
import teamcode.threading.ThreadedOpMode

/**
 * Example usage of ThreadedOpMode.
 * This demonstrates how to use threading in your robot OpMode.
 * Remove @Disabled annotation to enable this OpMode.
 */
@TeleOp(name = "Teleop", group = "Teleop")
class Teleop : ThreadedOpMode() {
    private lateinit var movementThread: MovementThread
    private lateinit var turretThread: TurretThread
    private lateinit var visionThread: VisionThread

    override fun initializeThreads() {
        // Create and configure threads
        movementThread = MovementThread()
        turretThread = TurretThread()
        visionThread = VisionThread()


        // Add threads to manager
        threadManager.addThread(movementThread)
        threadManager.addThread(visionThread)
        threadManager.addThread(turretThread)
    }

    override fun runInit() {
        var alliance = Alliance.RED

        while (opModeInInit()) {
            telemetry.addData("Status", "In Init")


            if (gamepad1.xWasPressed()) {
                alliance = Alliance.BLUE
            }
            if (gamepad1.bWasPressed()) {
                alliance = Alliance.RED
            }
            telemetry.update()
        }

        RobotHardware.alliance = alliance
    }

    // State variables for button press detection
    private var firstPressA = false
    private var firstPressB = false
    private var firstPressRightBumper = false
    private var firstPressLeftBumper = false

    override fun onStart() {
        // Enable turret control when OpMode starts
        turretThread!!.enable()
    }

    override fun mainLoop() {
        // Main loop iteration - called repeatedly by ThreadedOpMode
        // Telemetry staging and updates are handled automatically!

        // ===== MOVEMENT CONTROL =====
        // Update movement thread with gamepad1 input

        movementThread!!.setDriveInput(gamepad1)


        // Toggle precision mode with left trigger (reduces speed to 30%)
        if (gamepad1.left_trigger > 0.5) {
            movementThread!!.setSpeedMultiplier(0.3)
        } else {
            movementThread!!.setSpeedMultiplier(1.0)
        }


        // ===== TURRET CONTROL =====
        // Turret automatically tracks targets using vision-based PID
        firstPressA = gamepad1.a && !firstPressA
        firstPressB = gamepad1.b && !firstPressB
        firstPressRightBumper = gamepad1.right_bumper && !firstPressRightBumper
        firstPressLeftBumper = gamepad1.left_bumper && !firstPressLeftBumper

        if (firstPressA) {
            turretThread.enable()
            Shooter.
        }

        if (firstPressB) {
            turretThread.disable()

        }


        telemetry.addData("Shooter Power", gamepad1.right_trigger)

        // ===== TELEMETRY =====
        // Just use telemetry.addData() - namespace and updates are handled automatically!
        telemetry.addData("Runtime", String.format("%.2f s", runtime!!.seconds()))
        telemetry.addData("Loop Time", String.format("%.0f ms", runtime!!.milliseconds()))

        telemetry.addData(
            "Kicker Servo Pos",
            String.format("%.2f", RobotHardware.kickerServo.position)
        )
    }

    override fun cleanup() {
        // Disable threads before stopping
        movementThread.disable()
        turretThread.disable()
    }
}

