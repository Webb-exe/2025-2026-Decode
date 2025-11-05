package teamcode.teleop;

import static java.lang.Double.max;
import static java.lang.Double.min;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import teamcode.robot.core.Alliance;
import teamcode.robot.core.RobotHardware;
import teamcode.robot.subsystems.Shooter;
import teamcode.robot.threads.MovementThread;
import teamcode.robot.threads.TurretThread;
import teamcode.robot.threads.VisionThread;
import teamcode.threading.ThreadedOpMode;

/**
 * Example usage of ThreadedOpMode.
 * This demonstrates how to use threading in your robot OpMode.
 * Remove @Disabled annotation to enable this OpMode.
 */
@TeleOp(name = "Teleop", group = "Teleop")
public class Teleop extends ThreadedOpMode {
    private MovementThread movementThread;
    private TurretThread turretThread;
    private VisionThread visionThread;
    
    @Override
    protected void initializeThreads() {
        // Create and configure threads
        movementThread = new MovementThread();
        turretThread = new TurretThread();
        visionThread = new VisionThread();
        
        // Add threads to manager
        threadManager.addThread(movementThread);
        threadManager.addThread(visionThread);
        threadManager.addThread(turretThread);
    }

    @Override
    protected void runInit() {
        Alliance alliance= Alliance.RED;

        while(opModeInInit()){
            telemetry.addData("Status", "In Init");


            if (gamepad1.xWasPressed()){
                alliance = Alliance.BLUE;
            }
            if (gamepad1.bWasPressed()){
                alliance = Alliance.RED;
            }
            telemetry.update();
        }

        RobotHardware.setAlliance(alliance);
    }
    
    // State variables for button press detection
    private boolean firstPressA = false;
    private boolean firstPressB = false;
    private boolean firstPressRightBumper = false;
    private boolean firstPressLeftBumper = false;
    
    @Override
    protected void onStart() {
        // Enable turret control when OpMode starts
        turretThread.enable();
    }
    
    @Override
    protected void mainLoop() {
        // Main loop iteration - called repeatedly by ThreadedOpMode
        // Telemetry staging and updates are handled automatically!
        
        // ===== MOVEMENT CONTROL =====
        // Update movement thread with gamepad1 input
        movementThread.setDriveInput(gamepad1);
        
        // Toggle precision mode with left trigger (reduces speed to 30%)
        if (gamepad1.left_trigger > 0.5) {
            movementThread.setSpeedMultiplier(0.3);
        } else {
            movementThread.setSpeedMultiplier(1.0);
        }
        
        // ===== TURRET CONTROL =====
        // Turret automatically tracks targets using vision-based PID
        firstPressA = gamepad1.a && !firstPressA;
        firstPressB = gamepad1.b && !firstPressB;
        firstPressRightBumper = gamepad1.right_bumper && !firstPressRightBumper;
        firstPressLeftBumper = gamepad1.left_bumper && !firstPressLeftBumper;

        if (firstPressA) {
            turretThread.enable();
        }

        if (firstPressB) {
            turretThread.disable();
        }

        RobotHardware.turretShooterRightMotor.setCurrentPosition(gamepad1.right_trigger);
        RobotHardware.turretShooterLeftMotor.setCurrentPosition(gamepad1.right_trigger);

        telemetry.addData("Shooter Power", gamepad1.right_trigger);

        // ===== TELEMETRY =====
        // Just use telemetry.addData() - namespace and updates are handled automatically!
        telemetry.addData("Runtime", String.format("%.2f s", runtime.seconds()));
        telemetry.addData("Loop Time", String.format("%.0f ms", runtime.milliseconds()));
        
        // Add servo position if available
        if (RobotHardware.kickerServo != null) {
            telemetry.addData("Kicker Servo Pos", String.format("%.2f", RobotHardware.kickerServo.getPosition()));
        }
    }
    
    @Override
    protected void cleanup() {
        // Disable threads before stopping
        movementThread.disable();
        turretThread.disable();
    }
}

