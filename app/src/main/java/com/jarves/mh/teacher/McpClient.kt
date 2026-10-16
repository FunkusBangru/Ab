package com.jarves.mh.teacher

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicInteger

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JSONObject,
    val serverId: String,
)

data class McpToolResult(
    val isError: Boolean,
    val content: String,
)

class McpClient(
    val serverId: String,
    inputStream: InputStream,
    outputStream: OutputStream,
) : AutoCloseable {

    private val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
    private val writer = BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))
    private val requestIdCounter = AtomicInteger(1)
    private var isInitialized = false

    @Synchronized
    fun sendRequest(method: String, params: JSONObject = JSONObject()): JSONObject? {
        val id = requestIdCounter.getAndIncrement()
        val request = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }

        writer.write(request.toString())
        writer.newLine()
        writer.flush()

        while (true) {
            val line = reader.readLine() ?: return null
            if (line.isBlank()) continue
            val response = runCatching { JSONObject(line) }.getOrNull() ?: continue
            if (response.optInt("id") == id) {
                return response
            }
        }
    }

    @Synchronized
    fun sendNotification(method: String, params: JSONObject = JSONObject()) {
        val notification = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }
        writer.write(notification.toString())
        writer.newLine()
        writer.flush()
    }

    fun initialize(): Boolean {
        val clientInfo = JSONObject().apply {
            put("name", "MobileHarness-Teacher")
            put("version", "1.1.0")
        }
        val params = JSONObject().apply {
            put("protocolVersion", "2024-11-05")
            put("capabilities", JSONObject())
            put("clientInfo", clientInfo)
        }

        val response = sendRequest("initialize", params) ?: return false
        if (response.has("error")) return false

        sendNotification("notifications/initialized")
        isInitialized = true
        return true
    }

    fun listTools(): List<McpTool> {
        if (!isInitialized) initialize()
        val response = sendRequest("tools/list") ?: return emptyList()
        val result = response.optJSONObject("result") ?: return emptyList()
        val toolsArray = result.optJSONArray("tools") ?: return emptyList()

        val count = toolsArray.length()
        val list = ArrayList<McpTool>(count)
        for (i in 0 until count) {
            val toolObj = toolsArray.getJSONObject(i)
            list.add(
                McpTool(
                    name = toolObj.getString("name"),
                    description = toolObj.optString("description", ""),
                    inputSchema = toolObj.optJSONObject("inputSchema") ?: JSONObject(),
                    serverId = serverId,
                ),
            )
        }
        return list
    }

    fun callTool(toolName: String, arguments: JSONObject): McpToolResult {
        if (!isInitialized) initialize()
        val params = JSONObject().apply {
            put("name", toolName)
            put("arguments", arguments)
        }

        val response = sendRequest("tools/call", params)
            ?: return McpToolResult(isError = true, content = "MCP server did not respond or connection closed")

        if (response.has("error")) {
            val error = response.getJSONObject("error")
            return McpToolResult(isError = true, content = error.optString("message", "Unknown MCP error"))
        }

        val result = response.optJSONObject("result")
            ?: return McpToolResult(isError = false, content = "")

        val isError = result.optBoolean("isError", false)
        val contentArray = result.optJSONArray("content")
        val textContent = if (contentArray != null) {
            val len = contentArray.length()
            val builder = StringBuilder(len * 64)
            for (i in 0 until len) {
                val item = contentArray.getJSONObject(i)
                if (item.optString("type") == "text") {
                    builder.append(item.optString("text"))
                }
            }
            builder.toString()
        } else {
            result.toString()
        }

        return McpToolResult(isError = isError, content = textContent)
    }

    override fun close() {
        runCatching { reader.close() }
        runCatching { writer.close() }
    }
}
