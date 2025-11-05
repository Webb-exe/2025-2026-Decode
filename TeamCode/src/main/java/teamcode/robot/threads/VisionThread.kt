package teamcode.robot.threads

import com.qualcomm.hardware.limelightvision.LLResult
import com.qualcomm.hardware.limelightvision.LLResultTypes.FiducialResult
import com.qualcomm.hardware.limelightvision.Limelight3A
import teamcode.robot.core.RobotHardware
import teamcode.threading.RobotThread
import java.util.function.Consumer
import kotlin.concurrent.Volatile

/**
 * Thread for processing vision data from Limelight.
 * Runs vision processing in a separate thread to avoid blocking the main loop.
 * Supports both basic target detection (tx, ty, ta) and AprilTag detection.
 */
class VisionThread : RobotThread("VisionThread", 100) {
    @Volatile
    private var targetsDetected = false

    /**
     * Get horizontal offset of target (tx)
     */
    @Volatile
    var targetX: Double = 0.0
        private set

    /**
     * Get vertical offset of target (ty)
     */
    @Volatile
    var targetY: Double = 0.0
        private set

    /**
     * Get target area
     */
    @Volatile
    var targetArea: Double = 0.0
        private set
    private val aprilTagsLock = Any()
    private var aprilTags: MutableMap<Int?, AprilTag?> = HashMap<Int?, AprilTag?>()

    /**
     * Represents an AprilTag detected by the Limelight.
     */
    class AprilTag(val id: Int, val yDegrees: Double, val xDegrees: Double)

    override fun onStart() {
        if (RobotHardware.limelight == null) {
            return
        }
        RobotHardware.limelight.start()
    }


    override fun runLoop() {
        if (RobotHardware.limelight == null) {
            telemetry!!.addData("Status", "No Limelight")
            telemetry!!.addData("Targets Detected", false)
            return
        }

        try {
            val result = RobotHardware.limelight.getLatestResult()
            // Read vision data from Limelight result
            targetsDetected = result.isValid()

            if (targetsDetected) {
                targetX = result.getTx() // Horizontal offset from crosshair
                targetY = result.getTy() // Vertical offset from crosshair
                targetArea = result.getTa() // Target area
            } else {
                targetX = 0.0
                targetY = 0.0
                targetArea = 0.0
            }


            // Update AprilTag detection
            updateAprilTags(result)


            // Update telemetry - data persists until next update from this thread
            telemetry!!.addData("Status", "Running")
            telemetry!!.addData("Targets Detected", targetsDetected)
            telemetry!!.addData("Target X", String.format("%.2f°", targetX))
            telemetry!!.addData("Target Y", String.format("%.2f°", targetY))
            telemetry!!.addData("Target Area", String.format("%.2f%%", targetArea))


            // Add AprilTag telemetry
            synchronized(aprilTagsLock) {
                telemetry!!.addData("AprilTags Count", aprilTags.size)
                if (!aprilTags.isEmpty()) {
                    val tagsInfo = StringBuilder()
                    aprilTags.forEach { (id: Int?, tag: AprilTag?) ->
                        if (tagsInfo.length > 0) tagsInfo.append(", ")
                        tagsInfo.append(String.format("ID:%d", id))
                    }
                    telemetry!!.addData("AprilTag IDs", tagsInfo.toString())
                }
            }
        } catch (e: Exception) {
            handleException(e)
            targetsDetected = false
            synchronized(aprilTagsLock) {
                aprilTags = HashMap<Int?, AprilTag?>()
            }
            telemetry!!.addData("Status", "Error: " + e.message)
            telemetry!!.addData("Targets Detected", false)
        }
    }

    /**
     * Update the map of detected AprilTags from Limelight result.
     */
    private fun updateAprilTags(result: LLResult?) {
        try {
            val detectedTags: MutableMap<Int?, AprilTag?> = HashMap<Int?, AprilTag?>()

            if (result != null && result.isValid()) {
                result.getFiducialResults().forEach(Consumer { fiducialResult: FiducialResult? ->
                    val tag = AprilTag(
                        fiducialResult!!.getFiducialId(),
                        fiducialResult.getTargetYDegrees(),
                        fiducialResult.getTargetXDegrees()
                    )
                    detectedTags.put(tag.id, tag)
                })
            }

            synchronized(aprilTagsLock) {
                aprilTags = detectedTags
            }
        } catch (e: Exception) {
            synchronized(aprilTagsLock) {
                aprilTags = HashMap<Int?, AprilTag?>()
            }
        }
    }

    /**
     * Check if targets are detected
     */
    fun hasTargets(): Boolean {
        return targetsDetected
    }

    val limelight: Limelight3A?
        /**
         * Get the Limelight instance
         */
        get() = RobotHardware.limelight

    /**
     * Get all detected AprilTags.
     * @return Array of detected AprilTags, or empty array if none detected
     */
    fun getAprilTags(): Array<AprilTag?> {
        synchronized(aprilTagsLock) {
            return aprilTags.values.toTypedArray<AprilTag?>()
        }
    }

    /**
     * Get a specific AprilTag by ID.
     * @param id The AprilTag ID to search for
     * @return The matching AprilTag, or null if not found
     */
    fun getAprilTag(id: Int): AprilTag? {
        synchronized(aprilTagsLock) {
            return aprilTags.get(id)
        }
    }

    /**
     * Get a specific AprilTag by ID(s).
     * @param ids Array of AprilTag IDs to search for
     * @return The first matching AprilTag, or null if not found
     */
    fun getAprilTag(ids: IntArray): AprilTag? {
        synchronized(aprilTagsLock) {
            for (id in ids) {
                val tag = aprilTags.get(id)
                if (tag != null) {
                    return tag
                }
            }
        }
        return null
    }

    /**
     * Check if any AprilTags are detected.
     * @return true if AprilTags are detected, false otherwise
     */
    fun hasAprilTags(): Boolean {
        synchronized(aprilTagsLock) {
            return !aprilTags.isEmpty()
        }
    }
}

