package com.capstone.pause.feature.report

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.capstone.pause.core.ai.report.WeeklyReportState
import com.capstone.pause.core.designsystem.R as DesignR
import com.capstone.pause.core.designsystem.component.durationLabel
import com.capstone.pause.core.designsystem.component.labelRes
import com.capstone.pause.core.model.ContextSlot
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * AI·모의 문장의 자리표시자(`{topContext}` 등)를 코드가 센 값으로 채운다(숫자는 코드, 말은 AI — ADR-0002).
 * 값을 모르는 자리표시자는 지운다.
 */
@Composable
internal fun rememberPlaceholderFiller(report: WeeklyReportState): (String) -> String {
    val top = report.observations.firstOrNull { it.kind == "TOP_CONTEXT" }
    val topSlot = ContextSlot.entries.firstOrNull { it.name == top?.labels?.get("context") }
    val values = buildMap {
        put("buddy", stringResource(DesignR.string.buddy_name))
        put("avgMin", durationLabel(report.avgMinPerDay.roundToInt()))
        topSlot?.let { put("topContext", stringResource(it.labelRes())) }
        top?.numbers?.get("minutes")?.let { put("topContextMin", durationLabel(it.roundToInt())) }
        report.reductionRate?.let {
            put("reduction", stringResource(R.string.report_percent, (abs(it) * PERCENT).roundToInt()))
        }
        report.stopRate?.let { put("stopRate", stringResource(R.string.report_percent, (it * PERCENT).roundToInt())) }
    }
    return { text -> fillPlaceholders(text, values) }
}

/** `{key}`를 [values]로 바꾸고, 모르는 키는 지운 뒤 겹친 공백을 정리한다(순수 함수). */
internal fun fillPlaceholders(text: String, values: Map<String, String>): String {
    val sb = StringBuilder()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val end = if (c == '{') text.indexOf('}', i + 1) else -1
        if (end > i && text.substring(i + 1, end).all { it.isLetter() || it == '_' }) {
            sb.append(values[text.substring(i + 1, end)].orEmpty())
            i = end + 1
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString().replace("  ", " ").trim()
}
