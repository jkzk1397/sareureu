package com.capstone.pause.core.ai.engine.eval

import com.capstone.pause.core.ai.engine.llm.LlmClient
import com.capstone.pause.core.ai.engine.llm.RestGeminiClient
import com.capstone.pause.core.ai.engine.mock.MockLlmClient
import com.capstone.pause.core.ai.engine.schema.AiJson
import com.capstone.pause.core.model.CopyPack
import com.capstone.pause.core.model.Strategy
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer

/**
 * AI 평가 하네스 진입점(05 문서 9절). 인자: [provider: mock|gemini] [출력 폴더].
 * 작업 폴더는 `core/ai-engine`(Gradle JavaExec 기본값)이라 데이터는 저장소 경로에서 읽는다.
 */
fun main(args: Array<String>) {
    val provider = args.getOrElse(0) { "mock" }
    val outDir = File(args.getOrElse(1) { "build/reports/ai-eval" }).apply { mkdirs() }
    val dataDir = File("src/test/resources/eval")
    val pack = AiJson.decodeFromString(CopyPack.serializer(), File(DEFAULT_PACK).readText(Charsets.UTF_8))
    val strategies = AiJson.decodeFromString(
        ListSerializer(Strategy.serializer()),
        File(STRATEGIES).readText(Charsets.UTF_8),
    )
    val mock = MockLlmClient(pack, strategies)
    val buddy = buddyName()

    val client: LlmClient = when (provider) {
        "gemini" -> RestGeminiClient.fromEnv().also {
            if (!it.isAvailable) {
                val msg = "GEMINI_API_KEY 환경변수가 없어 gemini 평가를 건너뛴다. 키를 넣고 다시 돌려 줘."
                println(msg)
                EvalReport.writeSkipped(outDir, provider, msg)
                return
            }
        }

        else -> mock
    }
    val rows = runBlocking { EvalRunner(dataDir, pack, strategies, mock, buddy).runAll(client) }
    val (md, csv) = EvalReport.write(outDir, provider, LocalDate.now().toString(), rows)
    println(EvalReport.summary(rows))
    println("aiEval provider=$provider → ${md.absolutePath}, ${csv.name}")
}

// 앱과 같은 캐릭터 이름을 쓰려고 designsystem의 strings.xml에서 읽는다(Kotlin에 이름을 쓰지 않는다).
private fun buddyName(): String {
    val xml = File(STRINGS).takeIf { it.exists() }?.readText(Charsets.UTF_8).orEmpty()
    return Regex("<string name=\"buddy_name\">([^<]+)</string>").find(xml)?.groupValues?.get(1) ?: "Buddy"
}

private const val DEFAULT_PACK = "../data/src/main/assets/copy/default_pack.json"

// 앱이 쓰는 전략 라이브러리 원본(복사본을 두지 않는다).
private const val STRATEGIES = "../data/src/main/assets/strategies.json"
private const val STRINGS = "../designsystem/src/main/res/values/strings.xml"
