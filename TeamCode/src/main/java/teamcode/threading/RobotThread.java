package teamcode.threading;

import com.qualcomm.robotcore.util.ElapsedTime;

/**
 * Base class for robot subsystem threads.
 * Provides common functionality for thread lifecycle management and synchronization.
 */
public abstract class RobotThread extends Thread {
    protected volatile boolean running = false;
    protected volatile boolean paused = false;
    protected ElapsedTime runtime;
    protected final Object lock = new Object();
    
    protected long updateIntervalMs = 20; // Default 20ms update rate (50Hz)
    
    public RobotThread(String name) {
        super(name);
    }
    
    public RobotThread(String name, long updateIntervalMs) {
        super(name);
        this.updateIntervalMs = updateIntervalMs;
    }
    
    /**
     * Initialize the thread - called before start()
     */

    public void initialize() {
        runtime = new ElapsedTime();
    }
    
    /**
     * Main thread loop - subclasses should override runLoop() instead of run()
     */
    @Override
    public final void run() {
        running = true;
        runtime.reset();
        
        onStart();
        
        while (running && !isInterrupted()) {
            synchronized (lock) {
                while (paused && running) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            if (!running) break;
            
            try {
                runLoop();
            } catch (Exception e) {
                handleException(e);
            }
            
            // Sleep for update interval
            try {
                Thread.sleep(updateIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        onStop();
    }
    
    /**
     * Override this method to implement the main logic of the thread
     */
    protected abstract void runLoop();
    
    /**
     * Called when thread starts - override for initialization
     */
    protected void onStart() {}
    
    /**
     * Called when thread stops - override for cleanup
     */
    protected void onStop() {}
    
    /**
     * Handle exceptions during thread execution
     */
    protected void handleException(Exception e) {
        e.printStackTrace();
    }
    
    /**
     * Start the thread
     */
    @Override
    public synchronized void start() {
        if (!running) {
            initialize();
            super.start();
        }
    }
    
    /**
     * Stop the thread gracefully
     */
    public void stopThread() {
        running = false;
        resumeThread(); // Wake up if paused
        interrupt();
    }
    
    /**
     * Pause the thread
     */
    public void pauseThread() {
        synchronized (lock) {
            paused = true;
        }
    }
    
    /**
     * Resume the thread
     */
    public void resumeThread() {
        synchronized (lock) {
            paused = false;
            lock.notify();
        }
    }
    
    /**
     * Check if thread is running
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Check if thread is paused
     */
    public boolean isPaused() {
        return paused;
    }
    
    /**
     * Set the update interval in milliseconds
     */
    public void setUpdateIntervalMs(long intervalMs) {
        this.updateIntervalMs = intervalMs;
    }
    
    /**
     * Get the update interval in milliseconds
     */
    public long getUpdateIntervalMs() {
        return updateIntervalMs;
    }
    
    /**
     * Wait for thread to finish
     */
    public void joinThread() throws InterruptedException {
        join();
    }
}

