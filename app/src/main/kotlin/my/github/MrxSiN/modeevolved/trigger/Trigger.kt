package my.github.MrxSiN.modeevolved.trigger

/**
 * One condition about the device that can turn a Mode on.
 *
 * A new kind of trigger is a new implementation of this interface. It is added
 * beside the others, never by editing them.
 */
interface Trigger {

    /** The input this trigger reads, so only the sources rules need are started. */
    val signal: Signal

    fun isMetBy(state: DeviceState): Boolean

    /**
     * The same answer, told what this trigger answered last time.
     *
     * A trigger reading a noisy input overrides this to resist flapping. The
     * rest read a clean yes or no and ignore [wasMet].
     */
    fun isMetBy(state: DeviceState, wasMet: Boolean): Boolean = isMetBy(state)
}

/** Met while connected to the Wi-Fi network named [ssid]. */
data class WifiTrigger(val ssid: String) : Trigger {
    override val signal: Signal get() = Signal.WIFI
    override fun isMetBy(state: DeviceState): Boolean = ssid in state.wifiNetworks
}

/**
 * Met while the Bluetooth device at [address] is connected.
 *
 * @property name what the device was called when chosen, kept for display only.
 */
data class BluetoothTrigger(val address: String, val name: String) : Trigger {
    // Outside the constructor, so equality and storage stay the address as given.
    private val key = address.uppercase()
    override val signal: Signal get() = Signal.BLUETOOTH
    override fun isMetBy(state: DeviceState): Boolean = key in state.bluetoothDevices
}

/**
 * Met while the last position fix is within [radiusMeters] of [center].
 *
 * Leaving takes more than arriving did: once inside, the fix must clear a
 * boundary wider by [EXIT_MARGIN_SHARE] of the radius, or by the fix's own
 * accuracy when that is larger. A 100 m-accurate fix against a 50 m area
 * therefore settles on one answer instead of flapping along the edge, and a
 * Mode is not switched twice a minute while the phone sits on a desk indoors.
 */
data class AreaTrigger(val center: GeoPoint, val radiusMeters: Double) : Trigger {

    override val signal: Signal get() = Signal.LOCATION

    override fun isMetBy(state: DeviceState): Boolean = isMetBy(state, wasMet = false)

    override fun isMetBy(state: DeviceState, wasMet: Boolean): Boolean {
        val sample = state.location ?: return false
        return sample.point.distanceTo(center) <= boundaryFor(sample, wasMet)
    }

    private fun boundaryFor(sample: LocationSample, wasMet: Boolean): Double =
        if (wasMet) radiusMeters + maxOf(radiusMeters * EXIT_MARGIN_SHARE, sample.accuracyMeters ?: 0.0) else radiusMeters

    private companion object {
        /** The least a fix must travel past the edge to count as having left. */
        const val EXIT_MARGIN_SHARE = 0.25
    }
}

/** Met while the device is [movement]. Flying reads its own signal, since the motion sensor cannot tell it apart. */
data class MovementTrigger(val movement: Movement) : Trigger {
    override val signal: Signal get() = if (movement == Movement.FLYING) Signal.FLIGHT else Signal.MOVEMENT
    override fun isMetBy(state: DeviceState): Boolean =
        if (movement == Movement.FLYING) state.flying == true else state.movement == movement
}

/**
 * Met while the device is connected to external power, or while it is not when [connected] is
 * false. Any source counts (cable, wireless, dock): Android reports which, but not reliably
 * whether a phone sits in a dock, so no dock-only trigger is offered.
 */
data class PowerTrigger(val connected: Boolean) : Trigger {
    override val signal: Signal get() = Signal.POWER
    override fun isMetBy(state: DeviceState): Boolean = state.powerConnected == connected
}

/** Met while airplane mode is [on], or while it is off when [on] is false. */
data class AirplaneTrigger(val on: Boolean) : Trigger {
    override val signal: Signal get() = Signal.AIRPLANE
    override fun isMetBy(state: DeviceState): Boolean = state.airplaneMode == on
}
