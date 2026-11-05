package com.capstone.pause.core.data.copy

import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.CopyLine
import com.capstone.pause.core.model.Tone
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** 기본 문구 팩 검사(05 문서 4절 숫자 정책·5.2절·7절 금지 표현). */
class DefaultCopyPackTest {
    private val pack = CopyPackJson.parse(File("src/main/assets/copy/default_pack.json").readText())

    private val allLines: List<CopyLine> = pack.card.values.flatMap { it.l1 + it.l2 + it.l3 + it.l4Question } +
        pack.chipReply.values.flatten() + pack.buddyMessage + pack.praise + pack.predictive.values

    private val allowed = setOf(
        "nickname", "min", "app", "context", "plan_then", "reduction", "avgMin", "topContext",
        "topContextMin", "stopRate", "timeRange", "friend", "buddy",
    )

    private val banned = listOf("중독", "장애", "진단", "환자", "한심", "정신 차려", "의지가 약", "쓰레기", "루저")

    @Test
    fun everyContextHasEveryLevelInEveryTone() {
        ContextSlot.entries.forEach { slot ->
            val card = pack.card[slot]
            requireNotNull(card) { "no card for $slot" }
            listOf(card.l1, card.l2, card.l3, card.l4Question).forEachIndexed { i, lines ->
                Tone.entries.forEach { tone ->
                    assertTrue(lines.any { tone in it.tones }, "$slot level ${i + 1} has no $tone line")
                }
            }
            if (slot != ContextSlot.OTHER) assertTrue(card.l1.size >= 2, "$slot needs two L1 lines")
            assertTrue(pack.predictive.containsKey(slot), "$slot predictive")
        }
        assertEquals(setOf("BORED", "TIRED", "PROCRASTINATING", "FUN", "JUST"), pack.chipReply.keys)
    }

    @Test
    fun noDigitsOutsidePlaceholdersAndOnlyWhitelistedPlaceholders() {
        allLines.forEach { line ->
            val placeholders = Regex("\\{([^}]*)\\}").findAll(line.text).map { it.groupValues[1] }.toList()
            assertTrue(placeholders.all { it in allowed }, "unknown placeholder in ${line.id}: $placeholders")
            val outside = line.text.replace(Regex("\\{[^}]*\\}"), "")
            assertTrue(outside.none { it.isDigit() }, "digit outside placeholder in ${line.id}")
        }
    }

    @Test
    fun shortLinesAtMostOneEmojiNoBannedWordsUniqueIds() {
        allLines.forEach { line ->
            assertTrue(line.text.length <= MAX_LEN, "${line.id} too long")
            val emoji = line.text.codePoints().filter { it in SYMBOLS || it >= PICTOGRAPHS }.count()
            assertTrue(emoji <= 1, "${line.id} has $emoji emoji")
            assertTrue(banned.none { it in line.text }, "${line.id} has a banned word")
            assertTrue(line.tones.isNotEmpty(), "${line.id} has no tone")
        }
        val ids = allLines.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate ids")
    }

    private companion object {
        const val MAX_LEN = 60
        val SYMBOLS = 0x2600..0x27BF
        const val PICTOGRAPHS = 0x1F000
    }
}
