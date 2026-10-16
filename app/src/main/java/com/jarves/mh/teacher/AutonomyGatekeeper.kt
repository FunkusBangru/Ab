package com.jarves.mh.teacher

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class AutonomyGatekeeper(
    @Volatile var currentMode: AutonomyMode = AutonomyMode.MODE_A_EVERY_ACTION,
) {
    private val remainingSequenceBudget = AtomicInteger(0)
    private val customToolRules = ConcurrentHashMap<String, Boolean>()
    private val allowedPathPrefixes = setOf("/workspace")
    private val deniedCommands = listOf("rm -rf /", "mkfs", "dd", "chmod -R 777")

    fun setSequenceBudget(count: Int) {
        remainingSequenceBudget.set(count.coerceAtLeast(0))
    }

    fun getSequenceBudget(): Int = remainingSequenceBudget.get()

    fun setCustomToolRule(toolName: String, isAllowed: Boolean) {
        customToolRules[toolName.lowercase()] = isAllowed
    }

    fun evaluateAction(
        toolName: String,
        operation: String,
        arguments: Map<String, String>,
        isReadOnly: Boolean = false,
        isDeterministic: Boolean = false,
        targetPath: String? = null,
        leavesDevice: Boolean = false,
    ): ActionApprovalRequest? {
        val isDestructive = if (arguments.isNotEmpty()) {
            arguments.values.any { value ->
                deniedCommands.any { dangerous -> value.contains(dangerous, ignoreCase = true) }
            }
        } else false

        if (isDestructive) {
            return buildRequest(
                operation = operation,
                toolName = toolName,
                arguments = arguments,
                targetResources = listOfNotNull(targetPath),
                expectedSideEffects = "Potentially destructive or high-risk system command",
                leavesDevice = leavesDevice,
                canUndo = false,
                riskLevel = "HIGH",
            )
        }

        return when (currentMode) {
            AutonomyMode.MODE_A_EVERY_ACTION -> {
                buildRequest(
                    operation = operation,
                    toolName = toolName,
                    arguments = arguments,
                    targetResources = listOfNotNull(targetPath),
                    expectedSideEffects = if (isReadOnly) "Read-only inspection" else "Modifies project or executes process",
                    leavesDevice = leavesDevice,
                    canUndo = !isDestructive,
                    riskLevel = if (isReadOnly) "SAFE" else "REVIEW",
                )
            }

            AutonomyMode.MODE_B_EVERY_TASK -> {
                if (leavesDevice) {
                    buildRequest(
                        operation = operation,
                        toolName = toolName,
                        arguments = arguments,
                        targetResources = listOfNotNull(targetPath),
                        expectedSideEffects = "Transmits data off device to external network",
                        leavesDevice = true,
                        canUndo = true,
                        riskLevel = "REVIEW",
                    )
                } else null
            }

            AutonomyMode.MODE_C_DEFINED_SEQUENCE -> {
                val current = remainingSequenceBudget.get()
                if (current > 0) {
                    remainingSequenceBudget.decrementAndGet()
                    null
                } else {
                    buildRequest(
                        operation = operation,
                        toolName = toolName,
                        arguments = arguments,
                        targetResources = listOfNotNull(targetPath),
                        expectedSideEffects = "Action sequence budget expired. Renewal needed.",
                        leavesDevice = leavesDevice,
                        canUndo = true,
                        riskLevel = "REVIEW",
                    )
                }
            }

            AutonomyMode.MODE_D_RESTRICTED_AUTO -> {
                if ((isReadOnly || isDeterministic) && !leavesDevice) {
                    null
                } else {
                    buildRequest(
                        operation = operation,
                        toolName = toolName,
                        arguments = arguments,
                        targetResources = listOfNotNull(targetPath),
                        expectedSideEffects = "Action alters files, executes shell, or accesses external network",
                        leavesDevice = leavesDevice,
                        canUndo = true,
                        riskLevel = "REVIEW",
                    )
                }
            }

            AutonomyMode.MODE_E_CUSTOM -> {
                val lowerTool = toolName.lowercase()
                val explicitlyAllowed = customToolRules[lowerTool] == true
                if (explicitlyAllowed && !leavesDevice) {
                    null
                } else {
                    buildRequest(
                        operation = operation,
                        toolName = toolName,
                        arguments = arguments,
                        targetResources = listOfNotNull(targetPath),
                        expectedSideEffects = "Tool '$toolName' is not on the custom allowlist",
                        leavesDevice = leavesDevice,
                        canUndo = true,
                        riskLevel = "REVIEW",
                    )
                }
            }
        }
    }

    private fun buildRequest(
        operation: String,
        toolName: String,
        arguments: Map<String, String>,
        targetResources: List<String>,
        expectedSideEffects: String,
        leavesDevice: Boolean,
        canUndo: Boolean,
        riskLevel: String,
    ): ActionApprovalRequest {
        return ActionApprovalRequest(
            operation = operation,
            toolName = toolName,
            arguments = arguments,
            targetResources = targetResources,
            expectedSideEffects = expectedSideEffects,
            leavesDevice = leavesDevice,
            canUndo = canUndo,
            riskLevel = riskLevel,
        )
    }
}
