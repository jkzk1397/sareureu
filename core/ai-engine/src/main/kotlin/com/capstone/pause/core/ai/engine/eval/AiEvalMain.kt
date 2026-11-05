package com.capstone.pause.core.ai.engine.eval

import java.io.File

/**
 * AI 평가 하네스 진입점. 0단계 스텁: 빈 보고서만 쓴다(4단계에서 실제 평가로 바꾼다).
 * 인자: [provider] [출력 폴더]
 */
fun main(args: Array<String>) {
    val provider = args.getOrElse(0) { "mock" }
    val outDir = File(args.getOrElse(1) { "build/reports/ai-eval" })
    outDir.mkdirs()
    File(outDir, "report.md").writeText(
        "# AI 평가 보고서\n\n- 제공자: $provider\n- 상태: 스텁(4단계에서 실제 평가로 바꾼다)\n- 항목: 0\n",
    )
    File(outDir, "report.csv").writeText("case_id,feature,schema_ok,validator_ok,latency_ms\n")
    println("aiEval(stub) provider=$provider → ${outDir.absolutePath}")
}
