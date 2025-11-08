package teamcode.robot.core.utils

/**
 * Timer utility class for measuring elapsed time.
 * Useful for timing operations, delays, and timeouts.
 */
class Timer {
    private var startTime: Long = 0
    private var pausedTime: Long = 0
    private var isRunning: Boolean = false
    private var isPaused: Boolean = false

    /**
     * Starts the timer. If already running, resets and starts again.
     */
    fun start() {
        startTime = System.nanoTime()
        pausedTime = 0
        isRunning = true
        isPaused = false
    }

    /**
     * Stops the timer and resets it.
     */
    fun stop() {
        isRunning = false
        isPaused = false
        startTime = 0
        pausedTime = 0
    }

    /**
     * Resets the timer without stopping it (restarts from 0).
     */
    fun reset() {
        if (isRunning) {
            startTime = System.nanoTime()
            pausedTime = 0
            isPaused = false
        } else {
            startTime = 0
            pausedTime = 0
        }
    }

    /**
     * Pauses the timer. Can be resumed with resume().
     */
    fun pause() {
        if (isRunning && !isPaused) {
            pausedTime = System.nanoTime()
            isPaused = true
        }
    }

    /**
     * Resumes the timer if it was paused.
     */
    fun resume() {
        if (isRunning && isPaused) {
            val pauseDuration = System.nanoTime() - pausedTime
            startTime += pauseDuration
            isPaused = false
        }
    }

    /**
     * Returns the elapsed time in nanoseconds.
     */
    fun elapsedNanos(): Long {
        if (!isRunning) return 0
        if (isPaused) {
            return pausedTime - startTime
        }
        return System.nanoTime() - startTime
    }

    /**
     * Returns the elapsed time in milliseconds.
     */
    fun elapsedMillis(): Long {
        return elapsedNanos() / 1_000_000
    }

    /**
     * Returns the elapsed time in seconds.
     */
    fun elapsedSeconds(): Double {
        return elapsedNanos() / 1_000_000_000.0
    }

    /**
     * Checks if the specified duration (in seconds) has elapsed.
     */
    fun hasElapsed(seconds: Double): Boolean {
        return elapsedSeconds() >= seconds
    }

    /**
     * Checks if the specified duration (in milliseconds) has elapsed.
     */
    fun hasElapsedMillis(millis: Long): Boolean {
        return elapsedMillis() >= millis
    }

    /**
     * Returns whether the timer is currently running.
     */
    fun isRunning(): Boolean {
        return isRunning
    }

    /**
     * Returns whether the timer is currently paused.
     */
    fun isPaused(): Boolean {
        return isPaused
    }
}

