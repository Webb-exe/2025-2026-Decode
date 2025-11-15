package teamcode.robot.command

import teamcode.robot.core.subsystem.Subsystem
import teamcode.robot.core.utils.Timer
import teamcode.threading.RobotThread

/**
 * Base class for all robot commands.
 * 
 * Commands execute on the CommandScheduler thread and call subsystem methods
 * directly across threads. Subsystems run on their own threads.
 * 
 * Design principles:
 * - Commands are simple to write
 * - Use current<SubsystemType>() to get subsystem references
 * - Subsystem method calls happen directly (cross-thread)
 * - Subsystems must ensure thread-safety for public methods
 * - Command lifecycle is managed by CommandScheduler
 * 
 * Example:
 * ```
 * class DriveForward(val seconds: Double) : Command() {
 *     private val drive = current<DriveSubsystem>()
 *     private val timer = Timer()
 *     
 *     override fun initialize() {
 *         timer.start()
 *     }
 *     
 *     override fun periodic() {
 *         drive.setPower(0.5, 0.5)  // Direct cross-thread call
 *     }
 *     
 *     override fun isFinished(): Boolean {
 *         return timer.elapsedSeconds() > seconds
 *     }
 *     
 *     override fun end(interrupted: Boolean) {
 *         drive.setPower(0.0, 0.0)
 *     }
 * }
 * ```
 */
abstract class Command(interruptibleInput: Boolean=true) {

    
    /**
     * Whether this command can be interrupted by other commands.
     * 
     * If false, attempting to schedule a conflicting command will fail
     * instead of interrupting this command.
     * 
     * Default is true (commands are interruptible).
     * 
     * Set to false for critical commands that must complete:
     * ```
     * class CriticalCommand : Command() {
     *     init {
     *         interruptible = false
     *     }
     * }
     * ```
     */
    var interruptible: Boolean = true

    var finishFn: Boolean =false
    
    /**
     * Internal tracking of which subsystems this command requires.
     * Automatically populated when current<T>() is called.
     */
    internal val requirements: MutableSet<Class<out Subsystem>> = mutableSetOf()

    init {
            interruptible = interruptibleInput

    }
    
    /**
     * Get a direct reference to a subsystem.
     * 
     * This method:
     * 1. Gets the subsystem instance from RobotThread.current<T>()
     * 2. Automatically adds the subsystem to this command's requirements
     * 3. Returns the subsystem reference
     * 
     * Commands call subsystem methods directly (cross-thread).
     * Subsystems must ensure thread-safety for all public methods.
     * 
     * Usage:
     * ```
     * private val drive = current<DriveSubsystem>()
     * 
     * override fun periodic() {
     *     drive.setPower(0.5, 0.5)  // Direct cross-thread call
     * }
     * 
     * override fun isFinished(): Boolean {
     *     return drive.currentState == DriveState.IDLE  // Direct read
     * }
     * ```
     * 
     * @return Direct reference to the subsystem
     */
    internal inline fun <reified T : Subsystem> current(): T {
        val subsystemClass = T::class.java
        
        // Get real subsystem instance
        val subsystem = RobotThread.current<T>()
        
        // Track this subsystem as a requirement
        requirements.add(subsystemClass)
        
        return subsystem
    }

    protected fun finishCommand(){
        finishFn=true
    }
    
    /**
     * Called once when the command is initially scheduled.
     * Override to implement initialization logic.
     */
    open fun initialize() {}
    
    /**
     * Called repeatedly while the command is scheduled.
     * Override to implement the main command logic.
     */
    open fun periodic() {}
    
    /**
     * Called once when the command ends, either because isFinished() returned true
     * or because the command was interrupted/canceled.
     * 
     * @param interrupted true if the command was interrupted, false if it finished normally
     */
    open fun end(interrupted: Boolean) {}
    
    /**
     * Returns whether the command has finished executing.
     * When this returns true, the scheduler will call end(false) and remove the command.
     * 
     * @return true if the command is finished, false otherwise
     */
    open fun isFinished(): Boolean = false

    internal fun isFinishedInternal(): Boolean{
        val result = isFinished()
        return  (result || finishFn)
    }
}

