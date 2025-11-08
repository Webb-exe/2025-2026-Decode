package teamcode.robot.core

import kotlin.math.abs
import kotlin.math.max

/**
 * Minimal, robust PID controller.
 *
 * Features:
 * - Time-based calculate() (no need to pass dt) or explicit-dt overload
 * - Derivative-on-measurement to reduce noise-induced spikes
 * - Integral windup protection via clamp
 * - Anti-windup when output saturates
 * - Optional derivative filtering
 * - Optional output clamp
 * - Continuous input mode for wrap-around signals (e.g., heading 0..360)
 * - Direction restriction for continuous inputs (positive or negative only)
 */
enum class ContinuousDirection {
    NONE,           // Allow both directions (default)
    POSITIVE_ONLY,  // Only allow positive error/output (increase only)
    NEGATIVE_ONLY   // Only allow negative error/output (decrease only)
}

class PID(kP: Double, kI: Double, kD: Double) {
    // ----- Optional getters for telemetry -----
    // Gains
    var kP: Double = 0.0
        private set
    var kI: Double = 0.0
        private set
    var kD: Double = 0.0
        private set

    /** Set target setpoint.  */
    // State
    var setpoint: Double = 0.0
    var integral: Double = 0.0
        private set
    private var prevMeasurement = Double.Companion.NaN
    private var prevDerivative = 0.0
    private var prevTimeNanos = 0L

    /** Get the most recent error (setpoint - measurement). Useful for telemetry and tuning.  */
    // Current values (for telemetry)
    var error: Double = 0.0
        private set

    /** Get the most recent derivative term. Useful for telemetry and tuning.  */
    var derivative: Double = 0.0
        private set

    // Limits
    private var integralMin = Double.Companion.NEGATIVE_INFINITY
    private var integralMax = Double.Companion.POSITIVE_INFINITY
    private var outputMin = Double.Companion.NEGATIVE_INFINITY
    private var outputMax = Double.Companion.POSITIVE_INFINITY

    // Continuous input (e.g., angles)
    private var continuous = false
    private var minInput = 0.0
    private var maxInput = 360.0
    private var continuousDirection = ContinuousDirection.NONE
    private var reverseThreshold = 0.0 // Allow reverse if error < this threshold

    // Tolerance for atSetpoint()
    private var positionTolerance = 0.0
    private var velocityTolerance =
        Double.Companion.POSITIVE_INFINITY // optionally check derivative too

    // Acceptable error for isAtPosition() (default 0.0 means never at position)
    private var acceptableError = 0.0

    private var scaleFactor = 1.0

    // Derivative filtering (0 = no filter, 1 = max filter)
    private var derivativeFilterCoeff = 0.0

    // Anti-windup: stop integral accumulation when output is saturated
    private var enableAntiWindup = true

    init {
        setGains(kP, kI, kD)
    }

    fun setScaleFactor(scaleFactor: Double) {
        this.scaleFactor = scaleFactor
    }

    /** Change PID gains at runtime.  */
    fun setGains(kP: Double, kI: Double, kD: Double) {
        this.kP = kP
        this.kI = kI
        this.kD = kD
    }

    /** Optional: clamp integral accumulator to avoid windup.  */
    fun setIntegratorRange(min: Double, max: Double) {
        require(!(min > max)) { "min must be <= max" }
        this.integralMin = min
        this.integralMax = max
    }

    /** Optional: clamp controller output.  */
    fun setOutputRange(min: Double, max: Double) {
        require(!(min > max)) { "min must be <= max" }
        this.outputMin = min
        this.outputMax = max
    }

    /** Enable wrap-around for signals like heading. Example: setContinuousInput(0, 360).  */
    fun setContinuousInput(minInput: Double, maxInput: Double) {
        require(!(minInput >= maxInput)) { "minInput must be < maxInput" }
        this.continuous = true
        this.minInput = minInput
        this.maxInput = maxInput
    }

    /** Disable wrap-around behavior.  */
    fun disableContinuousInput() {
        this.continuous = false
    }

    /**
     * Set direction restriction for continuous inputs.
     * When enabled, the PID will only move in the specified direction (positive or negative).
     * This forces the controller to take the longer path if needed to maintain direction.
     * Only applies when continuous input is enabled.
     * @param direction POSITIVE_ONLY, NEGATIVE_ONLY, or NONE (default)
     * @param reverseThreshold If the absolute error in the opposite direction is less than this value,
     *                         allow going in the opposite direction (default: 0.0 = never allow reverse)
     */
    fun setContinuousDirection(direction: ContinuousDirection, reverseThreshold: Double = 0.0) {
        require(reverseThreshold >= 0.0) { "reverseThreshold must be >= 0" }
        this.continuousDirection = direction
        this.reverseThreshold = reverseThreshold
    }

    /** Set tolerances for atSetpoint(). Velocity tolerance is optional.  */
    fun setTolerance(positionTolerance: Double, velocityTolerance: Double) {
        this.positionTolerance = max(0.0, positionTolerance)
        this.velocityTolerance = max(0.0, velocityTolerance)
    }

    fun setTolerance(positionTolerance: Double) {
        setTolerance(positionTolerance, Double.Companion.POSITIVE_INFINITY)
    }

    /**
     * Set the acceptable error threshold for isAtPosition().
     * When the absolute error is less than or equal to this value, isAtPosition() returns true.
     * Default is 0.0, meaning isAtPosition() will never return true unless explicitly set.
     * @param acceptableError Maximum acceptable absolute error (must be >= 0)
     */
    fun setAcceptableError(acceptableError: Double) {
        require(acceptableError >= 0.0) { "acceptableError must be >= 0" }
        this.acceptableError = acceptableError
    }

    /**
     * Check if the PID controller is at position based on the current error.
     * Returns true if the absolute error is within the acceptable error threshold.
     * @return true if abs(error) <= acceptableError
     */
    fun isAtPosition(): Boolean {
        return abs(this.error) <= acceptableError
    }

    /**
     * Set derivative filtering coefficient to smooth noisy derivative signals.
     * @param alpha Filter coefficient: 0 = no filtering, 1 = maximum filtering (0.1-0.3 typical)
     * filtered_derivative = alpha * prev_derivative + (1-alpha) * raw_derivative
     */
    fun setDerivativeFilter(alpha: Double) {
        this.derivativeFilterCoeff = clamp(alpha, 0.0, 1.0)
    }

    /**
     * Enable or disable anti-windup. When enabled, integral stops accumulating when output saturates.
     * @param enable true to enable anti-windup (default), false to disable
     */
    fun setAntiWindup(enable: Boolean) {
        this.enableAntiWindup = enable
    }

    /** Reset integral and derivative state.  */
    fun reset() {
        integral = 0.0
        prevMeasurement = Double.Companion.NaN
        prevDerivative = 0.0
        prevTimeNanos = 0L
        this.error = 0.0
        this.derivative = 0.0
    }

    /**
     * Calculate output using time since last call. Call this at a regular loop rate (e.g., every 20ms).
     * @param measurement current process variable
     * @return PID output (clamped if output range set)
     */
    fun calculate(measurement: Double): Double {
        val now = System.nanoTime()
        var dt = 0.0
        if (prevTimeNanos != 0L) {
            dt = (now - prevTimeNanos) / 1e9 // seconds
        }
        prevTimeNanos = now

        // If first run (no dt), just compute proportional term.
        if (dt <= 0.0) {
            prevMeasurement = measurement
            this.error = getError(setpoint, measurement)
            this.derivative = 0.0
            // No integral or derivative on first pass
            var output = kP * this.error * scaleFactor
            
            // Apply direction restriction to output if continuous input is enabled
            // Allow reverse if within threshold
            if (continuous) {
                when (continuousDirection) {
                    ContinuousDirection.POSITIVE_ONLY -> {
                        if (!allowingReverse && output < 0.0) output = 0.0
                    }
                    ContinuousDirection.NEGATIVE_ONLY -> {
                        if (!allowingReverse && output > 0.0) output = 0.0
                    }
                    ContinuousDirection.NONE -> {
                        // No restriction
                    }
                }
            }
            
            return clamp(output, outputMin, outputMax)
        }

        return calculateWithDt(measurement, dt)
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
    fun calculate(measurement: Double, dtSec: Double): Double {
        if (dtSec <= 0.0) {
            // For zero or negative dt, just return proportional term without updating state
            val error = getError(setpoint, measurement)
            var output = kP * error * scaleFactor
            
            // Apply direction restriction to output if continuous input is enabled
            // Allow reverse if within threshold
            if (continuous) {
                when (continuousDirection) {
                    ContinuousDirection.POSITIVE_ONLY -> {
                        if (!allowingReverse && output < 0.0) output = 0.0
                    }
                    ContinuousDirection.NEGATIVE_ONLY -> {
                        if (!allowingReverse && output > 0.0) output = 0.0
                    }
                    ContinuousDirection.NONE -> {
                        // No restriction
                    }
                }
            }
            
            return clamp(output, outputMin, outputMax)
        }

        return calculateWithDt(measurement, dtSec)
    }

    /**
     * Internal method that performs the actual PID calculation given a valid dt.
     */
    private fun calculateWithDt(measurement: Double, dtSec: Double): Double {
        // Error with optional wrap-around
        val error = getError(setpoint, measurement)
        this.error = error

        // Calculate unscaled, unclamped output for anti-windup check
        val pTerm = kP * error
        val iTerm = kI * integral

        // Derivative on measurement (less noise than derivative on error when setpoint steps)
        var dMeas = 0.0
        if (!java.lang.Double.isNaN(prevMeasurement)) {
            dMeas = (measurement - prevMeasurement) / dtSec
            // Apply derivative filtering if enabled
            if (derivativeFilterCoeff > 0.0) {
                dMeas =
                    derivativeFilterCoeff * prevDerivative + (1.0 - derivativeFilterCoeff) * dMeas
            }
        }
        prevMeasurement = measurement
        prevDerivative = dMeas
        this.derivative = dMeas

        val dTerm = -kD * dMeas

        // Calculate output before scaling
        val outputBeforeScale = pTerm + iTerm + dTerm
        val outputBeforeClamp = outputBeforeScale * scaleFactor

        // Check if output would saturate
        val outputSaturated = outputBeforeClamp < outputMin || outputBeforeClamp > outputMax

        // Integral accumulation with anti-windup
        // Only accumulate integral if output is not saturated OR anti-windup is disabled
        if (!enableAntiWindup || !outputSaturated) {
            integral += error * dtSec
            integral = clamp(integral, integralMin, integralMax)
        }

        // If saturated and anti-windup enabled, don't update integral

        // Apply direction restriction to output if continuous input is enabled
        // The error has already been adjusted to the preferred direction, so output should match
        // Unless we're allowing reverse (within threshold)
        var finalOutput = outputBeforeClamp
        if (continuous) {
            when (continuousDirection) {
                ContinuousDirection.POSITIVE_ONLY -> {
                    // If allowing reverse (small error), allow negative output
                    // Otherwise, clamp negative outputs to 0 to ensure we only move forward
                    if (!allowingReverse && finalOutput < 0.0) {
                        finalOutput = 0.0
                    }
                }
                ContinuousDirection.NEGATIVE_ONLY -> {
                    // If allowing reverse (small error), allow positive output
                    // Otherwise, clamp positive outputs to 0 to ensure we only move backward
                    if (!allowingReverse && finalOutput > 0.0) {
                        finalOutput = 0.0
                    }
                }
                ContinuousDirection.NONE -> {
                    // No restriction
                }
            }
        }
        
        // Final output with clamp
        val output: Double = clamp(finalOutput, outputMin, outputMax)
        return output
    }

    /** True if current measurement is within tolerance of setpoint. Optionally checks velocity if available.  */
    fun atSetpoint(measurement: Double, measurementRate: Double): Boolean {
        val posErr = abs(getError(setpoint, measurement))
        val velOk = abs(measurementRate) <= velocityTolerance
        return posErr <= positionTolerance && velOk
    }

    fun atSetpoint(measurement: Double): Boolean {
        val posErr = abs(getError(setpoint, measurement))
        return posErr <= positionTolerance
    }

    // Track if we're allowing reverse (for output restriction)
    private var allowingReverse = false
    
    // ----- Helpers -----
    private fun getError(target: Double, measurement: Double): Double {
        var error = target - measurement
        allowingReverse = false
        
        if (continuous) {
            val range = maxInput - minInput
            // Wrap error into (-range/2, +range/2] to find shortest path
            var wrappedError = wrap(error, -range / 2.0, range / 2.0)
            
            // Apply direction restriction for continuous inputs
            // If target is in opposite direction, go the long way around
            // Unless the error is small enough (within reverseThreshold)
            when (continuousDirection) {
                ContinuousDirection.POSITIVE_ONLY -> {
                    if (wrappedError < 0.0) {
                        // Target is behind us
                        val absWrappedError = abs(wrappedError)
                        if (reverseThreshold > 0.0 && absWrappedError <= reverseThreshold) {
                            // Error is small enough, allow going backward
                            error = wrappedError
                            allowingReverse = true
                        } else {
                            // Go forward the long way
                            error = wrappedError + range
                        }
                    } else if (wrappedError == 0.0) {
                        // Already at target
                        error = 0.0
                    } else {
                        // Target is ahead, use normal error
                        error = wrappedError
                    }
                }
                ContinuousDirection.NEGATIVE_ONLY -> {
                    if (wrappedError > 0.0) {
                        // Target is ahead of us
                        val absWrappedError = abs(wrappedError)
                        if (reverseThreshold > 0.0 && absWrappedError <= reverseThreshold) {
                            // Error is small enough, allow going forward
                            error = wrappedError
                            allowingReverse = true
                        } else {
                            // Go backward the long way
                            error = wrappedError - range
                        }
                    } else if (wrappedError == 0.0) {
                        // Already at target
                        error = 0.0
                    } else {
                        // Target is behind, use normal error
                        error = wrappedError
                    }
                }
                ContinuousDirection.NONE -> {
                    // No restriction, use shortest path
                    error = wrappedError
                }
            }
        }
        return error
    }

    val pTerm: Double
        /** Get the proportional term contribution to the output.  */
        get() = kP * this.error

    val iTerm: Double
        /** Get the integral term contribution to the output.  */
        get() = kI * integral

    val dTerm: Double
        /** Get the derivative term contribution to the output.  */
        get() = -kD * this.derivative

    companion object {
        private fun wrap(value: Double, min: Double, max: Double): Double {
            val range = max - min
            if (range <= 0) return value
            var wrapped = (value - min) % range
            if (wrapped < 0) wrapped += range
            return wrapped + min
        }

        private fun clamp(v: Double, min: Double, max: Double): Double {
            if (v < min) return min
            if (v > max) return max
            return v
        }
    }
}