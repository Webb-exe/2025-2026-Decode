# Threading Architecture Diagram

## System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          MAIN THREAD                                 │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │               ThreadedOpMode.mainLoop()                     │     │
│  │                                                              │     │
│  │  • Reads gamepad inputs                                     │     │
│  │  • Updates robot state                                      │     │
│  │  • Schedules commands → commandScheduler.schedule(cmd)      │     │
│  │  • Updates telemetry                                        │     │
│  └────────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ schedule(command)
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    COMMAND SCHEDULER THREAD                          │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │              CommandScheduler.runLoop()                     │     │
│  │                                                              │     │
│  │  Active Commands:                                           │     │
│  │  ┌──────────────────────────────────────┐                  │     │
│  │  │ Command 1:                            │                  │     │
│  │  │  • initialize() → periodic() → end()  │                  │     │
│  │  │  • Uses current<Subsystem>()          │────────┐         │     │
│  │  │  • Calls methods directly             │        │         │     │
│  │  └──────────────────────────────────────┘        │         │     │
│  │                                                   │         │     │
│  │  ┌──────────────────────────────────────┐        │         │     │
│  │  │ Command 2:                            │        │         │     │
│  │  │  • initialize() → periodic() → end()  │        │         │     │
│  │  │  • Uses current<Subsystem>()          │────────┼────┐    │     │
│  │  │  • Calls methods directly             │        │    │    │     │
│  │  └──────────────────────────────────────┘        │    │    │     │
│  └───────────────────────────────────────────────────┼────┼────┘     │
└────────────────────────────────────────────────────┼────┼───────────┘
                                                      │    │
                   Direct Cross-Thread Call ──────────┘    │
                   (Thread-safe method)                    │
                                                           │
        ┌──────────────────────────────────────────────────┼─────────┐
        │                                                   │         │
        ▼                                                   ▼         ▼
┌───────────────────┐                             ┌────────────────────┐
│ SUBSYSTEM THREAD  │                             │ SUBSYSTEM THREAD   │
│                   │                             │                    │
│ KickerSubsystem   │                             │ SpindexerSubsystem │
│                   │                             │                    │
│ • periodic()      │                             │ • periodic()       │
│   Called every    │                             │   Called every     │
│   20ms            │                             │   20ms             │
│                   │                             │                    │
│ • @Volatile       │                             │ • @Volatile        │
│   currentState    │◄────── Direct Read          │   currentState     │
│                   │                             │                    │
│ • Hardware        │                             │ • Hardware         │
│   Control         │                             │   Control          │
│                   │                             │                    │
│ • Thread-safe     │                             │ • Thread-safe      │
│   Public Methods  │                             │   Public Methods   │
│                   │                             │                    │
└───────────────────┘                             └────────────────────┘
```

## Data Flow Examples

### Example 1: Scheduling a Command

```
1. Teleop detects button press
   └─► Main Thread: commandScheduler.schedule(TriggerKicker())

2. Command queued in CommandScheduler
   └─► Scheduler Thread: Picks up command from queue
       └─► Calls command.initialize()
       └─► Starts calling command.periodic() every 20ms

3. Command calls subsystem method
   └─► Command Thread: kicker.triggerKicker()
       └─► Direct cross-thread method call
           └─► Subsystem method executes (must be thread-safe)

4. Subsystem updates state
   └─► Kicker Thread: periodic() continues running
       └─► Updates hardware
       └─► Updates @Volatile currentState

5. Command checks completion
   └─► Command Thread: kicker.currentState == KickerState.IDLE
       └─► Direct read of @Volatile property (thread-safe)
       └─► Returns true when complete

6. Scheduler ends command
   └─► Scheduler Thread: command.end(interrupted = false)
       └─► Removes command from active list
```

### Example 2: Direct Cross-Thread Communication

```
WRITING (Method Calls):
┌─────────────┐                                 ┌──────────────┐
│   Command   │ ─────────────────────────────► │  Subsystem   │
│   Thread    │      Direct Method Call         │   Thread     │
│             │      (cross-thread)             │              │
│             │      subsystem.method()         │  Executes    │
└─────────────┘                                 └──────────────┘
  Scheduler                                       Subsystem
    20ms                                            20ms
                 Method must be thread-safe

READING (State Access):
┌─────────────┐                                 ┌──────────────┐
│   Command   │ ─────────────────────────────► │  Subsystem   │
│   Thread    │       Direct Read               │   @Volatile  │
│             │       (thread-safe)             │  Property    │
└─────────────┘                                 └──────────────┘
  Scheduler                                       Any Thread
    20ms                                         Safe Read
```

## Thread Timing

```
Main Thread Loop:        ████████████████████████████████████████
                         ↓ every ~20ms
                         schedule commands, update telemetry

Command Scheduler:       ████████████████████████████████████████
                         ↓ every 20ms
                         initialize, periodic, isFinished, end

Subsystem Thread 1:      ████████████████████████████████████████
                         ↓ every 20ms
                         periodic(), telemetry

Subsystem Thread 2:      ████████████████████████████████████████
                         ↓ every 20ms
                         periodic(), telemetry

All threads run independently but coordinate through:
• Direct method calls (cross-thread, must be thread-safe)
• @Volatile properties (for state reads)
• CommandScheduler (for command lifecycle)
```

## Thread Safety Mechanisms

### 1. @Volatile Properties
```kotlin
@Volatile
var currentState: KickerState = KickerState.IDLE
    private set  // Only subsystem thread can write
                 // Any thread can read safely
```

### 2. Thread-Safe Methods
```kotlin
// Subsystem methods can be called from any thread
// Must ensure thread-safety internally
fun setPower(power: Double) {
    // This can be called from command thread
    // Must be thread-safe (use synchronized if needed)
    motorPower = power
}
```

### 3. Command Requirements
```kotlin
// CommandScheduler ensures only one command per subsystem
requirements: MutableSet<Class<out Subsystem>>

// If Command A uses DriveSubsystem
// And Command B also uses DriveSubsystem
// Then scheduling B will cancel A (if interruptible)
```

## Key Invariants

1. **One Command Per Subsystem**
   - Enforced by CommandScheduler
   - Based on requirements set

2. **Thread Ownership**
   - Each subsystem owns its hardware
   - Subsystem thread runs periodic() continuously
   - Commands call subsystem methods directly (cross-thread)

3. **Thread-Safety Requirements**
   - Subsystem public methods must be thread-safe
   - Use @Volatile for state variables
   - Use synchronized blocks if needed for complex operations

4. **Command Lifecycle**
   - Always runs on CommandScheduler thread
   - initialize() → periodic() → isFinished() → end()
   - Subsystem calls happen directly

## Performance Characteristics

- **Latency**: Method calls execute immediately (no queueing)
- **Throughput**: All threads run in parallel at 50 Hz
- **Safety**: Must be ensured by subsystem implementation
- **Scalability**: Add more subsystems = more threads = better parallelism

## Summary

```
Main Thread       → Schedules commands
                 ↓
Scheduler Thread  → Runs commands
                 ↓ (direct call)
Subsystem Thread  ← Controls hardware
                 ↑ (direct read)
Scheduler Thread  ← Reads state to check completion
```

The system achieves:
- ✅ Parallel execution of subsystems
- ✅ Simple command writing
- ✅ Predictable timing
- ⚠️ Subsystems must ensure thread-safety

## Thread-Safety Best Practices

1. **Mark state variables @Volatile** if they're read from commands
2. **Keep method operations atomic** when possible
3. **Use synchronized blocks** for complex operations if needed
4. **Avoid long-running operations** in subsystem methods
5. **Document thread-safety** in subsystem method comments

---

**Key Difference**: Commands call subsystem methods directly across threads. Subsystems are responsible for ensuring thread-safety.
