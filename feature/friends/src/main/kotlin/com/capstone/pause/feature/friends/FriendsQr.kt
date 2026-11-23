package com.capstone.pause.feature.friends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.capstone.pause.core.designsystem.theme.LightPauseColors
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** QR 칸(정사각형 [size]×[size], 행 우선). 테두리 여백은 그릴 때 더한다. */
data class QrCode(val size: Int, val dark: List<Boolean>) {
    /** (x, y) 칸이 검은지. */
    fun isDark(x: Int, y: Int): Boolean = dark[y * size + x]
}

/** 초대 주소 → QR 칸(순수, zxing). 실패하면 null. */
object InviteQr {
    /** [text]를 오류 정정 M 단계로. */
    fun encode(text: String): QrCode? = runCatching {
        val matrix = Encoder.encode(text, ErrorCorrectionLevel.M).matrix
        val size = matrix.width
        QrCode(size, List(size * size) { i -> matrix.get(i % size, i / size).toInt() == 1 })
    }.getOrNull()
}

/**
 * QR 그리기. 카메라가 잘 읽도록 다크 테마에서도 밝은 바탕에 어두운 칸(라이트 토큰)으로, 둘레에 4칸 여백.
 */
@Composable
internal fun QrImage(code: QrCode, description: String, modifier: Modifier = Modifier) {
    val light = LightPauseColors.surface
    val dark = LightPauseColors.ink
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = description },
    ) {
        val cells = code.size + QUIET_ZONE * 2
        val cell = size.minDimension / cells
        drawRect(light)
        for (y in 0 until code.size) {
            for (x in 0 until code.size) {
                if (code.isDark(x, y)) {
                    drawRect(
                        color = dark,
                        topLeft = Offset((x + QUIET_ZONE) * cell, (y + QUIET_ZONE) * cell),
                        // 칸 사이 머리카락 틈이 생기지 않게 조금 겹친다.
                        size = Size(cell + OVERLAP, cell + OVERLAP),
                    )
                }
            }
        }
    }
}

private const val QUIET_ZONE = 4
private const val OVERLAP = 0.5f
