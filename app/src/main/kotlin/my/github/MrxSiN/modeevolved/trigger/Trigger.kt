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
    override val signal: Signal get() = Signal.BLUETOOTH
    override fun isMetBy(state: DeviceState): Boolean = address.uppercase() in state.bluetoothDevices
}

/** Met while the last position fix is within [radiusMeters] of [center]. */
data class AreaTrigger(val center: GeoPoint, val radiusMeters: Double) : Trigger {
    override val signal: Signal get() = Signal.LOCATION
    override fun isMetBy(state: DeviceState): Boolean =
        state.location?.let { it.distanceTo(center) <= radiusMeters } ?: false
}

/** Met while the device is [movement]. Flying reads its own signal, since the motion sensor cannot tell it apart. */
data class MovementTrigger(val movement: Movement) : Trigger {
    override val signal: Signal get() = if (movement == Movement.FLYING) Signal.FLIGHT else Signal.MOVEMENT
    override fun isMetBy(state: DeviceState): Boolean =
        if (movement == Movement.FLYING) state.flying == true else state.movement == movement
}

/** Met while airplane mode is [on], or while it is off when [on] is false. */
data class AirplaneTrigger(val on: Boolean) : Trigger {
    override val signal: Signal get() = Signal.AIRPLANE
    override fun isMetBy(state: DeviceState): Boolean = state.airplaneMode == on
}
