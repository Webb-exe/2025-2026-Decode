package teamcode.threading

/**
 * Manages multiple robot threads, providing synchronized start/stop operations.
 */
class ThreadManager {
    private val threads: MutableList<RobotThread>

    /**
     * Check if manager is running
     */
    var isRunning: Boolean = false
        private set

    init {
        this.threads = ArrayList<RobotThread>()
    }

    /**
     * Add a thread to be managed
     */
    fun addThread(thread: RobotThread?) {
        synchronized(threads) {
            threads.add(thread!!)
        }
    }

    /**
     * Remove a thread from management
     */
    fun removeThread(thread: RobotThread?) {
        synchronized(threads) {
            threads.remove(thread)
        }
    }

    /**
     * Start all managed threads
     */
    fun startAll() {
        synchronized(threads) {
            this.isRunning = true
            for (thread in threads) {
                if (!thread.isRunning) {
                    thread.start()
                }
            }
        }
    }

    /**
     * Stop all managed threads gracefully
     */
    fun stopAll() {
        synchronized(threads) {
            this.isRunning = false
            for (thread in threads) {
                if (thread.isRunning) {
                    thread.stopThread()
                }
            }

            // Wait for threads to finish
            for (thread in threads) {
                try {
                    thread.joinThread()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    /**
     * Pause all managed threads
     */
    fun pauseAll() {
        synchronized(threads) {
            for (thread in threads) {
                thread.pauseThread()
            }
        }
    }

    /**
     * Resume all managed threads
     */
    fun resumeAll() {
        synchronized(threads) {
            for (thread in threads) {
                thread.resumeThread()
            }
        }
    }

    /**
     * Get all managed threads
     */
    fun getThreads(): MutableList<RobotThread?> {
        synchronized(threads) {
            return ArrayList<RobotThread?>(threads)
        }
    }

    /**
     * Clear all threads from management
     */
    fun clear() {
        stopAll()
        synchronized(threads) {
            threads.clear()
        }
    }
}

