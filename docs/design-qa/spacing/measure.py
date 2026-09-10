#!/usr/bin/env python3
"""Measure rendered spacing on an Artistant screenshot.

Finds horizontal bands of content, reports each band's left/right inset from the
screen edge in dp, and the vertical gap to the next band. Everything is measured
from pixels, so it reflects what actually renders, not what the source says.
"""
import sys, json
from PIL import Image

DENSITY = float(sys.argv[2]) if len(sys.argv) > 2 else 2.75

def px2dp(px): return round(px / DENSITY, 1)

def analyse(path, top_skip=0, bottom_skip=0):
    im = Image.open(path).convert("RGB")
    W, H = im.size
    px = im.load()
    # Background = the modal colour of the row strip just under the status bar and
    # of the page as a whole; sample several rows to be safe.
    from collections import Counter
    c = Counter()
    for y in range(int(H*0.25), int(H*0.75), 7):
        for x in range(0, W, 11):
            c[px[x, y]] += 1
    bg = c.most_common(1)[0][0]
    def is_bg(p, tol=6):
        return abs(p[0]-bg[0]) <= tol and abs(p[1]-bg[1]) <= tol and abs(p[2]-bg[2]) <= tol
    # Row occupancy: for each y, the leftmost and rightmost non-background pixel.
    rows = []
    y0, y1 = top_skip, H - bottom_skip
    for y in range(y0, y1):
        l = r = None
        for x in range(W):
            if not is_bg(px[x, y]):
                l = x; break
        if l is not None:
            for x in range(W-1, -1, -1):
                if not is_bg(px[x, y]):
                    r = x; break
        rows.append((y, l, r))
    # Group consecutive occupied rows into bands, allowing small gaps inside a band.
    bands, cur = [], None
    GAP_TOL = 4
    blank = 0
    for y, l, r in rows:
        if l is None:
            blank += 1
            if cur and blank > GAP_TOL:
                bands.append(cur); cur = None
        else:
            blank = 0
            if cur is None:
                cur = {"top": y, "bottom": y, "left": l, "right": r}
            else:
                cur["bottom"] = y
                cur["left"] = min(cur["left"], l)
                cur["right"] = max(cur["right"], r)
    if cur: bands.append(cur)
    out = []
    for i, b in enumerate(bands):
        gap = (bands[i+1]["top"] - b["bottom"] - 1) if i+1 < len(bands) else None
        out.append({
            "top_px": b["top"], "bottom_px": b["bottom"],
            "h_dp": px2dp(b["bottom"]-b["top"]+1),
            "left_dp": px2dp(b["left"]), "right_dp": px2dp(W-1-b["right"]),
            "left_px": b["left"], "right_px": W-1-b["right"],
            "gap_to_next_dp": px2dp(gap) if gap is not None else None,
            "centre_off_dp": px2dp(((b["left"] + b["right"])/2) - (W-1)/2),
        })
    return {"file": path, "size": [W, H], "bg": list(bg), "density": DENSITY, "bands": out}

if __name__ == "__main__":
    r = analyse(sys.argv[1], top_skip=int(sys.argv[3]) if len(sys.argv)>3 else 0,
                bottom_skip=int(sys.argv[4]) if len(sys.argv)>4 else 0)
    print(json.dumps(r, indent=1))
