package teamcode.robot;

import static org.firstinspires.ftc.robotcore.internal.system.Misc.contains;

import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;

import java.util.ArrayList;
import java.util.List;

public class Limelight {

    public Limelight() {
    }

    class AprilTag {
        public int id;
        public double yDegrees;
        public double xDegrees;

        public AprilTag(int id, double yDegrees, double xDegrees) {
            this.id = id;
            this.yDegrees = yDegrees;
            this.xDegrees = xDegrees;
        }
    }

    public getAprilTags() {
        LLResult recentResults = RobotHardware.limelight.getLatestResult();

        if (recentResults.isValid()) {
            List<AprilTag> aprilTags = new ArrayList<>();
            recentResults.getFiducialResults().forEach(result -> {
                AprilTag tag = new AprilTag(
                        result.getFiducialId(),
                        result.getTargetYDegrees(),
                        result.getTargetXDegrees()
                );
                aprilTags.add(tag);
            });

            return aprilTags.toArray(new AprilTag[0]);
        }

        return null; // return empty array if no results
    }

    public getAprilTag(int[] ids) {
          LLResult recentResults = RobotHardware.limelight.getLatestResult();
          if (recentResults.isValid()){
              for (LLResultTypes.FiducialResult result: recentResults.getFiducialResults()){
                  if (contains(ids, result.getFiducialId())){
                      return new AprilTag(
                              result.getFiducialId(),
                              result.getTargetYDegrees(),
                              result.getTargetXDegrees()
                      );
                  }
              }
          }
          return null;
    }
}
