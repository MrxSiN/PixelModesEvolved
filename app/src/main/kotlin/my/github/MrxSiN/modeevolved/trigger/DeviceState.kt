package my.github.MrxSiN.modeevolved.trigger

/**
 * What the device knows about its surroundings at one moment.
 *
 * A null or empty value means "not known", which is different from "no":
 * a signal nobody asked for is never started, so its slice stays unknown and
 * no trigger reading it can be met. A signal that is running but has not
 * decided yet stays unknown too, which is why the phone is not "left still"
 * the moment it boots.
 *
 * @property wifiNetworks SSIDs of the Wi-Fi networks connected now.
 * @property bluetoothDevices hardware addresses of connected Bluetooth devices, upper case.
 * @property location last position fix, with how precise and how fresh it is.
 * @property movement whether the device is moving.
 * @property flying whether the device is on board a flying aircraft.
 * @property airplaneMode whether airplane mode is on.
 * @property powerConnected whether the device is connected to external power (cable, wireless or dock).
 */
data class DeviceState(
    val wifiNetworks: Set<String> = emptySet(),
    val bluetoothDevices: Set<String> = emptySet(),
    val location: LocationSample? = null,
    val movement: Movement? = null,
    val flying: Boolean? = null,
    val airplaneMode: Boolean? = null,
    val powerConnected: Boolean? = null,
)

/** A change to one slice of [DeviceState], applied by the source that owns it. */
typealias StateUpdate = (DeviceState) -> DeviceState

/** The independent inputs a trigger can read. Each one is served by one signal source. */
enum class Signal { WIFI, BLUETOOTH, LOCATION, MOVEMENT, FLIGHT, AIRPLANE, POWER }

/** How the device moves. [FLYING] is judged from position fixes, the others from the motion sensor. */
enum class Movement { MOVING, STILL, FLYING }
