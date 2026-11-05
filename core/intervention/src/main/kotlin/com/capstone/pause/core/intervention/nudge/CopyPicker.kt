package com.capstone.pause.core.intervention.nudge

import com.capstone.pause.core.model.CardCopy
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.CopyLine
import com.capstone.pause.core.model.CopyPack
import com.capstone.pause.core.model.Tone

/**
 * 문구 팩에서 한 줄 고르기(순수). 같은 세션·같은 단계면 늘 같은 줄(시작 시각을 씨앗으로).
 * 말투가 맞는 줄이 없으면 아무 줄, 맥락 카드가 없으면 OTHER → BREAK 순으로 대신 쓴다.
 */
object CopyPicker {
    /** 허용 자리표시자(05 문서 4절). 이 밖의 자리표시자는 빈 문자열로 지운다. */
    val PLACEHOLDERS = setOf(
        "nickname", "min", "app", "context", "plan_then", "reduction", "avgMin", "topContext",
        "topContextMin", "stopRate", "timeRange", "friend", "buddy",
    )

    /** [lines] 중 [tone]에 맞는 줄 하나. 없으면 null. */
    fun pick(lines: List<CopyLine>, tone: Tone, seed: Long): CopyLine? {
        if (lines.isEmpty()) return null
        val matching = lines.filter { tone in it.tones }.ifEmpty { lines }
        return matching[Math.floorMod(seed, matching.size.toLong()).toInt()]
    }

    /** 맥락·단계의 줄 목록. */
    fun cardLines(pack: CopyPack, context: ContextSlot, level: Int): List<CopyLine> {
        fun of(card: CardCopy?): List<CopyLine> = when (level) {
            1 -> card?.l1
            2 -> card?.l2
            3 -> card?.l3
            else -> card?.l4Question
        }.orEmpty()
        return of(pack.card[context]).ifEmpty { of(pack.card[ContextSlot.OTHER]) }
            .ifEmpty { of(pack.card[ContextSlot.BREAK]) }
    }

    /** `{name}` 자리표시자를 [values]로 채운다. 값이 없거나 허용되지 않은 자리표시자는 지운다. */
    fun fill(text: String, values: Map<String, String>): String =
        PLACEHOLDER.replace(text) { m -> values[m.groupValues[1]]?.takeIf { m.groupValues[1] in PLACEHOLDERS } ?: "" }
            .replace(DOUBLE_SPACE, " ")
            .trim()

    private val PLACEHOLDER = Regex("\\{([A-Za-z_]+)\\}")
    private val DOUBLE_SPACE = Regex(" {2,}")
}

/** 화면 고정 문구(문자열 리소스에서 읽어 넘긴다). */
data class FixedTexts(
    val buddyName: String,
    /** "르르 · 사르르"처럼 완성된 보낸 사람 이름. */
    val buddySender: String,
    /** "%1$s · 맡겨 둔 잔소리" 형식. */
    val friendSenderFormat: String,
    /** 맥락 이름(카드 문구의 {context}). */
    val contextLabels: Map<ContextSlot, String>,
    /** 이유 칩: 키 → 표시 문구(순서대로). */
    val reasonChips: List<ReasonChip>,
    /** 단계(1~3) → 연장하려면 그대로 입력해야 하는 문장. 단계가 오를수록 길어진다. */
    val typeToExtend: Map<Int, String>,
)

/** 친구가 맡긴 잔소리 하나(아직 안 쓴 것). */
data class PendingPoke(val id: String, val nickname: String, val text: String)

/**
 * 문구 팩으로 카드·메시지·칭찬을 쓰는 [NudgeWriter](순수).
 * [poke]는 메시지 알림을 만들 때 한 번 묻는다: 친구 잔소리가 있으면 그것을 먼저 쓴다(ONE_SHOT 3단계).
 */
class CopyNudgeWriter(
    private val pack: CopyPack,
    private val tone: Tone,
    private val texts: FixedTexts,
    private val nickname: String?,
    private val poke: () -> PendingPoke?,
) : NudgeWriter {

    private fun values(live: LiveInput, minutes: Int?): Map<String, String> = buildMap {
        put("buddy", texts.buddyName)
        put("app", live.appLabel)
        put("context", texts.contextLabels[live.context].orEmpty())
        minutes?.let { put("min", it.toString()) }
        nickname?.let { put("nickname", it) }
    }

    private fun seed(live: LiveInput, salt: Int): Long = live.sessionStart.epochSecond + salt

    override fun card(level: Int, live: LiveInput, elapsedMinutes: Int): CardContent {
        val line = CopyPicker.pick(CopyPicker.cardLines(pack, live.context, level), tone, seed(live, level))
        val text = line?.let { CopyPicker.fill(it.text, values(live, elapsedMinutes)) }.orEmpty()
        return CardContent(
            level = level,
            context = live.context,
            appLabel = live.appLabel,
            elapsedMinutes = elapsedMinutes,
            text = text,
            copyId = line?.id,
            reasonChips = if (level == 2) texts.reasonChips else emptyList(),
            breathSeconds = FrictionLadder.breathSeconds(level),
            typeToExtend = if (level < NudgeEngine.MAX_LEVEL) texts.typeToExtend[level] else null,
            question = if (level >= NudgeEngine.MAX_LEVEL) text else null,
            extendMinutes = if (level >=
                NudgeEngine.MAX_LEVEL
            ) {
                NudgeEngine.L4_EXTEND_MIN
            } else {
                NudgeEngine.DEFAULT_EXTEND_MIN
            },
        )
    }

    override fun message(live: LiveInput, elapsedMinutes: Int): OutgoingMessage {
        poke()?.let { p ->
            return OutgoingMessage(
                senderName = String.format(texts.friendSenderFormat, p.nickname),
                fromFriend = true,
                text = p.text,
                copyId = null,
                pokeId = p.id,
            )
        }
        val line = CopyPicker.pick(pack.buddyMessage, tone, seed(live, MESSAGE_SALT))
        return OutgoingMessage(
            senderName = texts.buddySender,
            fromFriend = false,
            text = line?.let { CopyPicker.fill(it.text, values(live, elapsedMinutes)) }.orEmpty(),
            copyId = line?.id,
        )
    }

    override fun praise(live: LiveInput): String {
        val line = CopyPicker.pick(pack.praise, tone, seed(live, PRAISE_SALT))
        return line?.let { CopyPicker.fill(it.text, values(live, null)) }.orEmpty()
    }

    /** 2단계 이유 칩에 맞는 답 문구(오버레이가 칩을 고른 뒤 보여 줄 수 있다). */
    fun chipReply(chipKey: String, live: LiveInput): String? = CopyPicker.pick(
        pack.chipReply[chipKey].orEmpty(),
        tone,
        seed(live, chipKey.hashCode()),
    )?.let { CopyPicker.fill(it.text, values(live, null)) }

    private companion object {
        const val MESSAGE_SALT = 101
        const val PRAISE_SALT = 202
    }
}
