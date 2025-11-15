# Command and Threading System Guide

## Overview

This guide explains how the command and threading system works in this robot codebase and how to properly use it.

## Architecture

### Threading Model

The robot uses a multi-threaded architecture:

1. **Main Thread**: Runs the OpMode's `mainLoop()` 
2. **CommandScheduler Thread**: Manages command lifecycle and execution
3. **Subsystem Threads**: Each subsystem runs on its own thread

### Key Components

#### 1. CommandScheduler

The `CommandScheduler` is a singleton that:
- Runs on its own dedicated thread (20ms update interval)
- Manages command lifecycle: `initialize()` → `periodic()` → `isFinished()` → `end()`
- Enforces "one command per subsystem" rule
- Handles command interruption and cancellation
- Provides thread-safe command scheduling

**Access**: Available via `commandScheduler` property in `ThreadedOpMode`.

#### 2. Commands

Commands are actions that use subsystems. They run on the CommandScheduler thread.

**Base class**: `Command`

**Lifecycle methods**:
- `initialize()`: Called once when command is scheduled
- `periodic()`: Called repeatedly while command is running (20ms intervals)
- `isFinished()`: Returns true when command should end
- `end(interrupted: Boolean)`: Called once when command ends

#### 3. Subsystems

Subsystems control hardware and run on their own threads.

**Base class**: `Subsystem`

**Key methods**:
- `periodic()`: Called repeatedly on subsystem thread (20ms intervals)
- `init()`: Called when subsystem thread starts
- `end()`: Called when subsystem thread stops

## Cross-Thread Communication

Commands execute on the CommandScheduler thread, but subsystems run on their own threads. Commands call subsystem methods **directly across threads**.

### Accessing Subsystems in Commands

Use `current<T>()` to get a reference to a subsystem:

```kotlin
class DriveForward(val seconds: Double) : Command() {
    // Get subsystem reference
    private val drive = current<DriveSubsystem>()
    
    override fun periodic() {
        drive.setPower(0.5, 0.5)  // Direct cross-thread call
    }
    
    override fun isFinished(): Boolean {
        return drive.currentState == DriveState.IDLE  // Direct read
    }
}
```

**What it does**:
- Returns direct reference to the subsystem
- Automatically adds subsystem to command's requirements
- Method calls happen directly across threads
- Subsystem must ensure thread-safety

### Thread Safety

Since commands call subsystem methods directly from a different thread, subsystems must ensure thread-safety:

1. **Use `@Volatile` for state variables** that commands read:
   ```kotlin
   @Volatile
   var currentState: SubsystemState = SubsystemState.IDLE
   ```

2. **Use `synchronized` blocks** for complex operations if needed:
   ```kotlin
   synchronized(this) {
       // Critical section
   }
   ```

3. **Keep public methods thread-safe** - they can be called from any thread

## How to Use Commands Properly

### ❌ WRONG: Calling methods directly

```kotlin
// DON'T DO THIS
if (gamepad2Ex.b.wasPressed()) {
    kickerSubsystem.triggerKicker()  // Bypasses command system
}

if (gamepad2Ex.dpadUp.wasPressed()) {
    ShootAndTurn().periodic()  // Bypasses command scheduler
}
```

### ✅ CORRECT: Scheduling commands

```kotlin
// DO THIS
if (gamepad2Ex.b.wasPressed()) {
    commandScheduler.schedule(TriggerKicker())  // Schedules properly
}

if (gamepad2Ex.dpadUp.wasPressed()) {
    commandScheduler.schedule(ShootAndTurn())  // Schedules properly
}
```

## Example Commands

### Simple Command (Instant)

```kotlin
class TriggerKicker : Command(false) {
    private val kicker = current<KickerSubsystem>()
    
    private var triggered = false

    override fun initialize() {
        triggered = false
    }

    override fun periodic() {
        if (!triggered) {
            kicker.triggerKicker()  // Direct cross-thread call
            triggered = true
        }
    }

    override fun isFinished(): Boolean {
        return triggered && kicker.currentState == KickerState.IDLE
    }
}
```

### Sequential Command Group

```kotlin
fun Shoot(): SequentialCommandGroup {
    return SequentialCommandGroup(
        TriggerKicker(),
        WaitCommand(0.5),
        TriggerKicker()
    )
}
```

### Parallel Command Group

```kotlin
fun DriveAndIntake(): ParallelCommandGroup {
    return ParallelCommandGroup(
        DriveForward(2.0),
        RunIntake()
    )
}
```

## Command Properties

### Interruptibility

Commands can be interruptible or non-interruptible:

```kotlin
// Interruptible (default)
class MyCommand : Command() { ... }

// Non-interruptible
class CriticalCommand : Command(false) {
    // Cannot be interrupted by other commands
}
```

### Requirements

Commands automatically declare requirements when they call `current<T>()`:

```kotlin
class DriveCommand : Command() {
    private val drive = current<DriveSubsystem>()  // Adds DriveSubsystem to requirements
    
    // CommandScheduler ensures only one command can use DriveSubsystem at a time
}
```

## Threading Safety Rules

### ✅ Safe Operations

1. **Reading @Volatile properties** from any thread
   ```kotlin
   val state = kicker.currentState  // Safe if @Volatile
   ```

2. **Calling thread-safe subsystem methods** from commands
   ```kotlin
   drive.setPower(0.5, 0.5)  // Safe if method is thread-safe
   ```

3. **Scheduling commands** from any thread
   ```kotlin
   commandScheduler.schedule(MyCommand())  // Thread-safe
   ```

### ❌ Unsafe Operations (Don't Do)

1. **Calling command.periodic()** directly
   ```kotlin
   MyCommand().periodic()  // BAD - bypasses scheduler
   ```

2. **Reading non-volatile fields** from other threads
   ```kotlin
   val value = subsystem.nonVolatileField  // BAD - not thread-safe
   ```

## Built-in Commands

### WaitCommand

Wait for a duration:

```kotlin
WaitCommand(2.0)  // Wait 2 seconds
WaitCommand.millis(500)  // Wait 500 milliseconds
```

### WaitUntilCommand

Wait until a condition is true:

```kotlin
WaitUntilCommand { shooter.isReady() }

// With timeout
WaitUntilCommand(
    condition = { shooter.isReady() },
    timeoutSeconds = 5.0
)
```

### InstantCommand

Execute a lambda instantly:

```kotlin
InstantCommand { telemetry.addData("Status", "Done") }
```

### SequentialCommandGroup

Run commands in sequence:

```kotlin
SequentialCommandGroup(
    DriveForward(1.0),
    WaitCommand(0.5),
    TurnLeft(90.0)
)
```

### ParallelCommandGroup

Run commands in parallel (finishes when all complete):

```kotlin
ParallelCommandGroup(
    DriveForward(2.0),
    RunIntake()
)
```

### ParallelRaceGroup

Run commands in parallel (finishes when first one completes):

```kotlin
ParallelRaceGroup(
    DriveForward(10.0),
    WaitCommand(2.0)  // Times out after 2 seconds
)
```

## Best Practices

1. **Always schedule commands** - never call `periodic()` directly
2. **Use `current()` to access subsystems** in commands
3. **Mark state fields `@Volatile`** in subsystems for thread-safe reading
4. **Ensure subsystem methods are thread-safe** - they may be called from other threads
5. **Use command groups** to compose complex behaviors
6. **Set interruptible = false** only for critical operations
7. **Keep `periodic()` fast** - it runs every 20ms
8. **Use `initialize()` for setup** - it runs only once

## Common Patterns

### State-Based Command

```kotlin
class WaitForState(val targetState: SubsystemState) : Command() {
    private val subsystem = current<MySubsystem>()
    
    override fun isFinished(): Boolean {
        return subsystem.currentState == targetState
    }
}
```

### Timed Command

```kotlin
class TimedCommand(val seconds: Double) : Command() {
    private val timer = Timer()
    
    override fun initialize() {
        timer.start()
    }
    
    override fun isFinished(): Boolean {
        return timer.elapsedSeconds() >= seconds
    }
}
```

### Conditional Command

```kotlin
class ConditionalDrive : Command() {
    private val drive = current<DriveSubsystem>()
    private val sensor = current<SensorSubsystem>()
    
    override fun periodic() {
        if (sensor.hasTarget) {
            drive.setPower(0.5, 0.5)
        } else {
            drive.setPower(0.0, 0.0)
        }
    }
}
```

## Troubleshooting

### Command doesn't run
- **Check**: Did you schedule it? Use `commandScheduler.schedule(command)`
- **Check**: Is another command using the same subsystem?

### State reads return wrong values
- **Check**: Is the property marked `@Volatile` in the subsystem?
- **Check**: Is there a race condition in the subsystem?

### Command never finishes
- **Check**: Does `isFinished()` eventually return true?
- **Check**: Are you reading the correct state variable?

### Thread safety issues
- **Check**: Are subsystem public methods thread-safe?
- **Check**: Are you accessing non-volatile fields from commands?

## Summary

| Action | Use | Example |
|--------|-----|---------|
| Access subsystem | `current<T>()` | `val drive = current<DriveSubsystem>()` |
| Call subsystem method | Direct call | `drive.setPower(0.5, 0.5)` |
| Read subsystem state | Direct read (@Volatile) | `drive.currentState` |
| Schedule command | `commandScheduler.schedule()` | `commandScheduler.schedule(MyCommand())` |
| Wait for duration | `WaitCommand()` | `WaitCommand(2.0)` |
| Wait for condition | `WaitUntilCommand()` | `WaitUntilCommand { ready }` |
| Run in sequence | `SequentialCommandGroup()` | `SequentialCommandGroup(cmd1, cmd2)` |
| Run in parallel | `ParallelCommandGroup()` | `ParallelCommandGroup(cmd1, cmd2)` |

---

**Remember**: Commands call subsystems directly across threads. Subsystems must ensure thread-safety for all public methods!
