package com.jarves.mh.teacher

import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayList

class StreamingUiAdapter {
    private val buffer = StringBuilder()
    private val emittedBlockIds = mutableSetOf<String>()

    fun reset() {
        buffer.clear()
        emittedBlockIds.clear()
    }

    fun parsePlan(jsonString: String): UiComponentPlan {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", 1)
        val mode = root.optString("mode", "interactive_lesson")
        val title = root.optString("title", "Interactive Lesson")
        val conceptId = root.optString("conceptId", "").takeIf { it.isNotBlank() }

        val blocksArray = root.optJSONArray("blocks") ?: JSONArray()
        val blocks = ArrayList<UiBlock>(blocksArray.length())

        for (i in 0 until blocksArray.length()) {
            val blockObj = blocksArray.optJSONObject(i) ?: continue
            val block = validateAndParseBlock(blockObj)
            if (block != null) {
                blocks.add(block)
            }
        }

        return UiComponentPlan(
            version = version,
            mode = mode,
            title = title,
            conceptId = conceptId,
            blocks = blocks,
        )
    }

    fun feedChunk(chunk: String): List<UiBlock> {
        buffer.append(chunk)
        val readyBlocks = ArrayList<UiBlock>()

        val text = buffer.toString()
        var searchPos = 0

        while (searchPos < text.length) {
            val openBrace = text.indexOf('{', searchPos)
            if (openBrace == -1) break

            val closeBrace = findMatchingBrace(text, openBrace)
            if (closeBrace == -1) {
                // Incomplete JSON object fragment: wait for more chunks
                break
            }

            val candidate = text.substring(openBrace, closeBrace + 1)
            val jsonObject = runCatching { JSONObject(candidate) }.getOrNull()

            if (jsonObject != null && jsonObject.has("type") && jsonObject.has("id")) {
                val block = validateAndParseBlock(jsonObject)
                if (block != null && emittedBlockIds.add(block.id)) {
                    readyBlocks.add(block)
                }
            }

            searchPos = closeBrace + 1
        }

        return readyBlocks
    }

    fun validateAndParseBlock(obj: JSONObject): UiBlock? {
        val type = obj.optString("type", "").trim().lowercase()
        val id = obj.optString("id", "").trim()
        if (type.isBlank() || id.isBlank()) return null

        return when (type) {
            "explanation" -> {
                val content = obj.optString("content", "").trim()
                if (content.isBlank()) return null
                UiBlock.ExplanationBlock(
                    id = id,
                    content = content,
                    title = obj.optString("title", "").takeIf { it.isNotBlank() },
                )
            }

            "slider" -> {
                val label = obj.optString("label", id)
                val min = obj.optDouble("min", 0.0).toFloat()
                val max = obj.optDouble("max", 100.0).toFloat()
                if (min >= max) return null

                val step = obj.optDouble("step", 1.0).toFloat().coerceAtLeast(0.01f)
                val rawValue = obj.optDouble("value", min.toDouble()).toFloat()
                val value = rawValue.coerceIn(min, max)
                val unit = obj.optString("unit", "")

                UiBlock.SliderBlock(
                    id = id,
                    label = label,
                    min = min,
                    max = max,
                    step = step,
                    value = value,
                    unit = unit,
                )
            }

            "chart" -> {
                val dataSource = obj.optString("data_source", obj.optString("dataSource", ""))
                if (dataSource.isBlank()) return null
                UiBlock.ChartBlock(
                    id = id,
                    dataSource = dataSource,
                    title = obj.optString("title", ""),
                    xAxisLabel = obj.optString("x_axis_label", obj.optString("xAxisLabel", "Years")),
                    yAxisLabel = obj.optString("y_axis_label", obj.optString("yAxisLabel", "Amount ($)")),
                )
            }

            "formula" -> {
                val expression = obj.optString("expression", "")
                if (expression.isBlank()) return null
                UiBlock.FormulaBlock(
                    id = id,
                    label = obj.optString("label", ""),
                    expression = expression,
                    unit = obj.optString("unit", ""),
                )
            }

            "assessment" -> {
                val prompt = obj.optString("prompt", "")
                val optionsArr = obj.optJSONArray("options")
                if (prompt.isBlank() || optionsArr == null || optionsArr.length() < 2) return null

                val options = ArrayList<String>(optionsArr.length())
                for (i in 0 until optionsArr.length()) {
                    options.add(optionsArr.optString(i))
                }

                val correctIdx = obj.optInt("correct_option", obj.optInt("correctOptionIndex", 0))
                val explanation = obj.optString("explanation", "")
                val misconception = obj.optString("misconception", "").takeIf { it.isNotBlank() }

                UiBlock.AssessmentBlock(
                    id = id,
                    prompt = prompt,
                    options = options,
                    correctOptionIndex = correctIdx.coerceIn(0, options.size - 1),
                    explanation = explanation,
                    misconception = misconception,
                )
            }

            else -> null
        }
    }

    private fun findMatchingBrace(text: String, startIdx: Int): Int {
        var depth = 0
        var inString = false
        var escape = false

        for (i in startIdx until text.length) {
            val c = text[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }
}
