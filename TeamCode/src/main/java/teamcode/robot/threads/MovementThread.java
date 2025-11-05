package teamcode.robot.threads;

import static java.lang.Math.abs;

import com.qualcomm.robotcore.hardware.Gamepad;
import teamcode.robot.core.RobotHardware;
import teamcode.threading.RobotThread;

/**
 * Thread for controlling mecanum drive movement.
 * Handles gamepad input and applies mecanum kinematics to control the robot's movement.
 */
public class MovementThread extends RobotThread {
    // Movement control variables
    private volatile double driveY = 0.0;     // Forward/backward
    private volatile double driveX = 0.0;     // Strafe left/right
    private volatile double driveRot = 0.0;   // Rotation
    private volatile double speedMultiplier = 1.0;
    private volatile boolean enabled = true;
    
    // Field-centric mode
    private volatile boolean fieldCentric = false;
    private volatile double robotHeading = 0.0; // In radians
    
    public MovementThread() {
        super("MovementThread", 5); // 200Hz update rate
    }
    
    @Override
    protected void onStart() {
        // Initialize drive motors to brake mode (optional)
        if (RobotHardware.leftFront != null) {
            RobotHardware.leftFront.setZeroPowerBehavior(com.qualcomm.robotcore.hardware.DcMotor.ZeroPowerBehavior.BRAKE);
            RobotHardware.leftBack.setZeroPowerBehavior(com.qualcomm.robotcore.hardware.DcMotor.ZeroPowerBehavior.BRAKE);
            RobotHardware.rightFront.setZeroPowerBehavior(com.qualcomm.robotcore.hardware.DcMotor.ZeroPowerBehavior.BRAKE);
            RobotHardware.rightBack.setZeroPowerBehavior(com.qualcomm.robotcore.hardware.DcMotor.ZeroPowerBehavior.BRAKE);
        }
    }
    
    @Override
    protected void runLoop() {
        if (!enabled || RobotHardware.leftFront == null) {
            // Update telemetry even when disabled
            telemetry.addData("Status", "Disabled");
            telemetry.addData("Enabled", false);
            return;
        }
        
        // Get current drive values (thread-safe with volatile)
        double y = driveY;
        double x = driveX;
        double rot = driveRot;
        
        // Apply field-centric transformation if enabled
        if (fieldCentric) {
            double heading = robotHeading;
            double temp = y * Math.cos(-heading) - x * Math.sin(-heading);
            x = y * Math.sin(-heading) + x * Math.cos(-heading);
            y = temp;
        }
        
        // Scaled Mecanum kinematics with theta+turn scaling
        double turn = rot * RobotConfig.TurnScale;

        double theta = Math.atan2(y, x);
        double power = Math.hypot(x, y) * RobotConfig.PowerScale;

        double sin = Math.sin(theta - Math.PI / 4);
        double cos = Math.cos(theta - Math.PI / 4);
        double max = Math.max(Math.abs(sin), Math.abs(cos));

        double leftFrontPower = power * cos / max + turn;
        double rightFrontPower = power * sin / max - turn;
        double leftBackPower = power * sin / max + turn;
        double rightBackPower = power * cos / max - turn;

        // Normalize speeds if total demand is over 1.0
        if ((power + Math.abs(turn)) > 1) {
            leftFrontPower /= (power + Math.abs(turn));
            rightFrontPower /= (power + Math.abs(turn));
            leftBackPower /= (power + Math.abs(turn));
            rightBackPower /= (power + Math.abs(turn));
        }
        
        // Apply speed multiplier
        leftFrontPower *= speedMultiplier;
        rightFrontPower *= speedMultiplier;
        leftBackPower *= speedMultiplier;
        rightBackPower *= speedMultiplier;
        
        // Set motor powers
        RobotHardware.leftFront.setPower(leftFrontPower);
        RobotHardware.rightFront.setPower(rightFrontPower);
        RobotHardware.leftBack.setPower(leftBackPower);
        RobotHardware.rightBack.setPower(rightBackPower);
        
        // Update telemetry - data persists until next update from this thread
        telemetry.addData("Status", "Running");
        telemetry.addData("Enabled", true);
        telemetry.addData("Speed Multiplier", String.format("%.1f%%", speedMultiplier * 100));
        telemetry.addData("Field Centric", fieldCentric);
        telemetry.addData("Drive Y", String.format("%.2f", driveY));
        telemetry.addData("Drive X", String.format("%.2f", driveX));
        telemetry.addData("Drive Rot", String.format("%.2f", driveRot));
        telemetry.addData("LF Power", String.format("%.2f", leftFrontPower));
        telemetry.addData("RF Power", String.format("%.2f", rightFrontPower));
        telemetry.addData("LB Power", String.format("%.2f", leftBackPower));
        telemetry.addData("RB Power", String.format("%.2f", rightBackPower));
    }
    
    /**
     * Update drive values from gamepad input
     * Call this from your main OpMode thread
     */
    public void setDriveInput(Gamepad gamepad) {
        if (gamepad == null) return;
        
        // Standard gamepad controls:
        // Left stick Y = forward/backward
        // Left stick X = strafe left/right
        // Right stick X = rotation
        this.setDriveValues(-gamepad.left_stick_y, gamepad.left_stick_x, gamepad.right_stick_x);
    }
    
    /**
     * Manually set drive values (for autonomous or custom control)
     * Applies cubic-style scaling for smoother control, but keeps sign and zero checks clear.
     * Uses the same power function for all axes and short-circuits for zeros.
     */
    public void setDriveValues(double y, double x, double rotation) {
        this.driveY = scaleInput(y, 1.8,1.8);
        this.driveX = scaleInput(x, 1.8,1.8);
        this.driveRot = scaleInput(rotation, 2.0,1.2);
    }

    /**
     * Scales joystick input while preserving the sign.
     * For zero input, returns zero directly.
     */
    private double scaleInput(double input, double exponent,double linear) {
        if (input == 0.0) return 0.0;
        return Math.copySign(Math.pow(Math.abs(input), exponent), input)*linear;
    }
    /**
     * Set speed multiplier (0.0 to 1.0)
     * Useful for precision mode vs speed mode
     */
    public void setSpeedMultiplier(double multiplier) {
        this.speedMultiplier = Math.max(0.0, Math.min(1.0, multiplier));
    }
    
    /**
     * Get current speed multiplier
     */
    public double getSpeedMultiplier() {
        return speedMultiplier;
    }
    
    /**
     * Enable field-centric drive mode
     * Requires IMU heading to be updated via setRobotHeading()
     */
    public void setFieldCentric(boolean enabled) {
        this.fieldCentric = enabled;
    }
    
    /**
     * Check if field-centric mode is enabled
     */
    public boolean isFieldCentric() {
        return fieldCentric;
    }
    
    /**
     * Update robot heading for field-centric drive
     * @param heading Robot heading in radians
     */
    public void setRobotHeading(double heading) {
        this.robotHeading = heading;
    }
    
    /**
     * Enable drive control
     */
    public void enable() {
        this.enabled = true;
    }
    
    /**
     * Disable drive control (stops all motors)
     */
    public void disable() {
        this.enabled = false;
        stopMotors();
    }
    
    /**
     * Check if drive control is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Stop all drive motors
     */
    private void stopMotors() {
        if (RobotHardware.leftFront != null) {
            RobotHardware.leftFront.setPower(0.0);
            RobotHardware.rightFront.setPower(0.0);
            RobotHardware.leftBack.setPower(0.0);
            RobotHardware.rightBack.setPower(0.0);
        }
    }
    
    @Override
    protected void onStop() {
        stopMotors();
    }
}
