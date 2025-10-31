package teamcode.teleop;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import teamcode.robot.RobotHardware;
import teamcode.robot.TurretThread;
import teamcode.robot.VisionThread;
import teamcode.threading.ThreadedOpMode;

/**
 * Example usage of ThreadedOpMode.
 * This demonstrates how to use threading in your robot OpMode.
 * Remove @Disabled annotation to enable this OpMode.
 */
@TeleOp(name = "Teleop", group = "Teleop")
public class Teleop extends ThreadedOpMode {
    private TurretThread turretThread;
    private VisionThread visionThread;
    
    @Override
    protected void initializeThreads() {
        // Create and configure threads
        turretThread = new TurretThread();
        visionThread = new VisionThread();
        
        // Connect vision thread to turret thread for vision-based PID control
        turretThread.setVisionThread(visionThread);
        
        // Add threads to manager
        threadManager.addThread(visionThread);
        threadManager.addThread(turretThread);
    }
    
    @Override
    protected void runOpModeThreaded() {
        // Enable turret control
        turretThread.enable();
        
        // Run until the end of the match (driver presses STOP)
        while (opModeIsActive()) {
            // Main loop - threads run concurrently
            // Turret automatically tracks targets using vision-based PID
            
            // Example: Telemetry update
            telemetry.addData("Vision Targets", visionThread.hasTargets());
            telemetry.addData("Target X", visionThread.getTargetX());
            telemetry.addData("Target Y", visionThread.getTargetY());
            telemetry.addData("Turret Enabled", turretThread.isEnabled());
            telemetry.addData("Turret Motor Position", RobotHardware.turretTurnMotor != null ? RobotHardware.turretTurnMotor.getCurrentPosition() : "N/A");
            telemetry.update();
            
            // Small sleep to prevent excessive CPU usage
            sleep(20);
        }
        
        // Disable turret before stopping
        turretThread.disable();
    }
    
    @Override
    protected void cleanup() {
        // Additional cleanup if needed
        // Threads are already stopped by ThreadManager
    }
}

