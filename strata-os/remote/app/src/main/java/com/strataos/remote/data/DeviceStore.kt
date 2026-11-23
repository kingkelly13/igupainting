package com.strataos.remote.data

import android.content.Context
import android.util.AtomicFile
import com.strataos.remote.ir.IrSignal
import com.strataos.remote.ir.Protocol
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Saves remotes as JSON in the app's private storage. */
class DeviceStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "remotes.json"))

    fun load(): List<Device> = runCatching {
        val root = JSONObject(String(file.readFully(), Charsets.UTF_8))
        val list = root.getJSONArray("devices")
        (0 until list.length()).mapNotNull { runCatching { device(list.getJSONObject(it)) }.getOrNull() }
    }.getOrDefault(emptyList())

    fun save(devices: List<Device>) {
        val root = JSONObject()
            .put("version", 1)
            .put("devices", JSONArray().apply { devices.forEach { put(json(it)) } })
        val out = file.startWrite()
        try {
            out.write(root.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    private fun json(d: Device) = JSONObject()
        .put("id", d.id)
        .put("name", d.name)
        .put("kind", d.kind.name)
        .put("source", d.source ?: JSONObject.NULL)
        .put("buttons", JSONArray().apply {
            d.buttons.forEach { b ->
                put(JSONObject().put("key", b.key).put("label", b.label).put("signal", json(b.signal)))
            }
        })

    private fun json(s: IrSignal): JSONObject = when (s) {
        is IrSignal.Parsed -> JSONObject()
            .put("type", "parsed")
            .put("protocol", s.protocol.flipperName)
            .put("address", s.address)
            .put("command", s.command)
        is IrSignal.Raw -> JSONObject()
            .put("type", "raw")
            .put("frequency", s.frequency)
            .put("duty", s.dutyCycle.toDouble())
            .put("once", JSONArray(s.once))
            .put("repeat", JSONArray(s.repeat))
    }

    private fun device(o: JSONObject): Device {
        val buttons = o.getJSONArray("buttons")
        return Device(
            id = o.getString("id"),
            name = o.getString("name"),
            kind = DeviceKind.of(o.optString("kind")),
            source = if (o.isNull("source")) null else o.optString("source"),
            buttons = (0 until buttons.length()).mapNotNull { i ->
                val b = buttons.getJSONObject(i)
                val signal = signal(b.getJSONObject("signal")) ?: return@mapNotNull null
                RemoteButton(b.getString("key"), b.getString("label"), signal)
            },
        )
    }

    private fun signal(o: JSONObject): IrSignal? = when (o.optString("type")) {
        "parsed" -> Protocol.fromName(o.getString("protocol"))?.let {
            IrSignal.Parsed(it, o.getLong("address"), o.getLong("command"))
        }
        "raw" -> IrSignal.Raw(
            frequency = o.getInt("frequency"),
            once = ints(o.optJSONArray("once")),
            repeat = ints(o.optJSONArray("repeat")),
            dutyCycle = o.optDouble("duty", 0.33).toFloat(),
        )
        else -> null
    }

    private fun ints(a: JSONArray?): List<Int> = if (a == null) emptyList() else (0 until a.length()).map { a.getInt(it) }
}
