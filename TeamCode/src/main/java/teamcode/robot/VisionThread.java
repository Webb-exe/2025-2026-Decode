package teamcode.robot;

import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import teamcode.threading.RobotThread;

import java.util.HashMap;
import java.util.Map;

/**
 * Thread for processing vision data from Limelight.
 * Runs vision processing in a separate thread to avoid blocking the main loop.
 * Supports both basic target detection (tx, ty, ta) and AprilTag detection.
 */
public class VisionThread extends RobotThread {
    private volatile boolean targetsDetected = false;
    private volatile double targetX = 0.0;
    private volatile double targetY = 0.0;
    private volatile double targetArea = 0.0;
    private final Object aprilTagsLock = new Object();
    private Map<Integer, AprilTag> aprilTags = new HashMap<>();
    
    /**
     * Represents an AprilTag detected by the Limelight.
     */
    public static class AprilTag {
        public final int id;
        public final double yDegrees;
        public final double xDegrees;

        public AprilTag(int id, double yDegrees, double xDegrees) {
            super();
            this.id = id;
            this.yDegrees = yDegrees;
            this.xDegrees = xDegrees;
        }
    }
    
    public VisionThread() {
        super("VisionThread", 33); // ~30Hz update rate (typical for vision)
    }

    @Override
    protected void onStart(){
        if(RobotHardware.limelight == null){
            return;
        }
        RobotHardware.limelight.start();
    }


    @Override
    protected void runLoop() {
        if (RobotHardware.limelight == null) {
            return;
        }
        
        try {
            LLResult result = RobotHardware.limelight.getLatestResult();
            // Read vision data from Limelight result
            targetsDetected = result.isValid();
            
            if (targetsDetected) {
                targetX = result.getTx(); // Horizontal offset from crosshair
                targetY = result.getTy(); // Vertical offset from crosshair
                targetArea = result.getTa(); // Target area
            } else {
                targetX = 0.0;
                targetY = 0.0;
                targetArea = 0.0;
            }
            
            // Update AprilTag detection
            updateAprilTags(result);
        } catch (Exception e) {
            handleException(e);
            targetsDetected = false;
            synchronized (aprilTagsLock) {
                aprilTags = new HashMap<>();
            }
        }
    }
    
    /**
     * Update the map of detected AprilTags from Limelight result.
     */
    private void updateAprilTags(LLResult result) {
        try {
            Map<Integer, AprilTag> detectedTags = new HashMap<>();
            
            if (result != null && result.isValid()) {
                result.getFiducialResults().forEach(fiducialResult -> {
                    AprilTag tag = new AprilTag(
                            fiducialResult.getFiducialId(),
                            fiducialResult.getTargetYDegrees(),
                            fiducialResult.getTargetXDegrees()
                    );
                    detectedTags.put(tag.id, tag);
                });
            }
            
            synchronized (aprilTagsLock) {
                aprilTags = detectedTags;
            }
        } catch (Exception e) {
            synchronized (aprilTagsLock) {
                aprilTags = new HashMap<>();
            }
        }
    }
    
    /**
     * Check if targets are detected
     */
    public boolean hasTargets() {
        return targetsDetected;
    }
    
    /**
     * Get horizontal offset of target (tx)
     */
    public double getTargetX() {
        return targetX;
    }
    
    /**
     * Get vertical offset of target (ty)
     */
    public double getTargetY() {
        return targetY;
    }
    
    /**
     * Get target area
     */
    public double getTargetArea() {
        return targetArea;
    }
    
    /**
     * Get the Limelight instance
     */
    public Limelight3A getLimelight() {
        return RobotHardware.limelight;
    }
    
    /**
     * Get all detected AprilTags.
     * @return Array of detected AprilTags, or empty array if none detected
     */
    public AprilTag[] getAprilTags() {
        synchronized (aprilTagsLock) {
            return aprilTags.values().toArray(new AprilTag[0]);
        }
    }
    
    /**
     * Get a specific AprilTag by ID.
     * @param id The AprilTag ID to search for
     * @return The matching AprilTag, or null if not found
     */
    public AprilTag getAprilTag(int id) {
        synchronized (aprilTagsLock) {
            return aprilTags.get(id);
        }
    }
    
    /**
     * Get a specific AprilTag by ID(s).
     * @param ids Array of AprilTag IDs to search for
     * @return The first matching AprilTag, or null if not found
     */
    public AprilTag getAprilTag(int[] ids) {
        synchronized (aprilTagsLock) {
            for (int id : ids) {
                AprilTag tag = aprilTags.get(id);
                if (tag != null) {
                    return tag;
                }
            }
        }
        return null;
    }
    
    /**
     * Check if any AprilTags are detected.
     * @return true if AprilTags are detected, false otherwise
     */
    public boolean hasAprilTags() {
        synchronized (aprilTagsLock) {
            return !aprilTags.isEmpty();
        }
    }
}

