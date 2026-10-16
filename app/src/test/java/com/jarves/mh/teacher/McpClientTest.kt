package com.jarves.mh.teacher

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class McpClientTest {

    @Test
    fun `test MCP initialize handshake and tools list`() {
        val serverResponses = """
            {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"MockTutor","version":"0.6.1"}}}
            {"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"solve_equation","description":"Solves algebraic equations","inputSchema":{"type":"object"}}]}}
        """.trimIndent() + "\n"

        val serverIn = ByteArrayInputStream(serverResponses.toByteArray(Charsets.UTF_8))
        val clientOut = ByteArrayOutputStream()

        val client = McpClient("mock-tutor", serverIn, clientOut)

        val initialized = client.initialize()
        assertTrue("Handshake should succeed", initialized)

        val tools = client.listTools()
        assertEquals(1, tools.size)
        assertEquals("solve_equation", tools[0].name)
        assertEquals("Solves algebraic equations", tools[0].description)
        assertEquals("mock-tutor", tools[0].serverId)

        client.close()
    }

    @Test
    fun `test MCP tool call execution`() {
        val serverResponses = """
            {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"MockServer","version":"1.0"}}}
            {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"x = 5"}]}}
        """.trimIndent() + "\n"

        val serverIn = ByteArrayInputStream(serverResponses.toByteArray(Charsets.UTF_8))
        val clientOut = ByteArrayOutputStream()

        val client = McpClient("test-server", serverIn, clientOut)
        client.initialize()

        val args = JSONObject().put("equation", "2x = 10")
        val result = client.callTool("solve_equation", args)

        assertFalse(result.isError)
        assertEquals("x = 5", result.content)

        client.close()
    }
}
