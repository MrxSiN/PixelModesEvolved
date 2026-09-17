package my.github.MrxSiN.modeevolved.presentation

import android.content.res.Resources

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.rule.AirplaneKind
import my.github.MrxSiN.modeevolved.rule.AreaKind
import my.github.MrxSiN.modeevolved.rule.BluetoothKind
import my.github.MrxSiN.modeevolved.rule.MovementKind
import my.github.MrxSiN.modeevolved.rule.WifiKind
import my.github.MrxSiN.modeevolved.trigger.AirplaneTrigger
import my.github.MrxSiN.modeevolved.trigger.AreaTrigger
import my.github.MrxSiN.modeevolved.trigger.BluetoothTrigger
import my.github.MrxSiN.modeevolved.trigger.Movement
import my.github.MrxSiN.modeevolved.trigger.MovementTrigger
import my.github.MrxSiN.modeevolved.trigger.Trigger
import my.github.MrxSiN.modeevolved.trigger.WifiTrigger

/** How one kind of trigger is named, drawn and described to a person. */
typealias TriggerPresentation<T> = Presentation<Trigger, T>

object WifiPresentation : TriggerPresentation<WifiTrigger>(WifiKind) {
    override val label = R.string.trigger_wifi
    override val hint = R.string.trigger_wifi_hint
    override val icon = R.drawable.ic_trigger_wifi
    override fun summary(resources: Resources, item: WifiTrigger): String =
        resources.getString(R.string.trigger_wifi_summary, item.ssid)
}

object BluetoothPresentation : TriggerPresentation<BluetoothTrigger>(BluetoothKind) {
    override val label = R.string.trigger_bluetooth
    override val hint = R.string.trigger_bluetooth_hint
    override val icon = R.drawable.ic_trigger_bluetooth
    override fun summary(resources: Resources, item: BluetoothTrigger): String =
        resources.getString(R.string.trigger_bluetooth_summary, item.name.ifEmpty { item.address })
}

object AreaPresentation : TriggerPresentation<AreaTrigger>(AreaKind) {
    override val label = R.string.trigger_area
    override val hint = R.string.trigger_area_hint
    override val icon = R.drawable.ic_trigger_area
    override fun summary(resources: Resources, item: AreaTrigger): String =
        resources.getString(R.string.trigger_area_summary, Distances.format(resources, item.radiusMeters))
}

object MovementPresentation : TriggerPresentation<MovementTrigger>(MovementKind) {
    override val label = R.string.trigger_movement
    override val hint = R.string.trigger_movement_hint
    override val icon = R.drawable.ic_trigger_movement
    override fun summary(resources: Resources, item: MovementTrigger): String =
        resources.getString(titleOf(item.movement))

    fun titleOf(movement: Movement): Int = when (movement) {
        Movement.MOVING -> R.string.movement_moving
        Movement.STILL -> R.string.movement_still
        Movement.FLYING -> R.string.movement_flying
    }

    fun detailOf(movement: Movement): Int = when (movement) {
        Movement.MOVING -> R.string.movement_moving_detail
        Movement.STILL -> R.string.movement_still_detail
        Movement.FLYING -> R.string.movement_flying_detail
    }

    fun iconOf(movement: Movement): Int = when (movement) {
        Movement.MOVING -> R.drawable.ic_trigger_movement
        Movement.STILL -> R.drawable.ic_trigger_still
        Movement.FLYING -> R.drawable.ic_trigger_flight
    }
}

object AirplanePresentation : TriggerPresentation<AirplaneTrigger>(AirplaneKind) {
    override val label = R.string.trigger_airplane
    override val hint = R.string.trigger_airplane_hint
    override val icon = R.drawable.ic_trigger_airplane
    override fun summary(resources: Resources, item: AirplaneTrigger): String =
        resources.getString(titleOf(item.on))

    fun titleOf(on: Boolean): Int = if (on) R.string.airplane_on else R.string.airplane_off

    fun detailOf(on: Boolean): Int = if (on) R.string.airplane_on_detail else R.string.airplane_off_detail

    fun iconOf(on: Boolean): Int = if (on) R.drawable.ic_trigger_airplane else R.drawable.ic_trigger_airplane_off
}

/** Every trigger presentation, in the order a person chooses between them. */
val TriggerPresentations = PresentationSet(
    listOf(
        WifiPresentation,
        BluetoothPresentation,
        AreaPresentation,
        MovementPresentation,
        AirplanePresentation,
    ),
)

/** Distances as a person reads them: meters below one kilometer, kilometers above. */
object Distances {
    fun format(resources: Resources, meters: Double): String = if (meters < 1000) {
        resources.getString(R.string.distance_meters, meters.toInt())
    } else {
        resources.getString(R.string.distance_kilometers, meters / 1000)
    }
}
