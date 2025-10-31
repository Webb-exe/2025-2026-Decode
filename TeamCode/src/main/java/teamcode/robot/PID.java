package teamcode.robot;

/**
 * Minimal, robust PID controller.
 *
 * Features:
 *  - Time-based calculate() (no need to pass dt) or explicit-dt overload
 *  - Derivative-on-measurement to reduce noise-induced spikes
 *  - Integral windup protection via clamp
 *  - Optional output clamp
 *  - Continuous input mode for wrap-around signals (e.g., heading 0..360)
 */
public class PID {

    // Gains
    private double kP, kI, kD;

    // State
    private double setpoint = 0.0;
    private double integral = 0.0;
    private double prevMeasurement = Double.NaN;
    private long   prevTimeNanos   = 0L;

    // Limits
    private double integralMin = Double.NEGATIVE_INFINITY;
    private double integralMax = Double.POSITIVE_INFINITY;
    private double outputMin   = Double.NEGATIVE_INFINITY;
    private double outputMax   = Double.POSITIVE_INFINITY;

    // Continuous input (e.g., angles)
    private boolean continuous = false;
    private double  minInput   = 0.0;
    private double  maxInput   = 360.0;

    // Tolerance for atSetpoint()
    private double positionTolerance = 0.0;
    private double velocityTolerance = Double.POSITIVE_INFINITY; // optionally check derivative too

    public PID(double kP, double kI, double kD) {
        super();
        setGains(kP, kI, kD);
    }

    /** Change PID gains at runtime. */
    public void setGains(double kP, double kI, double kD) {
        this.kP = kP;
        this.kI = kI;
        this.kD = kD;
    }

    /** Set target setpoint. */
    public void setSetpoint(double setpoint) {
        this.setpoint = setpoint;
    }

    public double getSetpoint() {
        return setpoint;
    }

    /** Optional: clamp integral accumulator to avoid windup. */
    public void setIntegratorRange(double min, double max) {
        this.integralMin = min;
        this.integralMax = max;
    }

    /** Optional: clamp controller output. */
    public void setOutputRange(double min, double max) {
        this.outputMin = min;
        this.outputMax = max;
    }

    /** Enable wrap-around for signals like heading. Example: setContinuousInput(0, 360). */
    public void setContinuousInput(double minInput, double maxInput) {
        this.continuous = true;
        this.minInput = minInput;
        this.maxInput = maxInput;
    }

    /** Disable wrap-around behavior. */
    public void disableContinuousInput() {
        this.continuous = false;
    }

    /** Set tolerances for atSetpoint(). Velocity tolerance is optional. */
    public void setTolerance(double positionTolerance, double velocityTolerance) {
        this.positionTolerance = Math.max(0.0, positionTolerance);
        this.velocityTolerance = Math.max(0.0, velocityTolerance);
    }

    public void setTolerance(double positionTolerance) {
        setTolerance(positionTolerance, Double.POSITIVE_INFINITY);
    }

    /** Reset integral and derivative state. */
    public void reset() {
        integral = 0.0;
        prevMeasurement = Double.NaN;
        prevTimeNanos = 0L;
    }

    /**
     * Calculate output using time since last call. Call this at a regular loop rate (e.g., every 20ms).
     * @param measurement current process variable
     * @return PID output (clamped if output range set)
     */
    public double calculate(double measurement) {
        final long now = System.nanoTime();
        double dt = 0.0;
        if (prevTimeNanos != 0L) {
            dt = (now - prevTimeNanos) / 1e9; // seconds
        }
        prevTimeNanos = now;

        // If first run (no dt), just compute proportional term.
        if (dt <= 0.0) {
            prevMeasurement = measurement;
            // No integral or derivative on first pass
            double error = getError(setpoint, measurement);
            double output = kP * error;
            return clamp(output, outputMin, outputMax);
        }

        return calculateWithDt(measurement, dt);
    }

    /**
     * Calculate output with an explicit dt.
     * Uses derivative-on-measurement: D = -kD * (d(measurement)/dt)
     * Note: This method does NOT update the internal time tracking, so it can be mixed
     * with time-based calculate() calls without interfering with time tracking.
     * @param measurement current process variable
     * @param dtSec delta time in seconds (must be > 0)
     * @return PID output (clamped if output range set)
     */
    public double calculate(double measurement, double dtSec) {
        if (dtSec <= 0.0) {
            // For zero or negative dt, just return proportional term
            double error = getError(setpoint, measurement);
            double output = kP * error;
            return clamp(output, outputMin, outputMax);
        }

        return calculateWithDt(measurement, dtSec);
    }

    /**
     * Internal method that performs the actual PID calculation given a valid dt.
     */
    private double calculateWithDt(double measurement, double dtSec) {
        // Error with optional wrap-around
        double error = getError(setpoint, measurement);

        // Integral with windup clamp
        integral += error * dtSec;
        integral = clamp(integral, integralMin, integralMax);

        // Derivative on measurement (less noise than derivative on error when setpoint steps)
        double dMeas = 0.0;
        if (!Double.isNaN(prevMeasurement)) {
            dMeas = (measurement - prevMeasurement) / dtSec;
        }
        prevMeasurement = measurement;

        double output = (kP * error) + (kI * integral) - (kD * dMeas);
        return clamp(output, outputMin, outputMax);
    }

    /** True if current measurement is within tolerance of setpoint. Optionally checks velocity if available. */
    public boolean atSetpoint(double measurement, double measurementRate) {
        double posErr = Math.abs(getError(setpoint, measurement));
        boolean velOk = Math.abs(measurementRate) <= velocityTolerance;
        return posErr <= positionTolerance && velOk;
    }

    public boolean atSetpoint(double measurement) {
        double posErr = Math.abs(getError(setpoint, measurement));
        return posErr <= positionTolerance;
    }

    // ----- Helpers -----

    private double getError(double target, double measurement) {
        double error = target - measurement;
        if (continuous) {
            double range = maxInput - minInput;
            // Wrap error into (-range/2, +range/2]
            error = wrap(error, -range / 2.0, range / 2.0);
        }
        return error;
    }

    private static double wrap(double value, double min, double max) {
        double range = max - min;
        if (range <= 0) return value;
        double wrapped = (value - min) % range;
        if (wrapped < 0) wrapped += range;
        return wrapped + min;
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    // ----- Optional getters for telemetry -----
    public double getKP() { return kP; }
    public double getKI() { return kI; }
    public double getKD() { return kD; }
    public double getIntegral() { return integral; }
}