package teamcode.robot.threads;

import teamcode.robot.core.PID;
import teamcode.robot.core.RobotConfig;
import teamcode.robot.core.RobotHardware;
import teamcode.threading.RobotThread;

/**
 * Thread for controlling turret rotation using PID control based on vision data.
 * Uses targetX from VisionThread to keep the target centered.
 */
public class TurretThread extends RobotThread {
    private PID turretPID;
    private VisionThread visionThread;
    private volatile boolean enabled = false;
    
    public TurretThread() {
        super("TurretThread", 10); // 100Hz update rate
    }
    
    /**
     * Set the VisionThread reference for vision-based PID control.
     */
    public void setVisionThread(VisionThread visionThread) {
        this.visionThread = visionThread;
    }
    
    @Override
    protected void onStart() {
        turretPID = new PID(RobotConfig.TurretPGain, RobotConfig.TurretIGain, RobotConfig.TurretDGain);
        turretPID.setOutputRange(-RobotConfig.TurretSpeedClamp,RobotConfig.TurretSpeedClamp); // Motor power range
        turretPID.setIntegratorRange(-0.5, 0.5); // Prevent integral windup
        turretPID.setScaleFactor(RobotConfig.TurretScaleFactor);
        turretPID.reset();
        // Setpoint is 0.0 to center the target (targetX = 0 means centered)
        turretPID.setSetpoint(0.0);
    }
    
    @Override
    protected void runLoop() {
        if (!enabled) {
            telemetry.addData("Status", "Disabled");
            telemetry.addData("Enabled", false);
            telemetry.addData("Motor Power", 0.0);
            return;
        }
        
        if (RobotHardware.turretTurnMotor == null) {
            telemetry.addData("Status", "No Motor");
            telemetry.addData("Enabled", true);
            return;
        }
        
        if (visionThread == null) {
            telemetry.addData("Status", "No Vision Thread");
            telemetry.addData("Enabled", true);
            RobotHardware.turretTurnMotor.setPower(0.0);
            return;
        }
        
        // Only control if vision has targets detected
        if (!visionThread.hasTargets()) {
            RobotHardware.turretTurnMotor.setPower(0.0);
            telemetry.addData("Status", "No Targets");
            telemetry.addData("Enabled", true);
            telemetry.addData("Motor Power", 0.0);
            telemetry.addData("Target Found", false);
            return;
        }
        
        // Get targetX from vision thread (horizontal offset from crosshair in degrees)
        // Negative targetX means target is to the left, positive means right
        VisionThread.AprilTag aprilTag = visionThread.getAprilTag(24);
        if (aprilTag==null){
            RobotHardware.turretTurnMotor.setPower(0.0);
            telemetry.addData("Status", "No AprilTag 24");
            telemetry.addData("Enabled", true);
            telemetry.addData("Motor Power", 0.0);
            telemetry.addData("Target Found", false);
            return;
        }
        
        // Calculate PID output based on vision data
        // Setpoint is 0.0 (centered), measurement is targetX
        double output = turretPID.calculate(aprilTag.xDegrees);
        
        // Apply output to motor
        RobotHardware.turretTurnMotor.setPower(output);
        
        // Update telemetry - data persists until next update from this thread
        telemetry.addData("Status", "Tracking");
        telemetry.addData("Enabled", true);
        telemetry.addData("Target Found", true);
        telemetry.addData("Target ID", aprilTag.id);
        telemetry.addData("Target X", String.format("%.2f°", aprilTag.xDegrees));
        telemetry.addData("Target Y", String.format("%.2f°", aprilTag.yDegrees));
        telemetry.addData("Motor Power", String.format("%.2f", output));
        telemetry.addData("Motor Position", RobotHardware.turretTurnMotor.getCurrentPosition());
        telemetry.addData("PID Error", String.format("%.2f°", aprilTag.xDegrees));
    }
    
    /**
     * Enable turret control
     */
    public void enable() {
        this.enabled = true;
    }
    
    /**
     * Disable turret control (stops motor)
     */
    public void disable() {
        this.enabled = false;
        if (RobotHardware.turretTurnMotor != null) {
            RobotHardware.turretTurnMotor.setPower(0.0);
        }
    }
    
    /**
     * Check if turret control is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Reset PID controller
     */
    public void resetPID() {
        if (turretPID != null) {
            turretPID.reset();
        }
    }
    
    /**
     * Set PID gains
     */
    public void setPIDGains(double kP, double kI, double kD) {
        if (turretPID != null) {
            turretPID.setGains(kP, kI, kD);
        }
    }
    
    @Override
    protected void onStop() {
        disable();
    }
}

