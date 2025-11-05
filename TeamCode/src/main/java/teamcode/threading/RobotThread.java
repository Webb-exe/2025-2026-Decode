package teamcode.threading;

import com.qualcomm.robotcore.util.ElapsedTime;

import java.util.concurrent.ConcurrentHashMap;

import teamcode.telemetry.RobotTelemetry;

/**
 * Base class for robot subsystem threads.
 * Provides common functionality for thread lifecycle management and synchronization.
 */
public abstract class RobotThread extends Thread {
    // Automatically tracks ONE instance per subclass
    private static final ConcurrentHashMap<Class<? extends RobotThread>, RobotThread> GLOBALS =
            new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T extends RobotThread> T currentOf(Class<T> type) {
        RobotThread instance = GLOBALS.get(type);
        if (instance == null)
            throw new IllegalStateException(type.getSimpleName() + " has not been constructed yet.");
        return (T) instance;
    }

    public static <T extends RobotThread> T current(Class<T> type) {
        RobotThread instance = GLOBALS.get(type);
        if (instance == null)
            throw new IllegalStateException(type.getSimpleName() + " has not started yet.");
        return (T) instance;
    }

    /** Automatically register this instance as the singleton for its class */
    private void registerAsGlobal() {
        RobotThread previous = GLOBALS.putIfAbsent(getClass(), this);
        if (previous != null && previous != this) {
            throw new IllegalStateException(
                    "Multiple instances of " + getClass().getSimpleName() + " are not allowed."
            );
        }
    }

    /** Automatically unregister on stop */
    private void unregisterGlobal() {
        GLOBALS.compute(getClass(), (k, v) -> v == this ? null : v);
    }
    protected volatile boolean running = false;
    protected volatile boolean paused = false;
    protected ElapsedTime runtime;
    protected final Object lock = new Object();
    protected RobotTelemetry telemetry;
    
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

        registerAsGlobal();

        if (telemetry != null)
            telemetry.setNamespace(getName());

        onStart();

        try {
            while (running && !isInterrupted()) {
                synchronized (lock) {
                    while (paused && running) {
                        try { lock.wait(); }
                        catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }

                if (!running) break;

                if (telemetry != null) telemetry.beginStaging();
                try {
                    runLoop();
                    if (telemetry != null) telemetry.commitStaging();
                } catch (Exception e) {
                    if (telemetry != null) telemetry.discardStaging();
                    handleException(e);
                }

                try { Thread.sleep(updateIntervalMs); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        finally {
            try { onStop(); }
            finally {
                // ✅ UNREGISTER on shutdown
                unregisterGlobal();
            }
        }
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
    
    /**
     * Set the telemetry system for this thread.
     * Should be called before starting the thread.
     * @param telemetry The shared telemetry system
     */
    public void setTelemetry(RobotTelemetry telemetry) {
        this.telemetry = telemetry;
        // Set namespace for this thread
        if (telemetry != null) {
            telemetry.setNamespace(getName());
        }
    }
    
    /**
     * Add telemetry data for this thread.
     * Uses the thread name as the namespace.
     * @param key The telemetry key
     * @param value The telemetry value
     */
    protected void addTelemetry(String key, Object value) {
        if (telemetry != null) {
            // Ensure namespace is set for this thread
            telemetry.setNamespace(getName());
            telemetry.addData(key, value);
        }
    }
    
    /**
     * Add formatted telemetry data for this thread.
     * @param key The telemetry key
     * @param format Format string
     * @param args Format arguments
     */
    protected void addTelemetry(String key, String format, Object... args) {
        if (telemetry != null) {
            // Ensure namespace is set for this thread
            telemetry.setNamespace(getName());
            telemetry.addData(key, format, args);
        }
    }
    
    /**
     * Clear all telemetry data for this thread.
     */
    protected void clearTelemetry() {
        if (telemetry != null) {
            telemetry.clearNamespace(getName());
        }
    }
}

