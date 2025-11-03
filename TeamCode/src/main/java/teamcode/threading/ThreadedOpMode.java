package teamcode.threading;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import teamcode.robot.core.RobotHardware;
import teamcode.telemetry.RobotTelemetry;

/**
 * Base class for OpModes that use threading.
 * Extends LinearOpMode and provides thread management functionality.
 */
public abstract class ThreadedOpMode extends LinearOpMode {
    protected ThreadManager threadManager;
    protected ElapsedTime runtime;
    
    // Unified telemetry system - use this everywhere!
    protected RobotTelemetry telemetry;
    
    // The original FTC telemetry (renamed from 'telemetry')
    protected Telemetry ftcTelemetry;

    @Override
    public final void runOpMode() {
        runtime = new ElapsedTime();
        threadManager = new ThreadManager();
        
        // Save reference to original FTC telemetry
        ftcTelemetry = super.telemetry;
        
        // Create unified telemetry system
        telemetry = new RobotTelemetry(ftcTelemetry);
        telemetry.setNamespace("Main");
        
        // Initialize robot hardware
        RobotHardware.init(this, runtime);
        
        // Initialize threads
        initializeThreads();
        
        // Set telemetry for all threads
        for (RobotThread thread : threadManager.getThreads()) {
            thread.setTelemetry(telemetry);
        }
        
        telemetry.addData("Status", "Initialized");
        runInit();
        telemetry.update();
        
        // Wait for the game to start (driver presses PLAY)
        waitForStart();
        runtime.reset();
        
        // Start all threads
        threadManager.startAll();
        
        // Call onStart hook for user initialization after threads start
        onStart();
        
        try {
            // Run the main opmode loop with automatic telemetry staging
            while (opModeIsActive()) {
                // Use atomic staging pattern (same as threads) to prevent flickering
                telemetry.setNamespace("Main");
                telemetry.beginStaging();
                
                try {
                    // Call user's loop method
                    mainLoop();
                    
                    // Commit staging buffer atomically
                    telemetry.commitStaging();
                } catch (Exception e) {
                    // Discard staging on error
                    telemetry.discardStaging();
                    throw e;
                }
                
                // Update telemetry - displays all thread data + main loop data
                telemetry.update();
            }
        } finally {
            // Ensure threads are stopped even if exception occurs
            threadManager.stopAll();
            cleanup();
        }
    }

    /**
     * Override this method to implement your OpMode initialization logic.
     * Called once when INIT is pressed, before threads are started.
     */
    protected abstract void runInit();
    
    /**
     * Override this method to initialize and add threads to the thread manager.
     * Called before waitForStart().
     */
    protected abstract void initializeThreads();
    
    /**
     * Override this method for any setup after threads start but before the main loop begins.
     * Called once after waitForStart() and after threads are started.
     */
    protected void onStart() {}
    
    /**
     * Override this method to implement your main loop logic.
     * This is called repeatedly while the OpMode is active.
     * Telemetry staging and updates are handled automatically.
     */
    protected abstract void mainLoop();
    
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
    
    /**
     * Get the unified telemetry system
     */
    public RobotTelemetry getTelemetry() {
        return telemetry;
    }
    
    /**
     * Get the original FTC telemetry object
     */
    public Telemetry getFtcTelemetry() {
        return ftcTelemetry;
    }
}

