package teamcode.robot.subsystems

import com.qualcomm.hardware.limelightvision.LLResult
import com.qualcomm.hardware.limelightvision.LLResultTypes.FiducialResult
import com.qualcomm.hardware.limelightvision.Limelight3A
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.subsystem.Subsystem
import java.util.function.Consumer
import kotlin.concurrent.Volatile

/**
 * Vision subsystem for processing Limelight data.
 * Runs continuously on its own thread.
 */
class VisionSubsystem : Subsystem("Vision", 100) {
    
    @Volatile
    private var targetsDetected = false
    
    @Volatile
    var targetX: Double = 0.0
        private set
    
    @Volatile
    var targetY: Double = 0.0
        private set
    
    @Volatile
    var targetArea: Double = 0.0
        private set
    
    private val aprilTagsLock = Any()
    private var aprilTags: MutableMap<Int?, AprilTag?> = HashMap()
    
    class AprilTag(val id: Int, val yDegrees: Double, val xDegrees: Double)
    
    override fun init() {
        RobotHardware.limelight?.start()
    }
    
    override fun periodic() {
        if (RobotHardware.limelight == null) return
        
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
    
    fun hasTargets(): Boolean = targetsDetected
    
    val limelight: Limelight3A? get() = RobotHardware.limelight
    
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
        telemetry.addData("X", String.format("%.2f°", targetX))
        telemetry.addData("Y", String.format("%.2f°", targetY))
        telemetry.addData("Area", String.format("%.2f%%", targetArea))
        
        synchronized(aprilTagsLock) {
            telemetry.addData("AprilTags", aprilTags.size)
        }
    }
}
