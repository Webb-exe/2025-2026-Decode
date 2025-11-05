package teamcode.robot.threads

import com.qualcomm.robotcore.hardware.Gamepad
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.RobotStateEnum
import teamcode.threading.RobotThread

class InputThread: RobotThread("InputThread",10) {
    @Volatile
    private var gamepad1:Gamepad = Gamepad()
    @Volatile
    private var gamepad2:Gamepad = Gamepad()

    override fun runLoop(){
        when (RobotHardware.DriverState){
            RobotStateEnum.IDLE -> {

            }
            RobotStateEnum.SORTING -> {

            }
            RobotStateEnum.INTAKING -> {

            }
            RobotStateEnum.SHOOTING -> {

            }
        }
    }

    fun updateGamepads(gp1:Gamepad,gp2:Gamepad){
        gamepad1=gp1
        gamepad2=gp2
    }
}