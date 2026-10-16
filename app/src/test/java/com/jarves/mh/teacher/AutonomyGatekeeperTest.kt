package com.jarves.mh.teacher

import org.junit.Assert.*
import org.junit.Test

class AutonomyGatekeeperTest {

    @Test
    fun `test Mode A requires approval for all operations`() {
        val gatekeeper = AutonomyGatekeeper(AutonomyMode.MODE_A_EVERY_ACTION)

        val readApproval = gatekeeper.evaluateAction(
            toolName = "view_file",
            operation = "read_file",
            arguments = mapOf("path" to "/workspace/README.md"),
            isReadOnly = true,
        )
        assertNotNull("Mode A must require approval even for read-only actions", readApproval)

        val writeApproval = gatekeeper.evaluateAction(
            toolName = "write_to_file",
            operation = "write_file",
            arguments = mapOf("path" to "/workspace/test.txt"),
            isReadOnly = false,
        )
        assertNotNull("Mode A must require approval for file write actions", writeApproval)
    }

    @Test
    fun `test Mode D allows read-only and deterministic math automatically`() {
        val gatekeeper = AutonomyGatekeeper(AutonomyMode.MODE_D_RESTRICTED_AUTO)

        val mathResult = gatekeeper.evaluateAction(
            toolName = "calculator",
            operation = "evaluate_math",
            arguments = mapOf("expr" to "2+2"),
            isDeterministic = true,
        )
        assertNull("Mode D must allow deterministic math without user approval prompt", mathResult)

        val readResult = gatekeeper.evaluateAction(
            toolName = "view_file",
            operation = "read_file",
            arguments = mapOf("path" to "/workspace/README.md"),
            isReadOnly = true,
        )
        assertNull("Mode D must allow safe read-only actions automatically", readResult)

        val shellResult = gatekeeper.evaluateAction(
            toolName = "run_command",
            operation = "execute_command",
            arguments = mapOf("cmd" to "ls"),
            isReadOnly = false,
        )
        assertNotNull("Mode D must still require approval for shell execution", shellResult)
    }

    @Test
    fun `test Mode C respects sequence budget and prompts upon exhaustion`() {
        val gatekeeper = AutonomyGatekeeper(AutonomyMode.MODE_C_DEFINED_SEQUENCE)
        gatekeeper.setSequenceBudget(2)

        val action1 = gatekeeper.evaluateAction("tool_1", "op", emptyMap())
        assertNull("First action within budget should proceed", action1)
        assertEquals(1, gatekeeper.getSequenceBudget())

        val action2 = gatekeeper.evaluateAction("tool_2", "op", emptyMap())
        assertNull("Second action within budget should proceed", action2)
        assertEquals(0, gatekeeper.getSequenceBudget())

        val action3 = gatekeeper.evaluateAction("tool_3", "op", emptyMap())
        assertNotNull("Action exceeding sequence budget must request approval", action3)
    }

    @Test
    fun `test dangerous commands are always intercepted with HIGH risk`() {
        val gatekeeper = AutonomyGatekeeper(AutonomyMode.MODE_D_RESTRICTED_AUTO)

        val destructive = gatekeeper.evaluateAction(
            toolName = "run_command",
            operation = "execute_command",
            arguments = mapOf("cmd" to "rm -rf /"),
        )
        assertNotNull(destructive)
        assertEquals("HIGH", destructive?.riskLevel)
        assertFalse(destructive?.canUndo ?: true)
    }

    @Test
    fun `test Mode E custom tool allowlist`() {
        val gatekeeper = AutonomyGatekeeper(AutonomyMode.MODE_E_CUSTOM)
        gatekeeper.setCustomToolRule("my_trusted_tool", true)

        val allowed = gatekeeper.evaluateAction(
            toolName = "my_trusted_tool",
            operation = "op",
            arguments = emptyMap(),
        )
        assertNull("Allowlisted tool in Mode E should execute automatically", allowed)

        val unlisted = gatekeeper.evaluateAction(
            toolName = "untrusted_tool",
            operation = "op",
            arguments = emptyMap(),
        )
        assertNotNull("Unlisted tool in Mode E must request approval", unlisted)
    }
}
