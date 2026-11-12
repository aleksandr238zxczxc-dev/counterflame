package com.example.counterflame

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RemotePlayer(val id: String, val x: Float, val y: Float, val a: Float, val hp: Int, val score: Int, val skin: String)
data class RemoteEnemy(val id: String, val x: Float, val y: Float, val hp: Float)

class NetworkClient(private val url: String, private val listener: Listener) {

    interface Listener {
        fun onConnected(myId: String)
        fun onState(players: List<RemotePlayer>, enemies: List<RemoteEnemy>)
        fun onDisconnected()
        fun onTradeIncoming(offerId: String, fromId: String, give: String, want: String) {}
        fun onTradeResult(offerId: String, ok: Boolean) {}
    }

    private val http = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
    private var ws: WebSocket? = null
    var myId: String = ""
        private set

    fun connect() {
        val req = Request.Builder().url(url).build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onConnected(myId)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try { parse(text) } catch (_: Exception) {}
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onDisconnected()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onDisconnected()
            }
        })
    }

    private fun parse(text: String) {
        val o = JSONObject(text)
        when (o.optString("t")) {
            "welcome" -> { myId = o.optString("id"); listener.onConnected(myId) }
            "state" -> {
                val players = ArrayList<RemotePlayer>()
                val pa = o.optJSONArray("players")
                if (pa != null) for (i in 0 until pa.length()) {
                    val p = pa.getJSONObject(i)
                    players.add(RemotePlayer(
                        p.getString("id"),
                        p.getDouble("x").toFloat(), p.getDouble("y").toFloat(),
                        p.getDouble("a").toFloat(), p.getInt("hp"),
                        p.getInt("score"), p.optString("skin", "default")
                    ))
                }
                val enemies = ArrayList<RemoteEnemy>()
                val ea = o.optJSONArray("enemies")
                if (ea != null) for (i in 0 until ea.length()) {
                    val e = ea.getJSONObject(i)
                    enemies.add(RemoteEnemy(
                        e.getString("id"),
                        e.getDouble("x").toFloat(), e.getDouble("y").toFloat(),
                        e.getDouble("hp").toFloat()
                    ))
                }
                listener.onState(players, enemies)
            }
            "trade_incoming" -> listener.onTradeIncoming(
                o.getString("offerId"), o.getString("from"),
                o.optString("give", ""), o.optString("want", "")
            )
            "trade_result" -> listener.onTradeResult(
                o.getString("offerId"), o.optBoolean("ok")
            )
        }
    }

    fun sendMove(x: Float, y: Float, a: Float) {
        ws?.send(JSONObject().apply {
            put("t", "move"); put("x", x); put("y", y); put("a", a)
        }.toString())
    }

    fun sendShoot() { ws?.send("""{"t":"shoot"}""") }

    fun sendSkin(skinId: String) {
        ws?.send(JSONObject().apply { put("t", "skin"); put("skin", skinId) }.toString())
    }

    fun sendTradeOffer(targetId: String, give: String, want: String) {
        ws?.send(JSONObject().apply {
            put("t", "trade_offer"); put("targetId", targetId)
            put("give", give); put("want", want)
        }.toString())
    }

    fun sendTradeAccept(offerId: String) {
        ws?.send(JSONObject().apply {
            put("t", "trade_accept"); put("offerId", offerId)
        }.toString())
    }

    fun sendTradeDecline(offerId: String) {
        ws?.send(JSONObject().apply {
            put("t", "trade_decline"); put("offerId", offerId)
        }.toString())
    }

    fun close() {
        ws?.close(1000, "bye")
        http.dispatcher.executorService.shutdown()
    }
}
