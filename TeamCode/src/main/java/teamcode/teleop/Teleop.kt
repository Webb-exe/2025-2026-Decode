package teamcode.teleop

import com.qualcomm.robotcore.eventloop.opmode.TeleOp
import teamcode.robot.core.Alliance
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.state.RobotState
import teamcode.robot.core.state.RobotStateMachine
import teamcode.robot.subsystems.IntakeSubsystem
import teamcode.robot.subsystems.MovementSubsystem
import teamcode.robot.subsystems.ShooterState
import teamcode.robot.subsystems.ShooterSubsystem
import teamcode.robot.subsystems.SpindexerSubsystem
import teamcode.robot.subsystems.TurretState
import teamcode.robot.subsystems.TurretSubsystem
import teamcode.robot.subsystems.VisionSubsystem
import teamcode.threading.ThreadedOpMode
import kotlin.math.min

/**
 * Main teleop OpMode.
 * Clean architecture with automatic command scheduling.
 */
@TeleOp(name = "Teleop", group = "Teleop")
class Teleop : ThreadedOpMode() {
    
    private lateinit var movementSubsystem: MovementSubsystem
    private lateinit var turretSubsystem: TurretSubsystem
    private lateinit var visionSubsystem: VisionSubsystem
    private lateinit var shooterSubsystem: ShooterSubsystem
    private lateinit var spindexerSubsystem: SpindexerSubsystem
    private lateinit var intakeSubsystem: IntakeSubsystem
    private var lastA = false
    
    override fun initOpMode() {
        movementSubsystem = MovementSubsystem()
        turretSubsystem = TurretSubsystem()
        visionSubsystem = VisionSubsystem()
        shooterSubsystem = ShooterSubsystem()
        spindexerSubsystem = SpindexerSubsystem()
        intakeSubsystem = IntakeSubsystem()
        
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
        turretSubsystem.enable()
        turretSubsystem.enterManualMode()
    }
    
    override fun mainLoop() {
        when (RobotStateMachine.getState()){
            RobotState.IDLE -> {
                if (gamepad2Ex.rightTrigger.value>0.05){
                    RobotStateMachine.transitionTo(RobotState.SHOOTING)
                }
            }
            RobotState.SHOOTING -> {
                if (gamepad2Ex.rightTrigger.value<0.05 && shooterSubsystem.currentState==ShooterState.IDLE){
                    RobotStateMachine.transitionTo(RobotState.IDLE)
                    return
                }
                if (gamepad2Ex.b.wasPressed()){
                    shooterSubsystem.triggerKicker()
                }
            }
            RobotState.INTAKING -> {

            }


        }

        if (gamepad2Ex.a.wasPressed()){
            spindexerSubsystem.changeTargetPositionByOffset(1)
        }

//        if (gamepad2Ex.rightBumper.wasPressed()){
//            turretSubsystem.triggerStates()
//        }

        if (turretSubsystem.currentState== TurretState.MANUAL){
            if (gamepad2Ex.rightBumper.value){
                turretSubsystem.setManualTurnSpeed(1.0)
            } else if(gamepad2Ex.leftBumper.value){
                turretSubsystem.setManualTurnSpeed(-1.0)
            }else{
                turretSubsystem.setManualTurnSpeed(0.0)
            }
        }

        movementSubsystem.setDriveInput(gamepad1)


//        RobotHardware.kickerServo.set(gamepad1Ex.leftTrigger.value)
//        if (gamepad1Ex.rightTrigger.value != 0.0){
//            RobotHardware.spindexterMotor.set(gamepad1Ex.rightTrigger.value);
//        }
//        else if (gamepad1Ex.leftTrigger.value != 0.0){
//            RobotHardware.spindexterMotor.set(-gamepad1Ex.leftTrigger.value);
//        }
//        else{
//            RobotHardware.spindexterMotor.set(0.0);
//        }

//        spindexerSubsystem.updateGamepad(gamepad1Ex.leftTrigger.value,gamepad1Ex.rightTrigger.value)




        // ===== TELEMETRY =====
        robotTelemetry.addData("Robot State", getState().name)
        robotTelemetry.addData("Runtime", String.format("%.2f s", runtime.seconds()))
    }
    
    override fun cleanup() {
        movementSubsystem.disable()
        turretSubsystem.disable()
    }
}
