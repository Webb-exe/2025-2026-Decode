package teamcode.robot;

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
        super("TurretThread", 20); // 50Hz update rate
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
        turretPID.setOutputRange(-0.5, 0.5); // Motor power range
        turretPID.setIntegratorRange(-0.5, 0.5); // Prevent integral windup
        turretPID.reset();
        // Setpoint is 0.0 to center the target (targetX = 0 means centered)
        turretPID.setSetpoint(0.0);
    }
    
    @Override
    protected void runLoop() {
        if (!enabled || RobotHardware.turretTurnMotor == null || visionThread == null) {
            return;
        }
        
        // Only control if vision has targets detected
        if (!visionThread.hasTargets()) {
            RobotHardware.turretTurnMotor.setPower(0.0);
            return;
        }
        
        // Get targetX from vision thread (horizontal offset from crosshair in degrees)
        // Negative targetX means target is to the left, positive means right
        VisionThread.AprilTag aprilTag = visionThread.getAprilTag(24);
        if (aprilTag==null){
            RobotHardware.turretTurnMotor.setPower(0.0);
            return;
        }
        
        // Calculate PID output based on vision data
        // Setpoint is 0.0 (centered), measurement is targetX
        double output = turretPID.calculate(aprilTag.xDegrees);
        
        // Apply output to motor
        RobotHardware.turretTurnMotor.setPower(output);
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

