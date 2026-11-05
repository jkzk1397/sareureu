package com.capstone.pause.core.ai.engine.prompt

import com.capstone.pause.core.ai.engine.llm.AiFeature

/** 채운 프롬프트: 시스템 지시와 사용자 메시지. */
data class RenderedPrompt(val system: String, val user: String)

/** 프롬프트 파일 하나(머리말 + 본문). */
data class PromptTemplate(
    val id: String,
    val version: Int,
    val outputSchema: String,
    /** 모의·평가용으로만 읽는다. */
    val temperature: Double?,
    val providers: List<String>,
    val meta: Map<String, String>,
    val body: String,
) {
    /** `{{변수}}`를 채운다. [userTextVars]의 값은 `<user_text>` 태그를 지워 주입을 막는다. 빠진 변수는 오류. */
    fun render(vars: Map<String, String>, userTextVars: Set<String> = USER_TEXT_VARS): String = VAR.replace(body) { m ->
        val name = m.groupValues[1]
        val value = vars[name] ?: throw IllegalArgumentException("프롬프트 $id 변수 없음: $name")
        if (name in userTextVars) fence(value) else value
    }

    /** 채운 뒤 `=== user ===` 줄을 기준으로 (시스템 지시, 사용자 메시지)로 나눈다. 구분 줄이 없으면 전부 시스템. */
    fun renderParts(vars: Map<String, String>): RenderedPrompt {
        val text = render(vars)
        val i = text.indexOf(USER_MARK)
        return if (i < 0) {
            RenderedPrompt(text.trim(), "")
        } else {
            RenderedPrompt(text.substring(0, i).trim(), text.substring(i + USER_MARK.length).trim())
        }
    }

    /** 상수. */
    companion object {
        /** 시스템/사용자 구분 줄. */
        const val USER_MARK = "=== user ==="

        /** 사용자·친구가 쓴 글을 담는 변수. */
        val USER_TEXT_VARS = setOf("user_notes", "user_text")
        private val VAR = Regex("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}")
        private val TAG = Regex("</?\\s*user_text\\s*>", RegexOption.IGNORE_CASE)

        /** 사용자 글 안의 `<user_text>` 태그를 지워 바깥으로 빠져나가지 못하게 한다. */
        fun fence(text: String): String = TAG.replace(text, "")
    }
}

/**
 * `resources/prompts/<id>_v<n>.md`를 읽는다(05 문서 3절). 기존 버전 파일은 고치지 않고,
 * 새 버전을 추가한 뒤 [CURRENT]의 번호만 바꾼다.
 */
class PromptRegistry(private val loader: (String) -> String? = ::readResource) {
    private val cache = HashMap<String, PromptTemplate>()

    /** 기능의 현재 버전. */
    fun current(feature: AiFeature): PromptTemplate =
        get(feature.promptId, CURRENT[feature] ?: error("현재 버전 없음: ${feature.promptId}"))

    /** 특정 버전. */
    fun get(id: String, version: Int): PromptTemplate = cache.getOrPut("${id}_v$version") {
        val path = "prompts/${id}_v$version.md"
        parse(loader(path) ?: throw IllegalStateException("프롬프트 파일 없음: $path"))
    }

    /** 상수와 파서. */
    companion object {
        /** 기능별 현재 프롬프트 버전. 바꿀 땐 새 파일 + 평가 결과를 같은 PR에. */
        val CURRENT: Map<AiFeature, Int> = mapOf(
            AiFeature.WEEKLY_REPORT to 1,
            AiFeature.COPY_PACK to 1,
            AiFeature.CONTEXT_CHECK to 1,
            AiFeature.COACH_CHAT to 1,
            AiFeature.POKE_DRAFT to 1,
        )

        private fun readResource(path: String): String? =
            PromptRegistry::class.java.classLoader?.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            }

        /** 머리말(`---` 사이 `키: 값`)과 본문을 나눈다. 값의 `# 주석`과 `[a, b]` 목록을 처리한다. */
        fun parse(text: String): PromptTemplate {
            val normalized = text.replace("\r\n", "\n")
            require(normalized.startsWith("---\n")) { "머리말(---)이 없음" }
            val end = normalized.indexOf("\n---", startIndex = FRONT_START)
            require(end > 0) { "머리말이 닫히지 않음" }
            val meta = normalized.substring(FRONT_START, end).lines()
                .map { it.substringBefore(" #").trim() }
                .filter { it.isNotEmpty() && ':' in it }
                .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
            val body = normalized.substring(end + FRONT_END_LEN).trimStart('-', '\n').trim()
            return PromptTemplate(
                id = meta["id"] ?: error("id 없음"),
                version = meta["version"]?.toIntOrNull() ?: error("version 없음"),
                outputSchema = meta["output_schema"] ?: error("output_schema 없음"),
                temperature = meta["temperature"]?.toDoubleOrNull(),
                providers = meta["providers"]?.trim('[', ']')?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: emptyList(),
                meta = meta,
                body = body,
            )
        }

        private const val FRONT_START = 4
        private const val FRONT_END_LEN = 4
    }
}
