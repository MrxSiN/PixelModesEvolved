package my.github.MrxSiN.modeevolved.action

import org.json.JSONArray
import org.json.JSONObject

import my.github.MrxSiN.modeevolved.store.JsonKind
import my.github.MrxSiN.modeevolved.store.KindCodec
import my.github.MrxSiN.modeevolved.store.ModeMapCodec

/** How one kind of [ModeAction] is written to and read from storage. */
typealias ActionKind<T> = JsonKind<ModeAction, T>

private const val TIMING = "timing"

private fun JSONObject.timing() = Timing.valueOf(getString(TIMING))

object LaunchAppKind : ActionKind<LaunchAppAction>("launchApp", LaunchAppAction::class.java) {
    override fun write(item: LaunchAppAction): JSONObject =
        JSONObject().put(TIMING, item.timing.name).put(PACKAGE, item.packageName).put(LABEL, item.label)

    override fun decode(json: JSONObject) = LaunchAppAction(json.timing(), json.getString(PACKAGE), json.optString(LABEL))

    private const val PACKAGE = "package"
    private const val LABEL = "label"
}

object BroadcastKind : ActionKind<BroadcastAction>("broadcast", BroadcastAction::class.java) {
    override fun write(item: BroadcastAction): JSONObject =
        JSONObject().put(TIMING, item.timing.name).put(ACTION, item.intentAction).putOpt(PACKAGE, item.packageName)

    override fun decode(json: JSONObject) =
        BroadcastAction(json.timing(), json.getString(ACTION), json.optString(PACKAGE).ifEmpty { null })

    private const val ACTION = "action"
    private const val PACKAGE = "package"
}

object TaskerTaskKind : ActionKind<TaskerTaskAction>("taskerTask", TaskerTaskAction::class.java) {
    override fun write(item: TaskerTaskAction): JSONObject =
        JSONObject().put(TIMING, item.timing.name).put(TASK, item.taskName)

    override fun decode(json: JSONObject) = TaskerTaskAction(json.timing(), json.getString(TASK))

    private const val TASK = "task"
}

/** Converts every stored [ModeActions] to and from JSON. Actions of unknown kinds are dropped. */
class ActionCodec(val actions: KindCodec<ModeAction>) : ModeMapCodec<ModeActions>() {

    override fun encodeOne(list: ModeActions): JSONObject {
        val stored = JSONArray()
        list.items.forEach { stored.put(actions.encode(it)) }
        return JSONObject().put(ACTIONS, stored)
    }

    override fun decodeOne(modeId: String, json: JSONObject): ModeActions {
        val stored = json.getJSONArray(ACTIONS)
        return ModeActions(modeId, (0 until stored.length()).mapNotNull { actions.decode(stored.getJSONObject(it)) })
    }

    companion object {
        private const val ACTIONS = "actions"

        fun standard(): ActionCodec = ActionCodec(KindCodec(listOf(LaunchAppKind, BroadcastKind, TaskerTaskKind)))
    }
}
