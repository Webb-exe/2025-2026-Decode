package teamcode.robot.core;

/**
 * Minimal, robust PID controller.
 *
 * Features:
 *  - Time-based calculate() (no need to pass dt) or explicit-dt overload
 *  - Derivative-on-measurement to reduce noise-induced spikes
 *  - Integral windup protection via clamp
 *  - Anti-windup when output saturates
 *  - Optional derivative filtering
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
    private double prevDerivative = 0.0;
    private long   prevTimeNanos   = 0L;

    // Current values (for telemetry)
    private double lastError = 0.0;
    private double lastDerivative = 0.0;

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

    private double scaleFactor = 1.0;
    
    // Derivative filtering (0 = no filter, 1 = max filter)
    private double derivativeFilterCoeff = 0.0;
    
    // Anti-windup: stop integral accumulation when output is saturated
    private boolean enableAntiWindup = true;

    public PID(double kP, double kI, double kD) {
        super();
        setGains(kP, kI, kD);
    }

    public void setScaleFactor(double scaleFactor) {
        this.scaleFactor = scaleFactor;
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
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max");
        }
        this.integralMin = min;
        this.integralMax = max;
    }

    /** Optional: clamp controller output. */
    public void setOutputRange(double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max");
        }
        this.outputMin = min;
        this.outputMax = max;
    }

    /** Enable wrap-around for signals like heading. Example: setContinuousInput(0, 360). */
    public void setContinuousInput(double minInput, double maxInput) {
        if (minInput >= maxInput) {
            throw new IllegalArgumentException("minInput must be < maxInput");
        }
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
    
    /**
     * Set derivative filtering coefficient to smooth noisy derivative signals.
     * @param alpha Filter coefficient: 0 = no filtering, 1 = maximum filtering (0.1-0.3 typical)
     *              filtered_derivative = alpha * prev_derivative + (1-alpha) * raw_derivative
     */
    public void setDerivativeFilter(double alpha) {
        this.derivativeFilterCoeff = clamp(alpha, 0.0, 1.0);
    }
    
    /**
     * Enable or disable anti-windup. When enabled, integral stops accumulating when output saturates.
     * @param enable true to enable anti-windup (default), false to disable
     */
    public void setAntiWindup(boolean enable) {
        this.enableAntiWindup = enable;
    }

    /** Reset integral and derivative state. */
    public void reset() {
        integral = 0.0;
        prevMeasurement = Double.NaN;
        prevDerivative = 0.0;
        prevTimeNanos = 0L;
        lastError = 0.0;
        lastDerivative = 0.0;
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
            lastError = getError(setpoint, measurement);
            lastDerivative = 0.0;
            // No integral or derivative on first pass
            double output = kP * lastError;
            return clamp(output * scaleFactor, outputMin, outputMax);
        }

        return calculateWithDt(measurement, dt);
    }

    /**
     * Calculate output with an explicit dt.
     * Uses derivative-on-measurement: D = -kD * (d(measurement)/dt)
     * WARNING: This method shares state with the time-based calculate() method.
     * Do NOT mix both methods unless you understand the state implications.
     * @param measurement current process variable
     * @param dtSec delta time in seconds (must be > 0)
     * @return PID output (clamped if output range set)
     */
    public double calculate(double measurement, double dtSec) {
        if (dtSec <= 0.0) {
            // For zero or negative dt, just return proportional term without updating state
            double error = getError(setpoint, measurement);
            double output = kP * error;
            return clamp(output * scaleFactor, outputMin, outputMax);
        }

        return calculateWithDt(measurement, dtSec);
    }

    /**
     * Internal method that performs the actual PID calculation given a valid dt.
     */
    private double calculateWithDt(double measurement, double dtSec) {
        // Error with optional wrap-around
        double error = getError(setpoint, measurement);
        lastError = error;

        // Calculate unscaled, unclamped output for anti-windup check
        double pTerm = kP * error;
        double iTerm = kI * integral;
        
        // Derivative on measurement (less noise than derivative on error when setpoint steps)
        double dMeas = 0.0;
        if (!Double.isNaN(prevMeasurement)) {
            dMeas = (measurement - prevMeasurement) / dtSec;
            // Apply derivative filtering if enabled
            if (derivativeFilterCoeff > 0.0) {
                dMeas = derivativeFilterCoeff * prevDerivative + (1.0 - derivativeFilterCoeff) * dMeas;
            }
        }
        prevMeasurement = measurement;
        prevDerivative = dMeas;
        lastDerivative = dMeas;
        
        double dTerm = -kD * dMeas;

        // Calculate output before scaling
        double outputBeforeScale = pTerm + iTerm + dTerm;
        double outputBeforeClamp = outputBeforeScale * scaleFactor;
        
        // Check if output would saturate
        boolean outputSaturated = outputBeforeClamp < outputMin || outputBeforeClamp > outputMax;

        // Integral accumulation with anti-windup
        // Only accumulate integral if output is not saturated OR anti-windup is disabled
        if (!enableAntiWindup || !outputSaturated) {
            integral += error * dtSec;
            integral = clamp(integral, integralMin, integralMax);
        }
        // If saturated and anti-windup enabled, don't update integral

        // Final output with clamp
        double output = clamp(outputBeforeClamp, outputMin, outputMax);
        return output;
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
    
    /** Get the most recent error (setpoint - measurement). Useful for telemetry and tuning. */
    public double getError() { return lastError; }
    
    /** Get the most recent derivative term. Useful for telemetry and tuning. */
    public double getDerivative() { return lastDerivative; }
    
    /** Get the proportional term contribution to the output. */
    public double getPTerm() { return kP * lastError; }
    
    /** Get the integral term contribution to the output. */
    public double getITerm() { return kI * integral; }
    
    /** Get the derivative term contribution to the output. */
    public double getDTerm() { return -kD * lastDerivative; }
}