"""르르 얼굴: 각설탕 몸(사진 한 장) 위에 코드로 그리는 얼굴.
좌표계: 몸 이미지와 같은 정사각형 viewBox 0 0 100 100.
기준점(눈 사이 가운데) = (39.7, 78.0), 기본 눈 간격 g0 = 11.3 (몸 700px 기준 79px).
작은 크기에서는 얼굴을 키운다: 눈 간격 >= 14px, 눈 높이 >= 4px, 선 굵기 >= 1.6px (2026-11 상향).
"""
INK = "#141416"
AX, AY, G0 = 39.7, 78.0, 11.3
EXPR_KO = {"neutral": "기본", "watching": "보는 중", "calling": "말 걸기",
           "worried": "걱정", "cheer": "칭찬", "sleepy": "졸림"}
KO_EXPR = {v: k for k, v in EXPR_KO.items()}


def face_svg(expr: str, size_px: float, ink: str = INK) -> str:
    u = 100.0 / size_px                      # 1px in viewBox units
    g = min(max(G0, 14 * u), 32.0)           # eye gap
    ry = max(0.095 * g, 2.0 * u)             # eye half-height
    rx = ry * 0.62
    sw = max(0.06 * g, 1.6 * u)              # stroke width

    def P(x, y):
        return f"{AX + x * g:.2f} {AY + y * g:.2f}"

    def eyes(dx=0.0, k=1.0):
        return "".join(
            f'<ellipse cx="{AX + (sx + dx) * g:.2f}" cy="{AY:.2f}" rx="{rx * (1 if k >= 1 else 1.05):.2f}" ry="{ry * k:.2f}" fill="{ink}"/>'
            for sx in (-0.5, 0.5))

    def stroke(d):
        return f'<path d="{d}" fill="none" stroke="{ink}" stroke-width="{sw:.2f}" stroke-linecap="round" stroke-linejoin="round"/>'

    def arc_eye(up=True):
        dy = -0.11 if up else 0.08
        return "".join(stroke(f"M{P(sx - 0.1, 0.03 if up else 0)} Q{P(sx, dy)} {P(sx + 0.1, 0.03 if up else 0)}")
                       for sx in (-0.5, 0.5))

    def sparkle(x, y, s):
        cx, cy, r = AX + x * g, AY + y * g, s * g
        q = r * 0.28
        return (f'<path d="M{cx:.2f} {cy - r:.2f} Q{cx + q:.2f} {cy - q:.2f} {cx + r:.2f} {cy:.2f} '
                f'Q{cx + q:.2f} {cy + q:.2f} {cx:.2f} {cy + r:.2f} Q{cx - q:.2f} {cy + q:.2f} {cx - r:.2f} {cy:.2f} '
                f'Q{cx - q:.2f} {cy - q:.2f} {cx:.2f} {cy - r:.2f}Z" fill="{ink}"/>')

    if expr == "neutral":
        return eyes() + stroke(f"M{P(-0.13, 0.17)} Q{P(0, 0.29)} {P(0.13, 0.17)}")
    if expr == "watching":   # 곁눈질: 눈이 옆으로, 입은 일자
        return eyes(dx=0.2, k=0.72) + stroke(f"M{P(-0.06, 0.22)} L{P(0.1, 0.22)}")
    if expr == "calling":    # 말 걸기: 눈썹 올리고 입 벌림
        return (eyes(k=1.1)
                + stroke(f"M{P(-0.62, -0.34)} Q{P(-0.5, -0.44)} {P(-0.38, -0.34)}")
                + stroke(f"M{P(0.38, -0.34)} Q{P(0.5, -0.44)} {P(0.62, -0.34)}")
                + f'<ellipse cx="{AX:.2f}" cy="{AY + 0.24 * g:.2f}" rx="{max(0.075 * g, 1.5 * u):.2f}" ry="{max(0.09 * g, 1.7 * u):.2f}" fill="{ink}"/>')
    if expr == "worried":    # 걱정: 안쪽 눈썹이 올라감, 입꼬리 내려감
        return (eyes()
                + stroke(f"M{P(-0.66, -0.22)} L{P(-0.36, -0.33)}")
                + stroke(f"M{P(0.66, -0.22)} L{P(0.36, -0.33)}")
                + stroke(f"M{P(-0.12, 0.27)} Q{P(0, 0.18)} {P(0.12, 0.27)}"))
    if expr == "cheer":      # 칭찬: 웃는 눈, 큰 미소, 반짝이 둘
        return (arc_eye(up=True)
                + stroke(f"M{P(-0.18, 0.15)} Q{P(0, 0.36)} {P(0.18, 0.15)}")
                + sparkle(1.0, -0.55, 0.17) + sparkle(1.28, -0.2, 0.1))
    if expr == "sleepy":     # 졸림: 감은 눈, 작은 입, z
        z = 0.22
        return (arc_eye(up=False)
                + f'<circle cx="{AX:.2f}" cy="{AY + 0.24 * g:.2f}" r="{max(0.05 * g, 1.3 * u):.2f}" fill="none" stroke="{ink}" stroke-width="{sw * 0.85:.2f}"/>'
                + stroke(f"M{P(0.9, -0.72)} L{P(0.9 + z, -0.72)} L{P(0.9, -0.72 + z)} L{P(0.9 + z, -0.72 + z)}"))
    raise ValueError(expr)


# 알림 작은 아이콘용 단색 실루엣 (24x24, 채우기만, 눈은 구멍)
SILHOUETTE = (
    '<path fill-rule="evenodd" d="'
    'M3 9.2h10.6v10.2c0 .7-.5 1.2-1.2 1.2H4.2c-.7 0-1.2-.5-1.2-1.2z'
    'M6.4 15.1a.95.95 0 1 0 0 1.9a.95.95 0 1 0 0-1.9z'
    'M10.1 15.1a.95.95 0 1 0 0 1.9a.95.95 0 1 0 0-1.9z"/>'
    '<path d="M3.4 8.2L9 4.6h11.2l-5.8 3.6z"/>'
    '<path d="M14.6 8.9l5.8-3.6v10.4c0 .5-.2.9-.6 1.1l-5.2 3.2z"/>'
    '<path d="M12.6 21.1c1.6-.1 2.9-.6 4.3-.4c1.5.2 2.5.6 2.4 1.1c-.1.6-2.6.9-5 .8c-1.6 0-2.4-.4-1.7-1.5z"/>'
)
