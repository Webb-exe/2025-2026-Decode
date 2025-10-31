package teamcode.threading;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages multiple robot threads, providing synchronized start/stop operations.
 */
public class ThreadManager {
    private List<RobotThread> threads;
    private boolean running = false;
    
    public ThreadManager() {
        super();
        this.threads = new ArrayList<>();
    }
    
    /**
     * Add a thread to be managed
     */
    public void addThread(RobotThread thread) {
        synchronized (threads) {
            threads.add(thread);
        }
    }
    
    /**
     * Remove a thread from management
     */
    public void removeThread(RobotThread thread) {
        synchronized (threads) {
            threads.remove(thread);
        }
    }
    
    /**
     * Start all managed threads
     */
    public void startAll() {
        synchronized (threads) {
            running = true;
            for (RobotThread thread : threads) {
                if (!thread.isRunning()) {
                    thread.start();
                }
            }
        }
    }
    
    /**
     * Stop all managed threads gracefully
     */
    public void stopAll() {
        synchronized (threads) {
            running = false;
            for (RobotThread thread : threads) {
                if (thread.isRunning()) {
                    thread.stopThread();
                }
            }
            
            // Wait for threads to finish
            for (RobotThread thread : threads) {
                try {
                    thread.joinThread();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    
    /**
     * Pause all managed threads
     */
    public void pauseAll() {
        synchronized (threads) {
            for (RobotThread thread : threads) {
                thread.pauseThread();
            }
        }
    }
    
    /**
     * Resume all managed threads
     */
    public void resumeAll() {
        synchronized (threads) {
            for (RobotThread thread : threads) {
                thread.resumeThread();
            }
        }
    }
    
    /**
     * Get all managed threads
     */
    public List<RobotThread> getThreads() {
        synchronized (threads) {
            return new ArrayList<>(threads);
        }
    }
    
    /**
     * Check if manager is running
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Clear all threads from management
     */
    public void clear() {
        stopAll();
        synchronized (threads) {
            threads.clear();
        }
    }
}

