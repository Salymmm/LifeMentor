package com.lifementor.app.llm

import org.json.JSONObject

object JsonExtract {
    /** 从模型输出中提取第一个完整的 JSON 对象（容忍 ```json 包裹或前后废话） */
    fun obj(raw: String): JSONObject {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.substringAfter("```").let {
                if (it.startsWith("json")) it.removePrefix("json") else it
            }
            s = s.substringBefore("```")
        }
        val start = s.indexOf('{')
        if (start < 0) throw LlmException("模型没有按要求返回 JSON，请重试")
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until s.length) {
            val c = s[i]
            if (esc) { esc = false; continue }
            when (c) {
                '\\' -> if (inStr) esc = true
                '"' -> inStr = !inStr
                '{' -> if (!inStr) depth++
                '}' -> if (!inStr) {
                    depth--
                    if (depth == 0) return JSONObject(s.substring(start, i + 1))
                }
            }
        }
        throw LlmException("模型返回的 JSON 不完整，请重试")
    }
}
