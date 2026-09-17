package my.github.MrxSiN.modeevolved.rule

import org.json.JSONObject

import my.github.MrxSiN.modeevolved.store.JsonKind
import my.github.MrxSiN.modeevolved.trigger.AirplaneTrigger
import my.github.MrxSiN.modeevolved.trigger.AreaTrigger
import my.github.MrxSiN.modeevolved.trigger.BluetoothTrigger
import my.github.MrxSiN.modeevolved.trigger.GeoPoint
import my.github.MrxSiN.modeevolved.trigger.Movement
import my.github.MrxSiN.modeevolved.trigger.MovementTrigger
import my.github.MrxSiN.modeevolved.trigger.Trigger
import my.github.MrxSiN.modeevolved.trigger.WifiTrigger

/** How one kind of [Trigger] is written to and read from storage. */
typealias TriggerKind<T> = JsonKind<Trigger, T>

object WifiKind : TriggerKind<WifiTrigger>("wifi", WifiTrigger::class.java) {
    override fun write(item: WifiTrigger): JSONObject = JSONObject().put(SSID, item.ssid)
    override fun decode(json: JSONObject) = WifiTrigger(json.getString(SSID))
    private const val SSID = "ssid"
}

object BluetoothKind : TriggerKind<BluetoothTrigger>("bluetooth", BluetoothTrigger::class.java) {
    override fun write(item: BluetoothTrigger): JSONObject =
        JSONObject().put(ADDRESS, item.address).put(NAME, item.name)

    override fun decode(json: JSONObject) =
        BluetoothTrigger(json.getString(ADDRESS), json.optString(NAME))

    private const val ADDRESS = "address"
    private const val NAME = "name"
}

object AreaKind : TriggerKind<AreaTrigger>("area", AreaTrigger::class.java) {
    override fun write(item: AreaTrigger): JSONObject = JSONObject()
        .put(LATITUDE, item.center.latitude)
        .put(LONGITUDE, item.center.longitude)
        .put(RADIUS, item.radiusMeters)

    override fun decode(json: JSONObject) = AreaTrigger(
        GeoPoint(json.getDouble(LATITUDE), json.getDouble(LONGITUDE)),
        json.getDouble(RADIUS),
    )

    private const val LATITUDE = "latitude"
    private const val LONGITUDE = "longitude"
    private const val RADIUS = "radius"
}

object MovementKind : TriggerKind<MovementTrigger>("movement", MovementTrigger::class.java) {
    override fun write(item: MovementTrigger): JSONObject =
        JSONObject().put(STATE, item.movement.name)

    override fun decode(json: JSONObject) = MovementTrigger(Movement.valueOf(json.getString(STATE)))

    private const val STATE = "state"
}

object AirplaneKind : TriggerKind<AirplaneTrigger>("airplane", AirplaneTrigger::class.java) {
    override fun write(item: AirplaneTrigger): JSONObject = JSONObject().put(ON, item.on)
    override fun decode(json: JSONObject) = AirplaneTrigger(json.getBoolean(ON))
    private const val ON = "on"
}

