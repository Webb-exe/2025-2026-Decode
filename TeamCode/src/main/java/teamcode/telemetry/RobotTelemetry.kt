package teamcode.telemetry

import com.bylazar.telemetry.PanelsTelemetry
import org.firstinspires.ftc.robotcore.external.Telemetry
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
class RobotTelemetry(
    /**
     * Get the underlying FTC Telemetry object.
     * Use this if you need to access FTC-specific methods.
     * @return The FTC telemetry object
     */
    // The original FTC telemetry object
    val ftcTelemetry: Telemetry?
) {
    // Store telemetry data per namespace (thread)
    private val telemetryData: ConcurrentHashMap<String, ConcurrentHashMap<String, Any>> = ConcurrentHashMap()
    private val namespaceOrder: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
    private var nextOrder = 0L

    /**
     * Get the PanelsTelemetry instance.
     * @return The PanelsTelemetry instance, or null if not available
     */
    // PanelsTelemetry integration (optional)
    val panelsTelemetry: PanelsTelemetry = PanelsTelemetry
    private var usePanelsTelemetry = false

    // Current namespace for thread-local operations
    private val currentNamespace: ThreadLocal<String> =
        ThreadLocal.withInitial { "Main" }

    // Staging buffer for atomic updates - prevents flickering
    private val stagingBuffer: ThreadLocal<ConcurrentHashMap<String, Any>?> =
        ThreadLocal.withInitial { null }
    private val stagingMode: ThreadLocal<Boolean> =
        ThreadLocal.withInitial { false }

    var namespace: String?
        /**
         * Get the current namespace for this thread.
         */
        get() = currentNamespace.get()
        /**
         * Set the namespace for the current thread.
         * This is automatically called by RobotThread, but you can call it manually.
         * @param namespace The namespace (usually thread name)
         */
        set(namespace) {
            currentNamespace.set(namespace)
        }

    /**
     * Add telemetry data. Works like standard telemetry.addData().
     * Data is stored under the current thread's namespace.
     * @param key The telemetry key
     * @param value The telemetry value
     */
    fun addData(key: String, value: Any) {
        // Write to staging buffer if in staging mode
        if (stagingMode.get()!!) {
            stagingBuffer.get()?.put(key, value)
            return
        }

        // Normal mode - write directly to telemetry data
        val namespace = currentNamespace.get()
        if (namespace == null) return
        telemetryData.computeIfAbsent(namespace) { k ->
            namespaceOrder.putIfAbsent(k, nextOrder++)
            ConcurrentHashMap()
        }.put(key, value)
    }

    /**
     * Add formatted telemetry data.
     * @param key The telemetry key
     * @param format Format string
     * @param args Format arguments
     */
    fun addData(key: String, format: String, vararg args: Any?) {
        val value = String.format(format, *args)
        addData(key, value)
    }

    /**
     * Add data to a specific namespace (useful for main loop to add data).
     * Note: This method bypasses staging mode and writes directly.
     * @param namespace The namespace
     * @param key The telemetry key
     * @param value The telemetry value
     */
    fun addData(namespace: String, key: String, value: Any) {
        // This method always writes directly, bypassing staging
        // (typically used by main loop which doesn't need staging)
        telemetryData.computeIfAbsent(namespace) { k ->
            namespaceOrder.putIfAbsent(k, nextOrder++)
            ConcurrentHashMap()
        }.put(key, value)
    }

    /**
     * Remove a specific data entry from current namespace.
     * @param key The key to remove
     */
    fun removeData(key: String) {
        val namespace = currentNamespace.get()
        if (namespace == null) return
        telemetryData[namespace]?.remove(key)
    }

    /**
     * Clear all data for current namespace.
     */
    fun clearNamespace() {
        val namespace = currentNamespace.get()
        if (namespace == null) return
        telemetryData.remove(namespace)
        namespaceOrder.remove(namespace)
    }

    /**
     * Clear all data for a specific namespace.
     * @param namespace The namespace to clear
     */
    fun clearNamespace(namespace: String) {
        telemetryData.remove(namespace)
        namespaceOrder.remove(namespace)
    }

    /**
     * Clear all telemetry data from all namespaces.
     */
    fun clearAll() {
        telemetryData.clear()
        namespaceOrder.clear()
        nextOrder = 0
    }

    /**
     * Update all telemetry outputs (FTC Driver Station and PanelsTelemetry dashboard).
     * Call this once per loop in your main OpMode.
     */
    fun update() {
        // Update FTC telemetry
        if (ftcTelemetry != null) {
            updateFtcTelemetry()
        }


        // Update PanelsTelemetry if available
        if (usePanelsTelemetry) {
            updatePanelsTelemetry()
        }
    }

    /**
     * Update FTC telemetry with all stored data.
     */
    private fun updateFtcTelemetry() {
        val telemetry = ftcTelemetry ?: return
        
        // Sort namespaces by order and add data
        telemetryData.keys.stream()
            .sorted { a: String, b: String ->
                namespaceOrder.getOrDefault(a, Long.MAX_VALUE)
                    .compareTo(namespaceOrder.getOrDefault(b, Long.MAX_VALUE))
            }
            .forEach { namespace: String ->
                val data = telemetryData[namespace]
                if (data != null && data.isNotEmpty()) {
                    // Add namespace header
                    telemetry.addData(
                        "=== ${namespace.uppercase(Locale.getDefault())} ===",
                        ""
                    )

                    // Add all data entries for this namespace
                    data.forEach { (key, value) ->
                        telemetry.addData(key, value)
                    }

                    // Add blank line between namespaces
                    telemetry.addData("", "")
                }
            }

        telemetry.update()
    }

    /**
     * Update PanelsTelemetry dashboard with all stored data.
     */
    private fun updatePanelsTelemetry() {
        // Add all namespaces to the dashboard
        telemetryData.keys.stream()
            .sorted { a: String, b: String ->
                namespaceOrder.getOrDefault(a, Long.MAX_VALUE)
                    .compareTo(namespaceOrder.getOrDefault(b, Long.MAX_VALUE))
            }
            .forEach { namespace: String ->
                val data = telemetryData[namespace]
                if (data != null && data.isNotEmpty()) {
                    // Add namespace header
                    panelsTelemetry.telemetry.addData(
                        "=== ${namespace.uppercase(Locale.getDefault())} ===",
                        ""
                    )

                    data.forEach { (key, value) ->
                        val fullKey = "$namespace/$key"
                        panelsTelemetry.telemetry.addData(fullKey, value)
                    }
                }
            }

        // Update PanelsTelemetry and mirror to FTC telemetry
        panelsTelemetry.telemetry.update()
    }

    /**
     * Get all data for a specific namespace.
     * @param namespace The namespace
     * @return Map of key-value pairs, or null if namespace doesn't exist
     */
    fun getNamespaceData(namespace: String): MutableMap<String, Any>? {
        val data = telemetryData[namespace]
        return data?.let { LinkedHashMap(it) }
    }

    val formattedString: String
        /**
         * Get a formatted string representation of all telemetry data.
         * @return Formatted string with all telemetry data
         */
        get() {
            val sb = StringBuilder()

            telemetryData.keys.stream()
                .sorted { a: String, b: String ->
                    namespaceOrder.getOrDefault(a, Long.MAX_VALUE)
                        .compareTo(namespaceOrder.getOrDefault(b, Long.MAX_VALUE))
                }
                .forEach { namespace: String ->
                    val data = telemetryData[namespace]
                    if (data != null && data.isNotEmpty()) {
                        sb.append("=== ")
                            .append(namespace.uppercase(Locale.getDefault()))
                            .append(" ===\n")
                        data.forEach { (key, value) ->
                            sb.append(key).append(": ").append(value).append("\n")
                        }
                        sb.append("\n")
                    }
                }

            return sb.toString()
        }

    val namespaces: Array<String>
        /**
         * Get all namespaces.
         * @return Array of namespace names
         */
        get() = telemetryData.keys.toTypedArray()

    /**
     * Check if a namespace exists and has data.
     * @param namespace The namespace to check
     * @return true if namespace exists and has data
     */
    fun hasNamespace(namespace: String): Boolean {
        val data = telemetryData[namespace]
        return data != null && data.isNotEmpty()
    }

    var isPanelsTelemetryEnabled: Boolean
        /**
         * Check if PanelsTelemetry is enabled.
         * @return true if PanelsTelemetry is available and enabled
         */
        get() = usePanelsTelemetry
        /**
         * Enable or disable PanelsTelemetry integration.
         * @param enabled true to enable, false to disable
         */
        set(enabled) {
            this.usePanelsTelemetry = enabled
        }

    /**
     * Begin staging mode for atomic updates.
     * Creates a new staging buffer for the current thread.
     * All addData() calls will write to the staging buffer until commitStaging() is called.
     * This prevents flickering by ensuring the main loop never sees empty/partial data.
     */
    fun beginStaging() {
        stagingMode.set(true)
        stagingBuffer.set(ConcurrentHashMap())
    }

    /**
     * Commit the staging buffer atomically.
     * Replaces the current namespace's data with the staging buffer in a single atomic operation.
     * This ensures the main loop always sees complete data (never empty).
     */
    fun commitStaging() {
        if (!stagingMode.get()!! || stagingBuffer.get() == null) {
            return  // Not in staging mode, nothing to commit
        }

        val namespace = currentNamespace.get() ?: return
        val staged = stagingBuffer.get() ?: return

        // Atomic swap - namespace is never empty during this operation
        telemetryData[namespace] = staged

        // Ensure namespace order is tracked
        namespaceOrder.putIfAbsent(namespace, nextOrder++)

        // Clear staging state
        stagingMode.set(false)
        stagingBuffer.set(null)
    }

    /**
     * Discard the staging buffer without committing.
     * Used when an exception occurs during runLoop() to prevent corrupted telemetry.
     */
    fun discardStaging() {
        stagingMode.set(false)
        stagingBuffer.set(null)
    }
}

