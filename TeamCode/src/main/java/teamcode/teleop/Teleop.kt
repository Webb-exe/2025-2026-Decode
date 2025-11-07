package teamcode.teleop

import com.qualcomm.robotcore.eventloop.opmode.TeleOp
import teamcode.robot.core.Alliance
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.state.RobotState
import teamcode.robot.core.state.RobotStateMachine
import teamcode.robot.subsystems.MovementSubsystem
import teamcode.robot.subsystems.ShooterSubsystem
import teamcode.robot.subsystems.TurretSubsystem
import teamcode.robot.subsystems.VisionSubsystem
import teamcode.threading.ThreadedOpMode
import kotlin.math.max

/**
 * Main teleop OpMode.
 * Clean architecture with automatic command scheduling.
 */
@TeleOp(name = "Teleop", group = "Teleop")
class Teleop : ThreadedOpMode() {
    
    private lateinit var movement: MovementSubsystem
    private lateinit var turret: TurretSubsystem
    private lateinit var vision: VisionSubsystem
    private lateinit var shooterSubsystem: ShooterSubsystem
    
    override fun initOpMode() {
        movement = MovementSubsystem()
        turret = TurretSubsystem()
        vision = VisionSubsystem()
        shooterSubsystem = ShooterSubsystem()
        
        // ===== INIT PHASE LOGIC =====
        var alliance = Alliance.RED
        
        while (opModeInInit()) {
            telemetry.addData("Status", "In Init")
            
            if (gamepad1.xWasPressed()) {
                alliance = Alliance.BLUE
            }
            if (gamepad1.bWasPressed()) {
                alliance = Alliance.RED
            }
            telemetry.update()
        }
        
        RobotHardware.alliance = alliance
    }
    
    override fun onStart() {
        turret.enable()

    }
    
    override fun mainLoop() {
        movement.setDriveInput(gamepad1)


        when (RobotStateMachine.getState()){
            RobotState.IDLE -> {
                if (gamepad1Ex.rightTrigger.value>0.5){
                    RobotStateMachine.transitionTo(RobotState.INTAKING)
                }
            }
            RobotState.SHOOTING -> {
                if (!gamepad1Ex.rightBumper.value){
                    RobotStateMachine.transitionTo(RobotState.IDLE);
                }
            }
            RobotState.SORTING -> {
                if (gamepad1Ex.rightBumper.value){
                    RobotStateMachine.transitionTo(RobotState.SHOOTING)
                }
                if(gamepad1Ex.leftBumper.value){

                }
            }
            RobotState.INTAKING -> {
                if (gamepad1Ex.rightTrigger.value<=0.5){
                    RobotStateMachine.transitionTo(RobotState.SORTING)
                }
            }


        }



        if (gamepad1Ex.a.wasPressed()){
            var temp=max(RobotHardware.kickerServo.get()-0.1,0.0)
            RobotHardware.kickerServo.set(temp)

        }
        if(gamepad1Ex.b.value){
            RobotHardware.kickerServo.set(1.0)
        }

        if (gamepad1Ex.rightTrigger.value != 0.0){
            RobotHardware.spindexterMotor.set(gamepad1Ex.rightTrigger.value);
        }
        else if (gamepad1Ex.leftTrigger.value != 0.0){
            RobotHardware.spindexterMotor.set(-gamepad1Ex.leftTrigger.value);
        }
        else{
            RobotHardware.spindexterMotor.set(0.0);
        }


        // ===== TELEMETRY =====
        telemetry.addData("Servo", RobotHardware.kickerServo.get())
        telemetry.addData("Robot State", getState().name)
        telemetry.addData("Shooter Power", shooterSubsystem.getPower())
        telemetry.addData("Runtime", String.format("%.2f s", runtime.seconds()))
    }
    
    override fun cleanup() {
        movement.disable()
        turret.disable()
    }
}
