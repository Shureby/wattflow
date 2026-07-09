"""Generate Play Store assets: icon-512.png and feature-graphic-1024x500.png.

Renders the Battery Pulse brand mark (same geometry as the in-app adaptive
icon vector) with Pillow at 4x and downsamples for antialiasing.

Usage: python generate.py
"""
from PIL import Image, ImageDraw, ImageFont

BG = (30, 27, 51)          # #1E1B33
PURPLE = (167, 139, 250)   # #A78BFA
LIGHT = (237, 233, 254)    # #EDE9FE
WHITE = (255, 255, 255)


def draw_mark(draw, cx, cy, unit, with_bolt=False):
    """Battery Pulse mark. `unit` = pixels per viewport unit (108-based art,
    coordinates below are the vector's, relative to center (54, 54))."""
    def x(v):  # viewport x -> px
        return cx + (v - 54) * unit

    def y(v):
        return cy + (v - 54) * unit

    def blend(alpha):
        return tuple(int(BG[i] + (PURPLE[i] - BG[i]) * alpha) for i in range(3))

    # flow dots entering the battery
    for dcx, r, alpha in ((12, 4, 0.35), (22, 4.5, 0.65), (32, 5, 1.0)):
        draw.ellipse(
            [x(dcx) - r * unit, y(54) - r * unit, x(dcx) + r * unit, y(54) + r * unit],
            fill=blend(alpha),
        )
    # battery body
    stroke = round(5 * unit)
    draw.rounded_rectangle(
        [x(42), y(36), x(86), y(72)], radius=8 * unit, outline=LIGHT, width=stroke
    )
    # terminal tip
    draw.rounded_rectangle([x(88), y(46), x(96), y(62)], radius=3 * unit, fill=LIGHT)
    # charge fill
    draw.rounded_rectangle([x(49), y(43), x(70), y(65)], radius=4 * unit, fill=PURPLE)
    if with_bolt:
        pts = [(61, 45), (55.5, 55.5), (59.5, 55.5), (57.5, 63.5), (64, 52.5),
               (60.2, 52.5), (63, 45)]
        draw.polygon([(x(px), y(py)) for px, py in pts], fill=WHITE)


def make_icon():
    s = 4  # supersample
    size = 512 * s
    img = Image.new("RGB", (size, size), BG)
    d = ImageDraw.Draw(img)
    # content sized so the mark fills ~80% of the square
    draw_mark(d, size / 2, size / 2, unit=size * 0.8 / 108 * 1.28)
    img = img.resize((512, 512), Image.LANCZOS)
    img.save("icon-512.png")
    print("icon-512.png")


def font(path_bold, px):
    for p in (path_bold, "C:/Windows/Fonts/arialbd.ttf"):
        try:
            return ImageFont.truetype(p, px)
        except OSError:
            continue
    return ImageFont.load_default()


def make_feature_graphic():
    s = 2
    w, h = 1024 * s, 500 * s
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)
    # vertical gradient #241E3A -> #141020
    top, bottom = (36, 30, 58), (20, 16, 32)
    for row in range(h):
        t = row / h
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        d.line([(0, row), (w, row)], fill=c)

    # brand mark on the left, with bolt
    draw_mark(d, w * 0.22, h * 0.5, unit=h * 0.78 / 108, with_bolt=True)

    # wordmark + taglines on the right
    title_f = font("C:/Windows/Fonts/segoeuib.ttf", int(104 * s))
    tag_f = font("C:/Windows/Fonts/seguisb.ttf", int(46 * s))
    sub_f = font("C:/Windows/Fonts/segoeui.ttf", int(30 * s))
    tx = w * 0.42
    d.text((tx, h * 0.26), "WattFlow", font=title_f, fill=WHITE)
    d.text((tx + 6 * s, h * 0.56), "See the real watts.", font=tag_f, fill=PURPLE)
    d.text((tx + 6 * s, h * 0.72), "Wired · Wireless · Open source · No ads",
           font=sub_f, fill=(150, 143, 175))

    img = img.resize((1024, 500), Image.LANCZOS)
    img.save("feature-graphic-1024x500.png")
    print("feature-graphic-1024x500.png")


if __name__ == "__main__":
    make_icon()
    make_feature_graphic()
