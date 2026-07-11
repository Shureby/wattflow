"""Generate the in-app product icon for wattflow_pro (512x512, no text/branding).
A simple crown mark on the brand indigo — signals 'premium unlock'.
"""
from PIL import Image, ImageDraw

BG = (30, 27, 51)        # #1E1B33 brand indigo
GOLD = (255, 213, 79)    # warm premium accent


def make():
    s = 4
    size = 512 * s
    img = Image.new("RGB", (size, size), BG)
    d = ImageDraw.Draw(img)

    cx = size / 2
    w = size * 0.44          # crown width
    base_y = size * 0.62
    top_y = size * 0.40
    mid_y = size * 0.30      # center spike reaches higher

    left = cx - w / 2
    right = cx + w / 2

    # crown outline: 5 points up (peaks) + band
    crown = [
        (left, base_y),
        (left, top_y),
        (cx - w * 0.25, base_y - (base_y - top_y) * 0.45),
        (cx, mid_y),
        (cx + w * 0.25, base_y - (base_y - top_y) * 0.45),
        (right, top_y),
        (right, base_y),
    ]
    d.polygon(crown, fill=GOLD)

    # crown band
    band_h = size * 0.10
    d.rounded_rectangle(
        [left, base_y - band_h * 0.2, right, base_y + band_h],
        radius=band_h * 0.3, fill=GOLD,
    )

    # three gem dots on the band (cut-outs in bg colour)
    for fx in (0.5 - 0.22, 0.5, 0.5 + 0.22):
        gx = left + (right - left) * fx
        r = size * 0.028
        d.ellipse([gx - r, base_y + band_h * 0.25 - r,
                   gx + r, base_y + band_h * 0.25 + r], fill=BG)

    img = img.resize((512, 512), Image.LANCZOS)
    img.save("pro-icon-512.png")
    print("pro-icon-512.png")


if __name__ == "__main__":
    make()
