package teamcode.threading;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;

import teamcode.robot.RobotHardware;

/**
 * Base class for OpModes that use threading.
 * Extends LinearOpMode and provides thread management functionality.
 */
public abstract class ThreadedOpMode extends LinearOpMode {
    protected ThreadManager threadManager;
    protected ElapsedTime runtime;
    
    @Override
    public final void runOpMode() {
        runtime = new ElapsedTime();
        threadManager = new ThreadManager();
        
        // Initialize robot hardware
        RobotHardware.init(this, runtime);
        
        // Initialize threads
        initializeThreads();
        
        telemetry.addData("Status", "Initialized");
        telemetry.update();
        
        // Wait for the game to start (driver presses PLAY)
        waitForStart();
        runtime.reset();
        
        // Start all threads
        threadManager.startAll();
        
        try {
            // Run the main opmode loop
            runOpModeThreaded();
        } finally {
            // Ensure threads are stopped even if exception occurs
            threadManager.stopAll();
            cleanup();
        }
    }
    
    /**
     * Override this method to implement your OpMode logic.
     * This runs in the main thread while other threads run concurrently.
     */
    protected abstract void runOpModeThreaded();
    
    /**
     * Override this method to initialize and add threads to the thread manager.
     * Called before waitForStart().
     */
    protected abstract void initializeThreads();
    
    /**
     * Override this method for cleanup operations.
     * Called after threads are stopped.
     */
    protected void cleanup() {}
    
    /**
     * Get the thread manager instance
     */
    public ThreadManager getThreadManager() {
        return threadManager;
    }
    
    /**
     * Get the runtime timer
     */
    public ElapsedTime getRuntimeTime() {
        return runtime;
    }
}

