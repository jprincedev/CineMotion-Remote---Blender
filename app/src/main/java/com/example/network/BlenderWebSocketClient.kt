package com.example.network

import android.util.Log
import com.example.model.CameraPose
import com.example.model.TakeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class BlenderWebSocketClient(
    private val scope: CoroutineScope
) {
    private val tag = "BlenderWS"

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _statusMessage = MutableStateFlow("Disconnected")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _targetName = MutableStateFlow("Character_01")
    val targetName: StateFlow<String> = _targetName.asStateFlow()

    private val _latencyMs = MutableStateFlow(0L)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    private var currentHost = "192.168.1.100"
    private var currentPort = 8765

    fun connect(ip: String, port: Int) {
        disconnect()
        currentHost = ip.trim()
        currentPort = port

        val url = "ws://$currentHost:$currentPort"
        _status.value = ConnectionStatus.CONNECTING
        _statusMessage.value = "Connecting to $url..."

        val request = try {
            Request.Builder().url(url).build()
        } catch (e: Exception) {
            _status.value = ConnectionStatus.ERROR
            _statusMessage.value = "Invalid URL: ${e.message}"
            return
        }

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(tag, "WebSocket connected to $url")
                _status.value = ConnectionStatus.CONNECTED
                _statusMessage.value = "Connected to $currentHost:$currentPort"

                // Send Hello handshake
                val helloJson = JSONObject().apply {
                    put("type", "hello")
                    put("app", "CineMotion Remote")
                    put("version", 1)
                }
                ws.send(helloJson.toString())

                startPingLoop()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closing: $code / $reason")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closed: $code / $reason")
                _status.value = ConnectionStatus.DISCONNECTED
                _statusMessage.value = "Disconnected"
                stopPingLoop()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket error: ${t.message}", t)
                _status.value = ConnectionStatus.ERROR
                _statusMessage.value = t.localizedMessage ?: "Connection failed"
                stopPingLoop()
            }
        })
    }

    fun disconnect() {
        stopPingLoop()
        try {
            webSocket?.close(1000, "User requested disconnect")
        } catch (_: Exception) {}
        webSocket = null
        _status.value = ConnectionStatus.DISCONNECTED
        _statusMessage.value = "Disconnected"
    }

    fun reconnect() {
        connect(currentHost, currentPort)
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "target" -> {
                    val name = json.optString("name", "Target")
                    _targetName.value = name
                }
                "pong" -> {
                    val pingT = json.optLong("t", 0L)
                    if (pingT > 0) {
                        val rtt = (System.currentTimeMillis() - pingT).coerceAtLeast(0L)
                        _latencyMs.value = rtt
                    }
                }
                "status" -> {
                    val msg = json.optString("message", "")
                    if (msg.isNotEmpty()) {
                        _statusMessage.value = msg
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error parsing incoming JSON: ${e.message}")
        }
    }

    private fun startPingLoop() {
        stopPingLoop()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive && _status.value == ConnectionStatus.CONNECTED) {
                sendPing()
                delay(1200)
            }
        }
    }

    private fun stopPingLoop() {
        pingJob?.cancel()
        pingJob = null
    }

    private fun sendPing() {
        val now = System.currentTimeMillis()
        val pingJson = JSONObject().apply {
            put("type", "ping")
            put("t", now)
        }
        sendText(pingJson.toString())
    }

    fun sendPose(
        pose: CameraPose,
        lensMm: Int,
        targetLock: Boolean,
        horizonLock: Boolean,
        targetName: String = ""
    ) {
        if (_status.value != ConnectionStatus.CONNECTED) return

        val poseJson = JSONObject().apply {
            put("type", "pose")
            put("t", pose.timestamp)
            put("pos", JSONArray().apply {
                put(pose.x.toDouble())
                put(pose.y.toDouble())
                put(pose.z.toDouble())
            })
            put("quat", JSONArray().apply {
                put(pose.qx.toDouble())
                put(pose.qy.toDouble())
                put(pose.qz.toDouble())
                put(pose.qw.toDouble())
            })
            put("lens", lensMm)
            put("targetLock", targetLock)
            put("horizonLock", horizonLock)
            put("target", targetName.ifEmpty { _targetName.value })
            put("targetDist", pose.radius.toDouble())
            put("anchor", JSONArray().apply {
                put(pose.anchorX.toDouble())
                put(pose.anchorY.toDouble())
                put(pose.anchorZ.toDouble())
            })
        }
        sendText(poseJson.toString())
    }

    fun sendRecordStart(takeName: String) {
        val json = JSONObject().apply {
            put("type", "record_start")
            put("name", takeName)
            put("t", System.currentTimeMillis())
        }
        sendText(json.toString())
    }

    fun sendRecordStop(takeName: String, durationMs: Long) {
        val json = JSONObject().apply {
            put("type", "record_stop")
            put("name", takeName)
            put("duration", durationMs)
            put("t", System.currentTimeMillis())
        }
        sendText(json.toString())
    }

    fun sendBakeTake(take: TakeEntity): Boolean {
        if (_status.value != ConnectionStatus.CONNECTED) return false
        val samples = take.parseSamples()
        val bakeJson = JSONObject().apply {
            put("type", "bake")
            put("take", JSONObject().apply {
                put("id", take.id)
                put("name", take.name)
                put("target", take.targetName)
                put("lens", take.lensMm)
                put("duration", take.durationMs)
                put("timestamp", take.timestamp)
                put("sampleCount", samples.size)
                val sampleArray = JSONArray()
                samples.forEach { s ->
                    val sObj = JSONObject().apply {
                        put("t", s.t)
                        put("pos", JSONArray(s.pos))
                        put("quat", JSONArray(s.quat))
                    }
                    sampleArray.put(sObj)
                }
                put("samples", sampleArray)
            })
        }
        return sendText(bakeJson.toString())
    }

    private fun sendText(text: String): Boolean {
        return webSocket?.send(text) ?: false
    }
}
