"""
Motion-compensated stability of a rendered shot, for the distant part of the picture: each frame is aligned to the one
before by a homography fitted to features in the far band (distant land moves almost like a picture at infinity),
and what is left after alignment is instability: tiles popping or swapping, shimmer, ghosting. Prints per-frame
residuals, flags spikes, and writes renders/<shot>/stability.png (the residual over time) and the worst frame's
residual map as stability-worst.png.

    python tools/stability.py SHOT [--band 0.08,0.45] [--from F --to F]
"""
import argparse
import os
import sys

import cv2
import numpy as np

REEL = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("shot")
    ap.add_argument("--band", default="0.08,0.45", help="rows (fractions) holding the distance")
    ap.add_argument("--from", dest="start", type=int, default=0)
    ap.add_argument("--to", dest="end", type=int, default=10**9)
    a = ap.parse_args()
    folder = os.path.join(REEL, "renders", a.shot, "frames")
    files = sorted(f for f in os.listdir(folder) if f.endswith(".png"))[a.start:a.end + 1]
    if len(files) < 3:
        sys.exit("need frames")
    top_f, bottom_f = (float(v) for v in a.band.split(","))
    orb = cv2.ORB_create(3000)
    matcher = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=True)

    def grey(f):
        img = cv2.imread(os.path.join(folder, f), cv2.IMREAD_GRAYSCALE)
        return cv2.resize(img, (img.shape[1] // 2, img.shape[0] // 2), interpolation=cv2.INTER_AREA)

    prev = grey(files[0])
    h, w = prev.shape
    top, bottom = int(top_f * h), int(bottom_f * h)
    mask = np.zeros_like(prev)
    mask[top:bottom] = 255
    residuals = []
    worst = (0, None, None)
    for f in files[1:]:
        cur = grey(f)
        k1, d1 = orb.detectAndCompute(prev, mask)
        k2, d2 = orb.detectAndCompute(cur, mask)
        r = float("nan")
        if d1 is not None and d2 is not None and len(k1) > 20 and len(k2) > 20:
            matches = matcher.match(d1, d2)
            if len(matches) > 20:
                src = np.float32([k1[m.queryIdx].pt for m in matches])
                dst = np.float32([k2[m.trainIdx].pt for m in matches])
                H, _ = cv2.findHomography(src, dst, cv2.RANSAC, 1.5)
                if H is not None:
                    warped = cv2.warpPerspective(prev, H, (w, h), flags=cv2.INTER_LINEAR)
                    valid = cv2.warpPerspective(np.full_like(prev, 255), H, (w, h)) > 250
                    diff = cv2.absdiff(warped, cur).astype(np.float32)
                    region = np.zeros_like(valid)
                    region[top:bottom] = True
                    sel = valid & region
                    r = float(diff[sel].mean())
                    if r > worst[0]:
                        worst = (r, f, (diff * sel).clip(0, 255).astype(np.uint8))
        residuals.append(r)
        prev = cur
    res = np.array(residuals)
    ok = res[~np.isnan(res)]
    median = float(np.median(ok))
    spikes = [(files[i + 1][:-4], round(float(v), 2)) for i, v in enumerate(res) if v > max(2 * median, median + 1.0)]
    print(f"{a.shot}: far-band residual after alignment: median {median:.2f}, p95 {np.percentile(ok, 95):.2f}, max {ok.max():.2f}")
    print(f"  spikes (> 2x median): {len(spikes)}: {spikes[:20]}")
    # a game-tick rhythm: at 30 fps and 20 ticks a second, ticks fall on two frames in three
    clean = np.nan_to_num(res, nan=median)
    detrended = clean - np.convolve(clean, np.ones(9) / 9, mode="same")
    core = detrended[5:-5] if len(detrended) > 20 else detrended
    spectrum = np.abs(np.fft.rfft(core)) ** 2
    freqs = np.fft.rfftfreq(len(core))
    band = (freqs > 0.3) & (freqs < 0.37)
    share = spectrum[band].sum() / max(spectrum[1:].sum(), 1e-9)
    print(f"  share of frame-to-frame variation at the 3-frame (tick) rhythm: {share:.0%}; detrended jitter rms {core.std():.3f}")
    if len(spikes) >= 3:
        idx = [int(s[0]) for s in spikes]
        gaps = np.diff(idx)
        vals, counts = np.unique(gaps, return_counts=True)
        print("  gaps between spikes:", dict(zip(vals.tolist(), counts.tolist())))

    plot_h, plot_w = 260, 1200
    plot = np.full((plot_h, plot_w, 3), 245, np.uint8)
    peak = max(ok.max(), 1e-6) * 1.1
    pts = [(int(i / max(1, len(res) - 1) * (plot_w - 20)) + 10, plot_h - 10 - int((0 if np.isnan(v) else v) / peak * (plot_h - 30)))
           for i, v in enumerate(res)]
    cv2.polylines(plot, [np.array(pts, np.int32)], False, (50, 70, 200), 2)
    cv2.line(plot, (10, plot_h - 10 - int(median / peak * (plot_h - 30))), (plot_w - 10, plot_h - 10 - int(median / peak * (plot_h - 30))), (150, 150, 150), 1)
    cv2.putText(plot, f"{a.shot} far residual per frame (grey: median {median:.2f})", (10, 18), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (40, 40, 40), 1)
    out = os.path.join(REEL, "renders", a.shot)
    cv2.imwrite(os.path.join(out, "stability.png"), plot)
    if worst[1]:
        cv2.imwrite(os.path.join(out, "stability-worst.png"), cv2.applyColorMap(cv2.convertScaleAbs(worst[2], alpha=6), cv2.COLORMAP_INFERNO))
        print(f"  worst frame {worst[1]} ({worst[0]:.2f}) -> stability-worst.png")


if __name__ == "__main__":
    main()
