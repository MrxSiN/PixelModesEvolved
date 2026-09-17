package my.github.MrxSiN.modeevolved.signal

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings

import my.github.MrxSiN.modeevolved.engine.SignalSource
import my.github.MrxSiN.modeevolved.trigger.Signal
import my.github.MrxSiN.modeevolved.trigger.StateUpdate

/** Whether airplane mode is on, from the global setting that every airplane mode toggle writes. */
class AirplaneSource(context: Context, private val handler: Handler) : SignalSource {

    override val signal: Signal = Signal.AIRPLANE

    private val resolver = context.contentResolver
    private var publish: ((StateUpdate) -> Unit)? = null

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = emit()
    }

    override fun start(publish: (StateUpdate) -> Unit) {
        this.publish = publish
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON), false, observer)
        emit()
    }

    override fun stop() {
        resolver.unregisterContentObserver(observer)
        publish?.invoke { it.copy(airplaneMode = null) }
        publish = null
    }

    private fun emit() {
        val on = Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        publish?.invoke { it.copy(airplaneMode = on) }
    }
}
