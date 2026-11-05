package com.capstone.pause.core.ai.engine.validate

import com.capstone.pause.core.model.CardCopy
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.CopyLine
import com.capstone.pause.core.model.CopyPack

/**
 * `copy_pack` 검증(05 문서 5.2절). 줄마다 검사해서 **실패한 줄만** 기본 팩 문구로 바꾼다.
 * 절반 넘게 바뀌면 재시도하도록 실패로 본다.
 */
class CopyPackValidator(private val defaultPack: CopyPack) {
    /** 고친 팩 또는 실패. [Validation.Valid.notes]에 바꾼 항목이 남는다. */
    fun validate(pack: CopyPack): Validation<CopyPack> {
        var total = 0
        var replaced = 0
        val notes = mutableListOf<String>()

        fun clean(lines: List<CopyLine>, fallback: List<CopyLine>, need: Int, where: String): List<CopyLine> {
            val kept = lines.mapIndexedNotNull { i, line ->
                total++
                val p = TextRules.problems(line.text, MAX_LINE, field = "$where[$i]") +
                    if (line.tones.isEmpty()) listOf("$where[$i]: tones 비어 있음") else emptyList()
                if (p.isEmpty()) {
                    line.copy(id = line.id.ifBlank { "ai_${where}_$i" })
                } else {
                    replaced++
                    notes += p
                    null
                }
            }
            if (kept.size >= need) return kept
            notes += "$where: 기본 문구로 채움"
            return kept + fallback.filter { f -> kept.none { it.id == f.id } }.take(need - kept.size)
        }

        val card = ContextSlot.entries.associateWith { slot ->
            val got = pack.card[slot]
            val def = defaultPack.card[slot] ?: defaultPack.card.getValue(FALLBACK_SLOT)
            val l1Need = if (slot == ContextSlot.OTHER) 1 else MIN_L1
            if (got == null) {
                notes += "card.$slot: 없어서 기본 문구"
                def
            } else {
                CardCopy(
                    l1 = clean(got.l1, def.l1, l1Need, "card.$slot.L1"),
                    l2 = clean(got.l2, def.l2, 1, "card.$slot.L2"),
                    l3 = clean(got.l3, def.l3, 1, "card.$slot.L3"),
                    l4Question = clean(got.l4Question, def.l4Question, 1, "card.$slot.L4Question"),
                )
            }
        }
        val chips = (defaultPack.chipReply.keys + pack.chipReply.keys).associateWith { key ->
            clean(pack.chipReply[key].orEmpty(), defaultPack.chipReply[key].orEmpty(), 1, "chipReply.$key")
        }
        val predictive = ContextSlot.entries.mapNotNull { slot ->
            val line = pack.predictive[slot]
            val ok = line != null && TextRules.problems(line.text, MAX_LINE).isEmpty() && line.tones.isNotEmpty()
            if (line != null) total++
            if (line != null && !ok) replaced++
            (if (ok) line else defaultPack.predictive[slot])?.let { slot to it }
        }.toMap()
        val repaired = CopyPack(
            version = pack.version.ifBlank { "ai" },
            card = card,
            chipReply = chips,
            buddyMessage = clean(pack.buddyMessage, defaultPack.buddyMessage, 1, "buddyMessage"),
            praise = clean(pack.praise, defaultPack.praise, 1, "praise"),
            predictive = predictive,
        )
        return if (total == 0 || replaced * 2 > total) {
            Validation.Invalid(listOf("절반 넘는 줄이 규칙 위반($replaced/$total)") + notes.take(MAX_REASONS))
        } else {
            Validation.Valid(repaired, notes)
        }
    }

    /** 상수. */
    companion object {
        /** 한 줄 최대 길이. */
        const val MAX_LINE = 60

        /** OTHER를 뺀 맥락의 L1 최소 개수. */
        const val MIN_L1 = 2
        private const val MAX_REASONS = 10
        private val FALLBACK_SLOT = ContextSlot.BREAK
    }
}
