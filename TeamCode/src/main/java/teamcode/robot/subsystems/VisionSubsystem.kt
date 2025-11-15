package teamcode.robot.subsystems

import com.qualcomm.hardware.limelightvision.LLResult
import com.qualcomm.hardware.limelightvision.LLResultTypes.FiducialResult
import com.qualcomm.hardware.limelightvision.Limelight3A
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.subsystem.Subsystem
import java.util.function.Consumer
import kotlin.concurrent.Volatile

enum class VisionPipeline(val id: Int) {
    OBELISK(0),
    RED(1),
    BLUE(2),
}

/**
 * Vision subsystem for processing Limelight data.
 * Runs continuously on its own thread.
 */
class VisionSubsystem : Subsystem("Vision", 100) {
    
    @Volatile
    private var targetsDetected = false
    
    @Volatile
    final var targetX: Double = 0.0
        private set
    
    @Volatile
    final var targetY: Double = 0.0
        private set
    
    @Volatile
    final var targetArea: Double = 0.0
        private set
    
    private val aprilTagsLock = Any()
    private var aprilTags: MutableMap<Int?, AprilTag?> = HashMap()

    private var currentPipeline = VisionPipeline.OBELISK
    
    class AprilTag(val id: Int, val yDegrees: Double, val xDegrees: Double)
    
    override fun init() {
        RobotHardware.limelight.start()
    }
    
    override fun periodic() {
        
        try {
            val result = RobotHardware.limelight.getLatestResult()
            targetsDetected = result.isValid()
            
            if (targetsDetected) {
                targetX = result.getTx()
                targetY = result.getTy()
                targetArea = result.getTa()
            } else {
                targetX = 0.0
                targetY = 0.0
                targetArea = 0.0
            }
            
            updateAprilTags(result)
        } catch (e: Exception) {
            handleException(e)
            targetsDetected = false
            synchronized(aprilTagsLock) {
                aprilTags = HashMap()
            }
        }
    }
    
    private fun updateAprilTags(result: LLResult?) {
        try {
            val detectedTags: MutableMap<Int?, AprilTag?> = HashMap()
            
            if (result != null && result.isValid()) {
                result.getFiducialResults().forEach(Consumer { fiducialResult: FiducialResult? ->
                    val tag = AprilTag(
                        fiducialResult!!.getFiducialId(),
                        fiducialResult.getTargetYDegrees(),
                        fiducialResult.getTargetXDegrees()
                    )
                    detectedTags[tag.id] = tag
                })
            }
            
            synchronized(aprilTagsLock) {
                aprilTags = detectedTags
            }
        } catch (e: Exception) {
            synchronized(aprilTagsLock) {
                aprilTags = HashMap()
            }
        }
    }

    fun setPipeline(pipeline: VisionPipeline) {
        if (currentPipeline == pipeline) return
        currentPipeline = pipeline
        RobotHardware.limelight.pipelineSwitch(pipeline.id)
    }
    
    fun hasTargets(): Boolean = targetsDetected
    
    fun getAprilTags(): Array<AprilTag?> {
        synchronized(aprilTagsLock) {
            return aprilTags.values.toTypedArray()
        }
    }
    
    fun getAprilTag(id: Int): AprilTag? {
        synchronized(aprilTagsLock) {
            return aprilTags[id]
        }
    }
    
    fun getAprilTag(ids: IntArray): AprilTag? {
        synchronized(aprilTagsLock) {
            for (id in ids) {
                aprilTags[id]?.let { return it }
            }
        }
        return null
    }
    
    fun hasAprilTags(): Boolean {
        synchronized(aprilTagsLock) {
            return aprilTags.isNotEmpty()
        }
    }
    
    override fun updateTelemetry() {
        super.updateTelemetry()
        telemetry.addData("Status", "Running")
        telemetry.addData("Targets", targetsDetected)
        telemetry.addData("X", targetX)
        telemetry.addData("Y", targetY)
        telemetry.addData("Area", targetArea)
        
        synchronized(aprilTagsLock) {
            telemetry.addData("AprilTags", aprilTags.size)
        }
    }
}
