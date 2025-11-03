# How RobotTelemetry Updates and Clears for Threads

## TL;DR - The Simple Explanation

**Threads write data → Data persists → Main loop displays everything**

- Each thread writes to its own "namespace" (storage bucket)
- Data stays there until the thread writes new data
- Main loop reads from ALL namespaces and displays them
- No data disappears between updates!

---

## Detailed Explanation

### 1. How Data Storage Works

```
RobotTelemetry has storage like this:

┌─────────────────────────────────────┐
│  Main                               │
│  ├─ Runtime: 15.23 s               │
│  └─ Loop Time: 20 ms               │
├─────────────────────────────────────┤
│  MovementThread                     │
│  ├─ Status: Running                │
│  ├─ Speed: 100%                    │
│  └─ Drive Y: 0.85                  │
├─────────────────────────────────────┤
│  VisionThread                       │
│  ├─ Status: Running                │
│  ├─ Targets: true                  │
│  └─ Target X: -2.34°               │
├─────────────────────────────────────┤
│  TurretThread                       │
│  ├─ Status: Tracking               │
│  └─ Motor Power: 0.12              │
└─────────────────────────────────────┘

Each namespace is a ConcurrentHashMap
Data persists independently per namespace
```

---

## 2. Thread Update Cycle

### Example Timeline:

```
Time    MovementThread (10ms)    VisionThread (100ms)    Main Loop (50ms)
─────   ────────────────────────  ───────────────────────  ─────────────────

0ms     Writes: Speed=100%        Writes: Targets=true    Writes: Runtime=0s
        Status=Running            Target X=-2.34°         Reads ALL data
                                                          Displays everything
                                                          
10ms    Writes: Speed=100%        [No update yet]         [No update yet]
        Status=Running
        Drive Y=0.85
        
20ms    Writes: Speed=80%         [No update yet]         [No update yet]
        Status=Running
        Drive Y=0.50
        
50ms    Writes: Speed=80%         [No update yet]         Writes: Runtime=50ms
        Status=Running                                    Reads ALL data
        Drive Y=0.40                                      Displays:
                                                            - Main: Runtime=50ms
                                                            - Movement: Speed=80%, Y=0.40
                                                            - Vision: Targets=true, X=-2.34° (still there!)
                                                            - Turret: Status=Tracking (still there!)
                                                          
100ms   Writes: Speed=60%         Writes: Targets=false   Writes: Runtime=100ms
        Status=Running            Target X=0.00°          Reads ALL data
                                                          Displays UPDATED data
```

**Key Points:**
- MovementThread updates every 10ms → its namespace gets new data every 10ms
- VisionThread updates every 100ms → its namespace keeps OLD data for 100ms
- Main loop reads at 50ms → displays whatever is currently in each namespace
- **Data from VisionThread at 0ms is STILL VISIBLE at 50ms** because it hasn't been overwritten!

---

## 3. How `addData()` Works - WITH AUTO-CLEAR

### Each thread loop starts with a clean slate!

```java
// RobotThread automatically does this BEFORE runLoop():
telemetry.clearNamespace();  // Clear old data from last loop

// Then your runLoop() executes:
@Override
protected void runLoop() {
    telemetry.addData("Status", "Running");   // Writes fresh to MovementThread namespace
    telemetry.addData("Speed", 100);          // Adds to MovementThread namespace
    telemetry.addData("Drive Y", 0.85);       // Adds to MovementThread namespace
}

// Storage now looks like:
MovementThread {
    Status: "Running"      <- ONLY these keys exist
    Speed: 100             <- Old keys from previous loop are GONE
    Drive Y: 0.85          <- Clean slate each iteration
}
```

**Important:** 
- Each loop iteration starts with cleared namespace
- Only keys you write in THIS loop appear in telemetry
- No stale data from previous loops!
- This prevents bugs where conditional data persists when it shouldn't

---

## 4. How `update()` Works

### When main loop calls `telemetry.update()`:

```java
// In main loop:
telemetry.setNamespace("Main");
telemetry.addData("Runtime", runtime.seconds());
telemetry.update();  // ← This is where the magic happens
```

**What happens during `update()`:**

```
Step 1: Loop through ALL namespaces (Main, MovementThread, VisionThread, TurretThread)
Step 2: For EACH namespace:
        - Read ALL key-value pairs currently stored
        - Add them to FTC telemetry display
        - Add them to PanelsTelemetry dashboard
Step 3: Call ftcTelemetry.update() → Sends to Driver Station
Step 4: Call panelsTelemetry.update() → Sends to Dashboard
```

**Critical Understanding:**
- `update()` is a READ operation, NOT a write operation
- `update()` does NOT clear any data
- `update()` displays a snapshot of ALL current data from ALL threads
- Each namespace's data remains intact after `update()`

---

## 5. How Clearing Works

### Automatic Clearing (Built-In)

**Each thread loop automatically clears its namespace before running:**

```java
// Thread at timestamp 0ms:
telemetry.addData("Status", "Searching");
telemetry.addData("Targets", 0);
telemetry.addData("Details", "Looking...");

// Thread at timestamp 100ms (NEW LOOP):
// ↓ Automatic clear happens here ↓
telemetry.addData("Status", "Tracking");
telemetry.addData("Targets", 3);
// "Details" key is now GONE because we didn't re-add it!
```

**Why this is better:**
- ✅ No stale data from conditional logic
- ✅ Predictable - you see exactly what current loop wrote
- ✅ Prevents bugs where old keys linger

### Manual Clear Methods (Rarely Needed)

```java
// Clear specific key from current namespace:
telemetry.removeData("OldKey");

// Clear ALL data from current namespace:
telemetry.clearNamespace();

// Clear ALL data from ALL namespaces:
telemetry.clearAll();
```

**When would you use these?**
- `removeData()`: When you want to remove a specific telemetry line that's no longer relevant
- `clearNamespace()`: When a thread is stopping or restarting fresh
- `clearAll()`: When transitioning between OpMode phases (rarely needed)

---

## 6. Data Persistence Between Main Loop Updates

### Scenario: VisionThread updates slower than main loop

```
Time    VisionThread (100ms interval)     Main Loop (50ms interval) Sees
─────   ─────────────────────────────────  ─────────────────────────────

0ms     CLEARS namespace, then writes:    Status: Tracking
        Status=Tracking                    Targets: true
        Targets=true                       Target X: -2.34°
        Target X=-2.34°
                
50ms    [No new loop yet]                  Status: Tracking  ← STILL THERE
        [Namespace unchanged]              Targets: true     ← STILL THERE
                                            Target X: -2.34°  ← STILL THERE
                
100ms   CLEARS namespace, then writes:    Status: No Targets ← UPDATED
        Status=No Targets                  Targets: false     ← UPDATED
        Targets=false                      (Target X is GONE) ← Not written this loop
```

**This is the SOLUTION to your original problem!**

In the old system, if VisionThread didn't update for 50ms, its telemetry would disappear.
Now:
- The data from 0ms stays visible at 50ms because VisionThread hasn't run a new loop yet
- When VisionThread runs at 100ms, it clears and writes fresh data
- Main loop always shows the most recent complete update from each thread

---

## 7. Why ThreadLocal is Key

```java
private ThreadLocal<String> currentNamespace = ThreadLocal.withInitial(() -> "Main");
```

**ThreadLocal means:**
- Each Java thread has its own copy of `currentNamespace`
- When RobotThread starts, it calls `telemetry.setNamespace(getName())`
- From that point on, ANY call to `telemetry.addData()` from that thread uses its namespace
- You never have to manually set it again!

**Example:**
```java
// MovementThread running in Thread-1:
telemetry.addData("Speed", 100);  // Goes to "MovementThread" namespace

// VisionThread running in Thread-2:
telemetry.addData("Targets", 5);  // Goes to "VisionThread" namespace

// Main loop running in Thread-3:
telemetry.setNamespace("Main");
telemetry.addData("Runtime", 10);  // Goes to "Main" namespace
```

Each thread automatically writes to its own bucket!

---

## 8. Common Patterns (WITH AUTO-CLEAR)

### Pattern 1: Continuous Updates (MovementThread)
```java
@Override
protected void runLoop() {
    // Namespace is auto-cleared before this runs
    // Write fresh data every loop
    telemetry.addData("Speed", currentSpeed);
    telemetry.addData("Position", currentPos);
    telemetry.addData("Heading", heading);
    // Only these 3 keys will appear - clean and simple!
}
```

### Pattern 2: State-Based Updates (TurretThread) - CLEAN!
```java
@Override
protected void runLoop() {
    // Namespace is auto-cleared before this runs
    
    if (!enabled) {
        telemetry.addData("Status", "Disabled");
        return;  // ONLY "Status" key exists - perfect!
    }
    
    if (tracking) {
        telemetry.addData("Status", "Tracking");
        telemetry.addData("Target ID", targetId);
        telemetry.addData("Error", error);
        // These 3 keys appear ONLY when tracking
    } else {
        telemetry.addData("Status", "Searching");
        // ONLY "Status" appears - no leftover "Target ID" or "Error"!
    }
}
```

### Pattern 3: Error Handling - CLEAN!
```java
@Override
protected void runLoop() {
    // Namespace is auto-cleared before this runs
    
    try {
        // Normal operation
        telemetry.addData("Status", "Running");
        telemetry.addData("Data", someValue);
        telemetry.addData("Last Update", timestamp);
    } catch (Exception e) {
        // Error state
        telemetry.addData("Status", "ERROR");
        telemetry.addData("Message", e.getMessage());
        // Only these 2 keys appear during error - no stale "Data" or "Last Update"!
    }
}
```

**Benefits of Auto-Clear:**
- ✅ No accidental leftover data from previous loops
- ✅ Conditional logic works as expected
- ✅ Easier to debug - telemetry shows exactly what current state wrote
- ✅ More predictable behavior

---

## 9. Performance Considerations

### Why This is Efficient

1. **ConcurrentHashMap**: Thread-safe with minimal locking
2. **Write-Heavy, Read-Light**: Threads write often (100Hz), main loop reads occasionally (20Hz)
3. **No Copying**: Data is stored once, read from storage during update
4. **Lazy Evaluation**: Only formats strings when update() is called

### Typical Performance
```
MovementThread: 10ms interval = 100 writes/second
VisionThread:   100ms interval = 10 writes/second  
Main Loop:      50ms interval = 20 reads/second

Total: ~110 write operations/second
       ~20 read operations/second
       
CPU overhead: < 1% on modern hardware
```

---

## 10. Summary Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                      RobotTelemetry                          │
│                                                              │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐           │
│  │   Main     │  │ Movement   │  │  Vision    │  ...      │
│  │ Namespace  │  │ Namespace  │  │ Namespace  │           │
│  │            │  │            │  │            │           │
│  │ Key→Value  │  │ Key→Value  │  │ Key→Value  │           │
│  │ Key→Value  │  │ Key→Value  │  │ Key→Value  │           │
│  └────────────┘  └────────────┘  └────────────┘           │
│        ▲              ▲              ▲                      │
│        │              │              │                      │
│  ┌─────┴──────┬───────┴────┬─────────┴──────┐            │
│  │            │            │                 │            │
│  │ Main Loop  │ Movement   │  Vision Thread  │            │
│  │ Writes     │ Thread     │  Writes         │            │
│  │ to "Main"  │ Writes to  │  to "Vision"    │            │
│  │            │ "Movement" │                 │            │
│  └────────────┴────────────┴─────────────────┘            │
│                                                              │
│  Main Loop calls telemetry.update() every 50ms:            │
│  ──────────────────────────────────────────────            │
│  1. Read from ALL namespaces                               │
│  2. Display on Driver Station                              │
│  3. Display on PanelsTelemetry                             │
│  4. Data remains in storage (NOT cleared)                  │
└──────────────────────────────────────────────────────────────┘
```

---

## Key Takeaways

✅ **Threads write independently** - Each thread updates its own namespace at its own rate

✅ **Auto-clear each loop** - Each thread loop starts with cleared namespace for clean state

✅ **Data persists between main loop updates** - Thread data stays visible until thread runs again

✅ **Main loop displays everything** - One `update()` call shows all thread data

✅ **No synchronization needed** - ThreadLocal + ConcurrentHashMap handle thread safety

✅ **Just use `telemetry.addData()`** - The namespace is automatically set and cleared for each thread

✅ **Call `telemetry.update()` once** - Usually in the main loop, shows everything

✅ **No stale data** - Only keys written in current loop appear in telemetry

