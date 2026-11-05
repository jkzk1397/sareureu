"""르르 그림(단색 배경) → 투명 WebP 512px.

사용: python3 cutout.py 입력.png 출력.webp [--size 512] [--pad 0.04]
- 배경색은 네 모서리에서 자동으로 읽는다(단색·옅은 그라데이션 배경용).
- 투명 PNG를 넣으면 배경 제거 없이 크기만 맞춘다.
- 결과는 정사각형, 각설탕 바닥이 아래 여백 pad에 닿도록 놓는다(웅덩이 포함).
필요: pip install opencv-python-headless pillow numpy
"""
import argparse
import cv2
import numpy as np
from PIL import Image

ap = argparse.ArgumentParser()
ap.add_argument("src"); ap.add_argument("dst")
ap.add_argument("--size", type=int, default=512); ap.add_argument("--pad", type=float, default=0.04)
a = ap.parse_args()

im = cv2.imread(a.src, cv2.IMREAD_UNCHANGED)
if im.shape[2] == 4 and im[..., 3].min() < 250:
    rgba = im
else:
    bgr = im[..., :3]
    H, W = bgr.shape[:2]
    k = max(4, min(H, W) // 50)
    corners = np.concatenate([bgr[:k, :k], bgr[:k, -k:], bgr[-k:, :k], bgr[-k:, -k:]]).reshape(-1, 3)
    bg = np.median(corners, axis=0)
    d = np.sqrt(((bgr.astype(float) - bg) ** 2).sum(-1))
    ys, xs = np.where(d > 30)
    x0, y0, x1, y1 = max(xs.min() - 30, 0), max(ys.min() - 30, 0), min(xs.max() + 30, W), min(ys.max() + 30, H)
    mask = np.full((H, W), cv2.GC_BGD, np.uint8)
    roi = np.zeros((H, W), bool); roi[y0:y1, x0:x1] = True
    mask[roi] = cv2.GC_PR_BGD
    mask[roi & (d > 16)] = cv2.GC_PR_FGD
    mask[cv2.erode((d > 45).astype(np.uint8), np.ones((9, 9), np.uint8)) > 0] = cv2.GC_FGD
    cv2.grabCut(bgr, mask, None, np.zeros((1, 65)), np.zeros((1, 65)), 6, cv2.GC_INIT_WITH_MASK)
    m = np.where((mask == cv2.GC_FGD) | (mask == cv2.GC_PR_FGD), 255, 0).astype(np.uint8)
    n, lab, st, _ = cv2.connectedComponentsWithStats(m, 8)
    m = np.where(lab == 1 + np.argmax(st[1:, 4]), 255, 0).astype(np.uint8)
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    ff = m.copy(); cv2.floodFill(ff, np.zeros((H + 2, W + 2), np.uint8), (0, 0), 255); m |= cv2.bitwise_not(ff)
    alpha = cv2.GaussianBlur(m, (0, 0), 1.2)
    rgba = cv2.cvtColor(bgr, cv2.COLOR_BGR2BGRA); rgba[..., 3] = alpha
    af = alpha.astype(float) / 255; edge = (af > 0.02) & (af < 0.98)
    for c in range(3):  # 가장자리 배경색 번짐 제거
        ch = rgba[..., c].astype(float)
        ch[edge] = np.clip((ch[edge] - bg[c] * (1 - af[edge])) / np.maximum(af[edge], 0.25), 0, 255)
        rgba[..., c] = ch.astype(np.uint8)

ys, xs = np.where(rgba[..., 3] > 8)
x0, y0, x1, y1 = xs.min(), ys.min(), xs.max() + 1, ys.max() + 1
side = int(max(x1 - x0, y1 - y0) / (1 - 2 * a.pad))
canvas = np.zeros((side, side, 4), np.uint8)
ox = (side - (x1 - x0)) // 2; oy = side - int(side * a.pad) - (y1 - y0)
canvas[oy:oy + (y1 - y0), ox:ox + (x1 - x0)] = rgba[y0:y1, x0:x1]
out = Image.fromarray(cv2.cvtColor(canvas, cv2.COLOR_BGRA2RGBA)).resize((a.size, a.size), Image.LANCZOS)
out.save(a.dst, quality=88, method=6)
print("saved", a.dst, out.size)
