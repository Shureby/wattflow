"""Generate the in-app product icon for wattflow_pro (512x512, no text/branding).
Gold crown on brand indigo. The three band gems echo the app icon's flow
dots: left->right growing larger and deeper purple.
"""
from PIL import Image, ImageDraw

BG = (30, 27, 51)        # #1E1B33 brand indigo
GOLD = (255, 213, 79)    # premium gold

# Flow-dot echo: light -> heavy purple, small -> large (mirrors app icon).
# (color, radius, x-offset) all as fraction of icon size. Group centered,
# dots fully separated, 1-2 gap slightly tighter than 2-3.
DOTS = [
    ((199, 184, 245), 0.026, -0.088),  # light lavender
    ((139, 110, 232), 0.033, -0.008),  # mid purple
    ((110, 85, 180), 0.041, 0.088),    # deep-but-lighter purple
]


def make():
    s = 4
    size = 512 * s
    img = Image.new("RGBA", (size, size), BG + (255,))
    d = ImageDraw.Draw(img)

    cx = size / 2
    w = size * 0.44
    base_y, top_y, mid_y = size * 0.60, size * 0.38, size * 0.28
    left, right = cx - w / 2, cx + w / 2

    crown = [
        (left, base_y), (left, top_y),
        (cx - w * 0.25, base_y - (base_y - top_y) * 0.45), (cx, mid_y),
        (cx + w * 0.25, base_y - (base_y - top_y) * 0.45), (right, top_y),
        (right, base_y),
    ]
    d.polygon(crown, fill=GOLD)

    band_h = size * 0.11
    band_top = base_y - band_h * 0.2
    band_bot = base_y + band_h
    d.rounded_rectangle([left, band_top, right, band_bot],
                        radius=band_h * 0.3, fill=GOLD)

    # three gems on the band, growing + deepening left->right, middle under
    # the crown's center spike
    gem_cy = (band_top + band_bot) / 2
    for color, rf, xoff in DOTS:
        gx = cx + size * xoff
        r = size * rf
        d.ellipse([gx - r, gem_cy - r, gx + r, gem_cy + r], fill=color)

    img.resize((512, 512), Image.LANCZOS).save("pro-icon-512.png")
    print("pro-icon-512.png")


if __name__ == "__main__":
    make()
