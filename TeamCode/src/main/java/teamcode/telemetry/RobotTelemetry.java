package teamcode.telemetry;

import com.bylazar.telemetry.PanelsTelemetry;
import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified thread-safe telemetry system that works with multiple threads.
 * 
 * This is a drop-in replacement for standard FTC Telemetry that:
 * - Stores data thread-safely so each thread can update at different rates
 * - Data persists between updates (no more disappearing telemetry!)
 * - Automatically organizes data by thread/namespace
 * - Works with both FTC Telemetry and PanelsTelemetry dashboard
 * 
 * Usage:
 * - In threads: robotTelemetry.addData("key", value) 
 * - In main loop: robotTelemetry.update()
 * 
 * Each thread gets its own namespace automatically based on thread name.
 */
public class RobotTelemetry {
    // Store telemetry data per namespace (thread)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> telemetryData;
    private final ConcurrentHashMap<String, Long> namespaceOrder;
    private long nextOrder = 0;
    
    // The original FTC telemetry object
    private Telemetry ftcTelemetry;
    
    // PanelsTelemetry integration (optional)
    private PanelsTelemetry panelsTelemetry;
    private boolean usePanelsTelemetry = false;
    
    // Current namespace for thread-local operations
    private ThreadLocal<String> currentNamespace = ThreadLocal.withInitial(() -> "Main");
    
    // Staging buffer for atomic updates - prevents flickering
    private ThreadLocal<ConcurrentHashMap<String, Object>> stagingBuffer = ThreadLocal.withInitial(() -> null);
    private ThreadLocal<Boolean> stagingMode = ThreadLocal.withInitial(() -> false);
    
    public RobotTelemetry(Telemetry ftcTelemetry) {
        this.telemetryData = new ConcurrentHashMap<>();
        this.namespaceOrder = new ConcurrentHashMap<>();
        this.ftcTelemetry = ftcTelemetry;
        
        // Try to initialize PanelsTelemetry if available
        try {
            this.panelsTelemetry = PanelsTelemetry.INSTANCE;
            this.usePanelsTelemetry = true;
        } catch (Exception e) {
            // PanelsTelemetry not available, that's okay
            this.usePanelsTelemetry = false;
        }
    }
    
    /**
     * Set the namespace for the current thread.
     * This is automatically called by RobotThread, but you can call it manually.
     * @param namespace The namespace (usually thread name)
     */
    public void setNamespace(String namespace) {
        currentNamespace.set(namespace);
    }
    
    /**
     * Get the current namespace for this thread.
     */
    public String getNamespace() {
        return currentNamespace.get();
    }
    
    /**
     * Add telemetry data. Works like standard telemetry.addData().
     * Data is stored under the current thread's namespace.
     * @param key The telemetry key
     * @param value The telemetry value
     */
    public void addData(String key, Object value) {
        // Write to staging buffer if in staging mode
        if (stagingMode.get() && stagingBuffer.get() != null) {
            stagingBuffer.get().put(key, value);
            return;
        }
        
        // Normal mode - write directly to telemetry data
        String namespace = currentNamespace.get();
        telemetryData.computeIfAbsent(namespace, k -> {
            namespaceOrder.putIfAbsent(k, nextOrder++);
            return new ConcurrentHashMap<>();
        }).put(key, value);
    }
    
    /**
     * Add formatted telemetry data.
     * @param key The telemetry key
     * @param format Format string
     * @param args Format arguments
     */
    public void addData(String key, String format, Object... args) {
        String value = String.format(format, args);
        addData(key, value);
    }
    
    /**
     * Add data to a specific namespace (useful for main loop to add data).
     * Note: This method bypasses staging mode and writes directly.
     * @param namespace The namespace
     * @param key The telemetry key
     * @param value The telemetry value
     */
    public void addData(String namespace, String key, Object value) {
        // This method always writes directly, bypassing staging
        // (typically used by main loop which doesn't need staging)
        telemetryData.computeIfAbsent(namespace, k -> {
            namespaceOrder.putIfAbsent(k, nextOrder++);
            return new ConcurrentHashMap<>();
        }).put(key, value);
    }
    
    /**
     * Remove a specific data entry from current namespace.
     * @param key The key to remove
     */
    public void removeData(String key) {
        String namespace = currentNamespace.get();
        ConcurrentHashMap<String, Object> namespaceData = telemetryData.get(namespace);
        if (namespaceData != null) {
            namespaceData.remove(key);
        }
    }
    
    /**
     * Clear all data for current namespace.
     */
    public void clearNamespace() {
        String namespace = currentNamespace.get();
        telemetryData.remove(namespace);
        namespaceOrder.remove(namespace);
    }
    
    /**
     * Clear all data for a specific namespace.
     * @param namespace The namespace to clear
     */
    public void clearNamespace(String namespace) {
        telemetryData.remove(namespace);
        namespaceOrder.remove(namespace);
    }
    
    /**
     * Clear all telemetry data from all namespaces.
     */
    public void clearAll() {
        telemetryData.clear();
        namespaceOrder.clear();
        nextOrder = 0;
    }
    
    /**
     * Update all telemetry outputs (FTC Driver Station and PanelsTelemetry dashboard).
     * Call this once per loop in your main OpMode.
     */
    public void update() {
        // Update FTC telemetry
        if (ftcTelemetry != null) {
            updateFtcTelemetry();
        }
        
        // Update PanelsTelemetry if available
        if (usePanelsTelemetry && panelsTelemetry != null) {
            updatePanelsTelemetry();
        }
    }
    
    /**
     * Update FTC telemetry with all stored data.
     */
    private void updateFtcTelemetry() {
        // Sort namespaces by order and add data
        telemetryData.keySet().stream()
            .sorted((a, b) -> Long.compare(
                namespaceOrder.getOrDefault(a, Long.MAX_VALUE),
                namespaceOrder.getOrDefault(b, Long.MAX_VALUE)
            ))
            .forEach(namespace -> {
                ConcurrentHashMap<String, Object> data = telemetryData.get(namespace);
                if (data != null && !data.isEmpty()) {
                    // Add namespace header
                    ftcTelemetry.addData("=== " + namespace.toUpperCase() + " ===", "");
                    
                    // Add all data entries for this namespace
                    data.forEach((key, value) -> {
                        ftcTelemetry.addData(key, value);
                    });
                    
                    // Add blank line between namespaces
                    ftcTelemetry.addData("", "");
                }
            });
        
        ftcTelemetry.update();
    }
    
    /**
     * Update PanelsTelemetry dashboard with all stored data.
     */
    private void updatePanelsTelemetry() {
        // Add all namespaces to the dashboard
        telemetryData.keySet().stream()
            .sorted((a, b) -> Long.compare(
                namespaceOrder.getOrDefault(a, Long.MAX_VALUE),
                namespaceOrder.getOrDefault(b, Long.MAX_VALUE)
            ))
            .forEach(namespace -> {
                ConcurrentHashMap<String, Object> data = telemetryData.get(namespace);
                if (data != null && !data.isEmpty()) {
                    // Add namespace header
                    panelsTelemetry.getTelemetry().addData("=== " + namespace.toUpperCase() + " ===", "");
                    
                    data.forEach((key, value) -> {
                        String fullKey = namespace + "/" + key;
                        panelsTelemetry.getTelemetry().addData(fullKey, value);
                    });
                }
            });
        
        // Update PanelsTelemetry and mirror to FTC telemetry
        panelsTelemetry.getTelemetry().update();
    }
    
    /**
     * Get all data for a specific namespace.
     * @param namespace The namespace
     * @return Map of key-value pairs, or null if namespace doesn't exist
     */
    public Map<String, Object> getNamespaceData(String namespace) {
        ConcurrentHashMap<String, Object> data = telemetryData.get(namespace);
        return data != null ? new LinkedHashMap<>(data) : null;
    }
    
    /**
     * Get a formatted string representation of all telemetry data.
     * @return Formatted string with all telemetry data
     */
    public String getFormattedString() {
        StringBuilder sb = new StringBuilder();
        
        telemetryData.keySet().stream()
            .sorted((a, b) -> Long.compare(
                namespaceOrder.getOrDefault(a, Long.MAX_VALUE),
                namespaceOrder.getOrDefault(b, Long.MAX_VALUE)
            ))
            .forEach(namespace -> {
                ConcurrentHashMap<String, Object> data = telemetryData.get(namespace);
                if (data != null && !data.isEmpty()) {
                    sb.append("=== ").append(namespace.toUpperCase()).append(" ===\n");
                    data.forEach((key, value) -> {
                        sb.append(key).append(": ").append(value).append("\n");
                    });
                    sb.append("\n");
                }
            });
        
        return sb.toString();
    }
    
    /**
     * Get all namespaces.
     * @return Array of namespace names
     */
    public String[] getNamespaces() {
        return telemetryData.keySet().toArray(new String[0]);
    }
    
    /**
     * Check if a namespace exists and has data.
     * @param namespace The namespace to check
     * @return true if namespace exists and has data
     */
    public boolean hasNamespace(String namespace) {
        ConcurrentHashMap<String, Object> data = telemetryData.get(namespace);
        return data != null && !data.isEmpty();
    }
    
    /**
     * Get the underlying FTC Telemetry object.
     * Use this if you need to access FTC-specific methods.
     * @return The FTC telemetry object
     */
    public Telemetry getFtcTelemetry() {
        return ftcTelemetry;
    }
    
    /**
     * Get the PanelsTelemetry instance.
     * @return The PanelsTelemetry instance, or null if not available
     */
    public PanelsTelemetry getPanelsTelemetry() {
        return panelsTelemetry;
    }
    
    /**
     * Check if PanelsTelemetry is enabled.
     * @return true if PanelsTelemetry is available and enabled
     */
    public boolean isPanelsTelemetryEnabled() {
        return usePanelsTelemetry;
    }
    
    /**
     * Enable or disable PanelsTelemetry integration.
     * @param enabled true to enable, false to disable
     */
    public void setPanelsTelemetryEnabled(boolean enabled) {
        this.usePanelsTelemetry = enabled && panelsTelemetry != null;
    }
    
    /**
     * Begin staging mode for atomic updates.
     * Creates a new staging buffer for the current thread.
     * All addData() calls will write to the staging buffer until commitStaging() is called.
     * This prevents flickering by ensuring the main loop never sees empty/partial data.
     */
    public void beginStaging() {
        stagingMode.set(true);
        stagingBuffer.set(new ConcurrentHashMap<>());
    }
    
    /**
     * Commit the staging buffer atomically.
     * Replaces the current namespace's data with the staging buffer in a single atomic operation.
     * This ensures the main loop always sees complete data (never empty).
     */
    public void commitStaging() {
        if (!stagingMode.get() || stagingBuffer.get() == null) {
            return; // Not in staging mode, nothing to commit
        }
        
        String namespace = currentNamespace.get();
        ConcurrentHashMap<String, Object> staged = stagingBuffer.get();
        
        // Atomic swap - namespace is never empty during this operation
        telemetryData.put(namespace, staged);
        
        // Ensure namespace order is tracked
        namespaceOrder.putIfAbsent(namespace, nextOrder++);
        
        // Clear staging state
        stagingMode.set(false);
        stagingBuffer.set(null);
    }
    
    /**
     * Discard the staging buffer without committing.
     * Used when an exception occurs during runLoop() to prevent corrupted telemetry.
     */
    public void discardStaging() {
        stagingMode.set(false);
        stagingBuffer.set(null);
    }
}

