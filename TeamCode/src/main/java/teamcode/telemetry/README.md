# RobotTelemetry - Unified Thread-Safe Telemetry System

## Overview

`RobotTelemetry` is a unified, thread-safe telemetry system that solves the problem of telemetry data disappearing when multiple threads update at different rates.

## The Problem It Solves

When you have multiple threads (MovementThread, VisionThread, TurretThread, etc.) each updating at different rates, the old approach would cause telemetry to disappear because:
- Thread A updates at 100Hz and adds its telemetry
- Thread B updates at 50Hz and adds its telemetry
- Main loop calls `telemetry.update()` at 20Hz
- Result: By the time the main loop updates, some thread's data is old or missing!

## The Solution

`RobotTelemetry` stores telemetry data in thread-safe namespaces so:
- Each thread can update its data independently at its own rate
- Data persists until explicitly changed or cleared
- Main loop displays ALL data from ALL threads, always up-to-date
- Automatically works with both FTC Driver Station and PanelsTelemetry dashboard

## Usage

### In Your Threads (MovementThread, VisionThread, etc.)

Just use `addTelemetry()` like normal - the namespace is automatically set to your thread name:

```java
@Override
protected void runLoop() {
    // Add telemetry data - it's thread-safe and persists!
    addTelemetry("Status", "Running");
    addTelemetry("Speed", String.format("%.2f", speed));
    addTelemetry("Position", currentPosition);
    
    // Your thread logic here...
}
```

### In Your Main OpMode

Just use `telemetry.addData()` and `telemetry.update()` like you always have:

```java
@Override
protected void runOpModeThreaded() {
    while (opModeIsActive()) {
        // Add main loop telemetry
        telemetry.setNamespace("Main");  // Optional - already set by default
        telemetry.addData("Runtime", runtime.seconds());
        telemetry.addData("Controls", "A=Enable, B=Disable");
        
        // Update everything - FTC Driver Station + PanelsTelemetry dashboard
        // All thread data is automatically included!
        telemetry.update();
    }
}
```

## How It Works

1. **ThreadedOpMode** creates a single `RobotTelemetry` instance
2. Each `RobotThread` gets a reference to this shared telemetry
3. Each thread stores data in its own namespace (thread name)
4. When you call `telemetry.update()` in the main loop:
   - All namespaces (Main, MovementThread, VisionThread, etc.) are displayed
   - Data is organized with headers like `=== MOVEMENTTHREAD ===`
   - Updates both FTC Driver Station AND PanelsTelemetry dashboard

## Features

- **Thread-Safe**: Uses ConcurrentHashMap for safe access from multiple threads
- **Persistent Data**: Telemetry doesn't disappear between updates
- **Automatic Organization**: Data organized by namespace/thread
- **PanelsTelemetry Integration**: Automatically works with PanelsTelemetry if available
- **Drop-In Replacement**: Use it just like standard FTC telemetry

## API Reference

### Basic Methods (Use These Everywhere)

```java
// Add data to current namespace
telemetry.addData("key", value);
telemetry.addData("key", "format %s", arg);

// Update all telemetry outputs
telemetry.update();
```

### Advanced Methods

```java
// Change namespace (usually automatic)
telemetry.setNamespace("MyNamespace");

// Get current namespace
String ns = telemetry.getNamespace();

// Clear data
telemetry.clearNamespace();        // Clear current namespace
telemetry.clearNamespace("Main");  // Clear specific namespace
telemetry.clearAll();              // Clear everything

// Get data programmatically
Map<String, Object> data = telemetry.getNamespaceData("MovementThread");
String formatted = telemetry.getFormattedString();

// PanelsTelemetry control
boolean enabled = telemetry.isPanelsTelemetryEnabled();
telemetry.setPanelsTelemetryEnabled(true);
```

## Example Output

```
=== MAIN ===
Runtime: 15.23 s
Loop Time: 20 ms

=== MOVEMENTTHREAD ===
Status: Running
Enabled: true
Speed Multiplier: 100.0%
Drive Y: 0.85
Drive X: -0.32
LF Power: 0.53

=== VISIONTHREAD ===
Status: Running
Targets Detected: true
Target X: -2.34°
Target Y: 1.45°
AprilTags Count: 2

=== TURRETTHREAD ===
Status: Tracking
Target Found: true
Target ID: 24
Motor Power: 0.12
PID Error: -2.34°
```

## Migration Guide

### Old Code (Before RobotTelemetry)

```java
// Had to manually track and update each thread's data
telemetry.addData("Movement Speed", movementThread.getSpeed());
telemetry.addData("Vision Targets", visionThread.hasTargets());
telemetry.addData("Turret Status", turretThread.isEnabled());
telemetry.update();
```

### New Code (With RobotTelemetry)

```java
// Threads update their own data automatically
// Just update once in main loop!
telemetry.addData("Runtime", runtime.seconds());
telemetry.update();  // Shows ALL data from ALL threads
```

## Notes

- The `telemetry` variable in `ThreadedOpMode` shadows the parent's telemetry, giving you the unified system
- Access original FTC telemetry via `ftcTelemetry` if needed
- PanelsTelemetry integration is automatic - no setup required!
- Each namespace maintains insertion order for keys
- Namespaces are ordered by creation time for consistent display

