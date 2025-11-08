package teamcode.robot.subsystems

import com.bylazar.configurables.annotations.Configurable
import teamcode.robot.core.ContinuousDirection
import teamcode.robot.core.PID
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.subsystem.Subsystem
import teamcode.robot.core.utils.degreesToTicks

@Configurable
object SpindexerConfig {
    @JvmField
    var motorGearCount: Int = 22
    @JvmField
    var mainGearCount: Int = 142
    @JvmField
    var tickPerRevolution: Double = 537.7
    @JvmField
    var maxPower: Double = 0.8
    @JvmField
    var acceptedError: Int = 10
    @JvmField
    var positions: Int = 3
    @JvmField
    var kp: Double = 0.8
    @JvmField
    var ki: Double = 0.0
    @JvmField
    var kd: Double = 0.01
    @JvmField
    var pidScale: Double = 0.01
}

enum class SpindexerState {
    IDLE,
    SPINNING,
}

class SpindexerSubsystem: Subsystem("Spindexer", 20) {
    @Volatile
    var currentState: SpindexerState = SpindexerState.IDLE
        private set

    var isEnabled = true

    private lateinit var pid: PID

    private var leftTrigger = 0.0;
    private var rightTrigger = 0.0;


    private var degrees: Double = 0.0
        set(value){
            field=value%360.0
        }
    
    /**
     * Set the target position in degrees (0-360)
     */
    fun setTargetDegrees(targetDegrees: Double) {
        degrees = targetDegrees
        currentState = SpindexerState.SPINNING
    }
    
    /**
     * Set the target position by position index (0 to positions-1)
     */
    fun setTargetPosition(position: Int) {
        require(position in 0 until SpindexerConfig.positions) {
            "Position must be between 0 and ${SpindexerConfig.positions - 1}"
        }
        val targetDegrees = (position * 360.0) / SpindexerConfig.positions
        setTargetDegrees(targetDegrees)
    }

    fun changeTargetPositionByOffset(offset: Int) {
        val currentPosition = ((degrees / 360.0) * SpindexerConfig.positions).toInt()
        val newPosition = (currentPosition + offset).mod(SpindexerConfig.positions)
        setTargetPosition(newPosition)
    }

    fun updateGamepad(left:Double,right:Double){
        leftTrigger=left
        rightTrigger=right
    }

    /**
     * Calculate the gear ratio: mainGearCount / motorGearCount
     * This represents how many motor rotations are needed for one spindexer rotation
     */
    private fun getGearRatio(): Double {
        return SpindexerConfig.mainGearCount.toDouble() / SpindexerConfig.motorGearCount.toDouble()
    }

    /**
     * Calculate the number of motor ticks per spindexer revolution
     * Accounts for gear ratio: one spindexer revolution requires gearRatio motor revolutions
     */
    private fun getTicksPerSpindexerRevolution(): Double {
        return SpindexerConfig.tickPerRevolution * getGearRatio()
    }

    override fun init() {
        pid = PID(SpindexerConfig.kp, SpindexerConfig.ki, SpindexerConfig.kd)
        pid.setOutputRange(-SpindexerConfig.maxPower, SpindexerConfig.maxPower)
        pid.setTolerance(SpindexerConfig.acceptedError.toDouble())
        pid.setAcceptableError(SpindexerConfig.acceptedError.toDouble())
        pid.setpoint = 0.0
        pid.setScaleFactor(SpindexerConfig.pidScale)
        // Enable continuous input for wrap-around behavior (0 to ticksPerSpindexerRevolution)
        // This ensures the PID automatically chooses the shorter rotation path
        pid.setContinuousInput(0.0, getTicksPerSpindexerRevolution())
        // Restrict to positive direction only (increase only, no backward movement)
        pid.setContinuousDirection(ContinuousDirection.POSITIVE_ONLY,300.0)
    }

    /**
     * Normalize ticks to the range [0, ticksPerSpindexerRevolution)
     * Accounts for gear ratio: one spindexer revolution = tickPerRevolution * (mainGearCount / motorGearCount)
     */
    internal fun normalizeTicks(ticks: Double): Double {
        val ticksPerSpindexerRevolution = getTicksPerSpindexerRevolution()
        var normalized = ticks % ticksPerSpindexerRevolution
        if (normalized < 0) {
            normalized += ticksPerSpindexerRevolution
        }
        return normalized
    }
    
    /**
     * Convert degrees to motor ticks, accounting for gear ratio
     * The motor needs to rotate (degrees * gearRatio) to rotate the spindexer by degrees
     */
    private fun degreesToMotorTicks(degrees: Double): Double {
        val motorDegrees = degrees * getGearRatio()
        return degreesToTicks(motorDegrees, SpindexerConfig.tickPerRevolution)
    }

    /**
     * Calculate target ticks for a given position index (0 to positions-1)
     */
    internal fun calculateTicksForPosition(position: Int): Int {
        require(position in 0 until SpindexerConfig.positions) {
            "Position must be between 0 and ${SpindexerConfig.positions - 1}"
        }
        val targetDegrees = (position * 360.0) / SpindexerConfig.positions
        val motorTicks = degreesToMotorTicks(targetDegrees)
        return normalizeTicks(motorTicks).toInt()
    }

    override fun periodic() {
         if (!isEnabled || current<ShooterSubsystem>().currentState!==ShooterState.IDLE){
             currentState = SpindexerState.IDLE
             RobotHardware.spindexterMotor.set(0.0)
             return
         }

         if (leftTrigger!=0.0){
             currentState = SpindexerState.SPINNING
             RobotHardware.spindexterMotor.set(leftTrigger)
         }
         else if(rightTrigger!=0.0){
             currentState = SpindexerState.SPINNING
             RobotHardware.spindexterMotor.set(-rightTrigger)
         }
         else{
             currentState = SpindexerState.IDLE
             RobotHardware.spindexterMotor.set(0.0)
         }

        
       // Get current motor position and normalize
       val currentTicks = RobotHardware.spindexterMotor.currentPosition.toDouble()
       val normalizedCurrentTicks = normalizeTicks(currentTicks)

       // Convert target degrees to motor ticks and normalize
       val targetTicks = degreesToMotorTicks(degrees)
       val normalizedTargetTicks = normalizeTicks(targetTicks)

       // Update PID setpoint
       pid.setpoint = normalizedTargetTicks

       // Calculate PID output (PID will automatically choose shorter path due to continuous input)
       // The continuous input mode wraps error to (-range/2, +range/2], ensuring shortest path
       val output = pid.calculate(normalizedCurrentTicks)
       telemetry.addData("output",output)

       // Set motor power

       // Update state based on whether we're at target
       if (pid.isAtPosition()) {
           currentState = SpindexerState.IDLE
           RobotHardware.spindexterMotor.set(0.0)
       }else{
           currentState = SpindexerState.SPINNING
           RobotHardware.spindexterMotor.set(output)
       }
    }

    override fun end() {
        currentState = SpindexerState.IDLE
        RobotHardware.spindexterMotor.set(0.0)
    }

    override fun updateTelemetry() {
        val currentTicks = RobotHardware.spindexterMotor.currentPosition.toDouble()
        val normalizedCurrentTicks = normalizeTicks(currentTicks)
        val targetTicks = degreesToMotorTicks(degrees)
        val normalizedTargetTicks = normalizeTicks(targetTicks)
        
        telemetry.addData("State", currentState.name)
        telemetry.addData("Target Degrees", String.format("%.2f", degrees))
        telemetry.addData("Current Ticks", String.format("%.2f", normalizedCurrentTicks))
        telemetry.addData("Absolute Ticks", currentTicks)
        telemetry.addData("Target Ticks", String.format("%.2f", normalizedTargetTicks))
        telemetry.addData("Error", String.format("%.2f", pid.error))
        telemetry.addData("At Position", pid.isAtPosition())
        telemetry.addData("Motor Power", String.format("%.2f", RobotHardware.spindexterMotor.get()))
    }
}