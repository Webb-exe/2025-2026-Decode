# Command System Quick Reference

## Basic Command Structure

```kotlin
class MyCommand : Command() {
    private val subsystem = current<MySubsystem>()
    private val timer = Timer()
    
    init {
        // Optional: Make non-interruptible
        // interruptible = false
    }
    
    override fun initialize() { }      // Called once at start
    override fun execute() { }         // Called repeatedly
    override fun isFinished() = false  // Return true when done
    override fun end(interrupted: Boolean) { }  // Cleanup
}
```

## Non-Interruptible Commands

Protect critical commands from being canceled:

```kotlin
class CriticalCommand : Command() {
    private val drive = current<DriveSubsystem>()
    
    init {
        interruptible = false  // Cannot be interrupted!
    }
    
    override fun execute() {
        // Critical operation
    }
}
```

**Behavior:**
- Conflicting commands will be **rejected** (not scheduled)
- Telemetry shows `[NON-INTERRUPTIBLE]` tag
- Use for: emergency stops, calibration, safety-critical ops
- Keep short and add timeouts!

## Built-in Commands Quick Lookup

| Command | Purpose | Example |
|---------|---------|---------|
| `InstantCommand` | One-shot action | `InstantCommand { println("Hi") }` |
| `WaitCommand` | Wait for duration | `WaitCommand(2.0)` |
| `WaitUntilCommand` | Wait for condition | `WaitUntilCommand { ready() }` |
| `SequentialCommandGroup` | Run in order | `SequentialCommandGroup(cmd1, cmd2)` |
| `ParallelCommandGroup` | Run together (all finish) | `ParallelCommandGroup(cmd1, cmd2)` |
| `ParallelRaceGroup` | Run together (first finish) | `ParallelRaceGroup(cmd1, cmd2)` |

## Command Groups At a Glance

### Sequential (One After Another)
```kotlin
SequentialCommandGroup(
    command1,  // Runs first
    command2,  // Then this
    command3   // Then this
)
// Total time = sum of all commands
```

### Parallel - All Complete
```kotlin
ParallelCommandGroup(
    command1,  // All run
    command2,  // at the same
    command3   // time
)
// Finishes when ALL are done
```

### Parallel - Race (First Wins)
```kotlin
ParallelRaceGroup(
    command1,  // Whichever
    command2   // finishes first
)
// Finishes when ANY is done
```

## Fluent API Cheatsheet

```kotlin
// Chain sequentially
cmd1.andThen(cmd2).andThen(cmd3)

// Run in parallel
cmd1.alongWith(cmd2)

// Add timeout
cmd.withTimeout(5.0)

// Race condition
cmd.raceWith(WaitCommand(3.0))

// Before action
cmd.beforeStarting { setup() }

// After action
cmd.andThenRun { cleanup() }
```

## Common Patterns

### Timed Sequence
```kotlin
SequentialCommandGroup(
    Action1(),
    WaitCommand(0.5),
    Action2()
)
```

### Action with Timeout
```kotlin
ParallelRaceGroup(
    LongRunningAction(),
    WaitCommand(5.0)  // Safety timeout
)
```

### Wait for Ready
```kotlin
SequentialCommandGroup(
    StartAction(),
    WaitUntilCommand { isReady() },
    NextAction()
)
```

### Parallel Preparation
```kotlin
ParallelCommandGroup(
    PrepareSubsystem1(),
    PrepareSubsystem2(),
    PrepareSubsystem3()
)
```

### Complex Sequence
```kotlin
SequentialCommandGroup(
    // Step 1: Prepare (parallel)
    ParallelCommandGroup(
        PrepA(),
        PrepB()
    ),
    
    // Step 2: Action with timeout
    ParallelRaceGroup(
        MainAction(),
        WaitCommand(5.0)
    ),
    
    // Step 3: Cleanup
    WaitCommand(0.5),
    Reset()
)
```

## Scheduling Commands

### From TeleOp
```kotlin
class MyTeleOp : ThreadedOpMode() {
    override fun mainLoop() {
        if (gamepad1Ex.a.wasPressed()) {
            commandScheduler.schedule(MyCommand())
        }
    }
}
```

### Cancel Commands
```kotlin
// Cancel specific command
commandScheduler.cancel(myCommand)

// Cancel all
commandScheduler.cancelAll()
```

## Subsystem Access in Commands

```kotlin
class MyCommand : Command() {
    // Get proxy (automatic requirement tracking)
    private val drive = current<DriveSubsystem>()
    private val shooter = current<ShooterSubsystem>()
    
    override fun execute() {
        // Just call methods - transparent threading!
        drive.setPower(0.5, 0.5)
        shooter.shoot()
    }
}
```

## Timing

```kotlin
class TimedCommand : Command() {
    private val timer = Timer()
    
    override fun initialize() {
        timer.start()
    }
    
    override fun isFinished(): Boolean {
        return timer.elapsedSeconds() > 2.0
    }
    
    override fun end(interrupted: Boolean) {
        timer.stop()
    }
}
```

## Instant Actions

```kotlin
// Simple lambda
InstantCommand { println("Hello") }

// With subsystem
InstantCommand {
    val drive = RobotThread.current<DriveSubsystem>()
    drive.reset()
}
```

## Conditional Logic

```kotlin
class ConditionalCommand : Command() {
    override fun execute() {
        if (condition1) {
            // Do something
        } else if (condition2) {
            // Do something else
        }
    }
    
    override fun isFinished() = someCondition
}
```

## Error Handling

Commands automatically handle errors:
- Sequential: Continues to next command
- Parallel: Marks failed command as finished
- Race: Error counts as finish
- All: Proper cleanup in end()

## Tips

1. **Keep execute() fast** - It runs at 50Hz
2. **Use Timer for delays** - Built-in timer support
3. **Group for complex behaviors** - Compose simple commands
4. **Add timeouts** - Use `.withTimeout()` or ParallelRaceGroup
5. **Check isFinished()** - Scheduler handles waiting
6. **Clean up in end()** - Always stop motors/timers

## Quick Start

1. Create command extending `Command`
2. Get subsystems with `current<T>()`
3. Implement lifecycle methods
4. Schedule with `commandScheduler.schedule(cmd)`

That's it! 🚀

