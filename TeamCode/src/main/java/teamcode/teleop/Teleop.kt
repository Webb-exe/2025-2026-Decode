package teamcode.teleop

import com.qualcomm.robotcore.eventloop.opmode.TeleOp
import teamcode.robot.core.Alliance
import teamcode.robot.core.RobotHardware
import teamcode.robot.core.state.RobotState
import teamcode.robot.core.state.RobotStateMachine
import teamcode.robot.subsystems.ColorSensorSubsystem
import teamcode.robot.subsystems.IntakeSubsystem
import teamcode.robot.subsystems.KickerSubsystem
import teamcode.robot.subsystems.MovementSubsystem
import teamcode.robot.subsystems.ShooterState
import teamcode.robot.subsystems.ShooterSubsystem
import teamcode.robot.subsystems.SpindexerSubsystem
import teamcode.robot.subsystems.TurretState
import teamcode.robot.subsystems.TurretSubsystem
import teamcode.robot.subsystems.VisionSubsystem
import teamcode.threading.ThreadedOpMode
import teamcode.commands.ShootAndTurn
import teamcode.commands.TriggerKicker
import teamcode.commands.SpindexerSpin
import teamcode.robot.command.execute
import teamcode.robot.subsystems.KickerState
import teamcode.robot.subsystems.VisionPipeline
import kotlin.math.min

/**
 * Main teleop OpMode.
 * Clean architecture with automatic command scheduling.
 */
@TeleOp(name = "Teleop", group = "Teleop")
class Teleop : ThreadedOpMode() {
    
    // Subsystems
    private lateinit var movementSubsystem: MovementSubsystem
    private lateinit var turretSubsystem: TurretSubsystem
    private lateinit var visionSubsystem: VisionSubsystem
    private lateinit var shooterSubsystem: ShooterSubsystem
    private lateinit var kickerSubsystem: KickerSubsystem
    private lateinit var spindexerSubsystem: SpindexerSubsystem
    private lateinit var intakeSubsystem: IntakeSubsystem
    private lateinit var colorSensorSubsystem: ColorSensorSubsystem

    
    override fun initOpMode() {
        // Initialize subsystems
        movementSubsystem = MovementSubsystem()
        turretSubsystem = TurretSubsystem()
        visionSubsystem = VisionSubsystem()
        shooterSubsystem = ShooterSubsystem()
        kickerSubsystem = KickerSubsystem()
        spindexerSubsystem = SpindexerSubsystem()
        intakeSubsystem = IntakeSubsystem()
        colorSensorSubsystem = ColorSensorSubsystem()

            telemetry.addData("Status", "In Init")

    }

    override fun onStart() {
        turretSubsystem.enable()
//        turretSubsystem.enterManualMode()
        visionSubsystem.setPipeline(VisionPipeline.RED)
    }
    
    override fun mainLoop() {
        when (RobotStateMachine.getState()){
            RobotState.IDLE -> {
                if (gamepad2Ex.rightTrigger.value>0.05){
                    RobotStateMachine.transitionTo(RobotState.SHOOTING)
                }
            }
            RobotState.SHOOTING -> {
                if (gamepad2Ex.rightTrigger.value<0.05 && kickerSubsystem.currentState== KickerState.IDLE){
                    RobotStateMachine.transitionTo(RobotState.IDLE)
                    return
                }
                // Schedule shoot command when B button is pressed
                if (gamepad2Ex.b.wasPressed()){
                    TriggerKicker().execute()
                }
                if (gamepad2Ex.dpadUp.wasPressed()){
                    ShootAndTurn().execute()
                }
            }
            RobotState.INTAKING -> {

            }


        }

        // Spindexer control - schedule cycle command when A button is pressed
        if (gamepad2Ex.a.wasPressed()){
            SpindexerSpin(1).execute()
        }

//        // Turret control - execute manual turret commands based on bumper input
//        if (turretSubsystem.currentState == TurretState.MANUAL){
//            if (gamepad2Ex.rightBumper.value){
//                turretSubsystem
//            } else if(gamepad2Ex.leftBumper.value){
//                ManualTurretCommand(turretSubsystem, -1.0).execute()
//            } else {
//                ManualTurretCommand(turretSubsystem, 0.0).execute()
//            }
//        }

        // Drive control
        movementSubsystem.setDriveInput(gamepad1)


        if(gamepad2Ex.dpadLeft.wasPressed()) {
            RobotHardware.turretTurnServo.set(RobotHardware.turretTurnServo.get()+0.1)
        }
        if(gamepad2Ex.dpadRight.wasPressed()) {
            RobotHardware.turretTurnServo.set(RobotHardware.turretTurnServo.get()-0.1)
        }
        // ===== TELEMETRY =====
        robotTelemetry.addData("Servo",RobotHardware.turretTurnServo.get())
        robotTelemetry.addData("Robot State", getState().name)
        robotTelemetry.addData("Runtime", runtime.seconds())
    }
    
    override fun cleanup() {
        movementSubsystem.disable()
        turretSubsystem.disable()
    }
}
