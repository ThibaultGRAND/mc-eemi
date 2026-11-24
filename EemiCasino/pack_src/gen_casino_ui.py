# -*- coding: utf-8 -*-
"""Icones des menus, fonds dessines des menus (police), machines a sous et video poker en 3D.

Sortie dans EemiCasino/pack/assets/eemicraft/ (a lancer APRES gen_casino_pack.py, qui produit roulette/fortune/plinko).
Conventions :
 - icones : item eemicraft:casino/icone/<nom> (Icons.java) ;
 - fonds : police eemicraft:casino, caractere U+E101 + index de BACKGROUNDS (meme ordre que Gui.java),
   espaces negatifs U+E001 (-8) et U+E002 (-169) ;
 - machines : eemicraft:casino/machine_sous, machine_levier, video_poker (Machines.java).
"""
import json
import math
import os

from PIL import Image, ImageDraw, ImageFont, ImageFilter

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pack", "assets", "eemicraft")
TEX = os.path.join(BASE, "textures", "item", "casino")
ICON_TEX = os.path.join(TEX, "icone")
MOD = os.path.join(BASE, "models", "item", "casino")
ICON_MOD = os.path.join(MOD, "icone")
ITM = os.path.join(BASE, "items", "casino")
ICON_ITM = os.path.join(ITM, "icone")
FONT_TEX = os.path.join(BASE, "textures", "font", "casino")
FONT_DEF = os.path.join(BASE, "font")

FB = "C:/Windows/Fonts/arialbd.ttf"
FK = "C:/Windows/Fonts/ariblk.ttf"
GOLD = (236, 186, 52, 255)
GOLD_D = (150, 100, 20, 255)
WHITE = (255, 255, 255, 255)
BLACK = (20, 20, 24, 255)
RED = (200, 30, 40, 255)
GREEN = (40, 170, 70, 255)
BLUE = (40, 110, 220, 255)

WHEEL = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26]
REDS = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}


def font(size, path=FB):
    return ImageFont.truetype(path, int(size))


def save_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def center_text(d, cx, cy, txt, f, fill, outline=None, ow=3):
    bb = f.getbbox(txt)
    x = cx - (bb[2] - bb[0]) / 2 - bb[0]
    y = cy - (bb[3] - bb[1]) / 2 - bb[1]
    if outline:
        for ox in range(-ow, ow + 1):
            for oy in range(-ow, ow + 1):
                if ox * ox + oy * oy <= ow * ow and (ox or oy):
                    d.text((x + ox, y + oy), txt, font=f, fill=outline)
    d.text((x, y), txt, font=f, fill=fill)


def fit_font(txt, maxw, size, path=FK):
    while size > 8:
        f = font(size, path)
        bb = f.getbbox(txt)
        if bb[2] - bb[0] <= maxw:
            return f
        size -= 2
    return font(size, path)


# =============================================================================== icones

S = 128  # dessin des icones, reduites a 32


def canvas():
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def badge(d, fill, outline=GOLD, r=26, inset=6):
    d.rounded_rectangle([inset, inset, S - inset, S - inset], radius=r, fill=outline)
    d.rounded_rectangle([inset + 8, inset + 8, S - inset - 8, S - inset - 8], radius=r - 6, fill=fill)


def disc(d, cx, cy, r, fill, outline=None, w=0):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill, outline=outline, width=w)


def text_badge(txt, fill, fg=WHITE, outline=GOLD, size=60):
    img, d = canvas()
    badge(d, fill, outline)
    center_text(d, S / 2, S / 2, txt, fit_font(txt, S - 32, size), fg, outline=(0, 0, 0, 160), ow=3)
    return img


def chip(color, label, edge=WHITE, fg=WHITE):
    img, d = canvas()
    disc(d, 64, 64, 58, (0, 0, 0, 90))
    disc(d, 64, 62, 56, color)
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        x, y = 64 + 46 * math.sin(a), 62 - 46 * math.cos(a)
        d.rounded_rectangle([x - 9, y - 9, x + 9, y + 9], radius=4, fill=edge)
    disc(d, 64, 62, 36, color, outline=edge, w=4)
    center_text(d, 64, 62, label, fit_font(label, 60, 40), fg, outline=(0, 0, 0, 150), ow=2)
    return img


def chip_stack(colors):
    img, d = canvas()
    n = len(colors)
    for i, c in enumerate(colors):
        y = 96 - i * 16
        d.ellipse([18, y - 18, 110, y + 18], fill=(0, 0, 0, 110))
        d.ellipse([18, y - 22, 110, y + 14], fill=c, outline=WHITE, width=3)
        for k in range(6):
            x = 26 + k * 15
            d.rectangle([x, y - 6, x + 6, y + 4], fill=WHITE)
    return img


def card_small(d, x, y, w, h, red=False, txt="A"):
    d.rounded_rectangle([x, y, x + w, y + h], radius=8, fill=(250, 250, 246), outline=(60, 60, 60), width=3)
    center_text(d, x + w / 2, y + h / 2, txt, font(h * 0.5, FK), RED if red else BLACK)


def arrow(d, cx, cy, size, direction, fill):
    s = size
    if direction == "up":
        pts = [(cx, cy - s), (cx + s, cy + s * 0.2), (cx + s * 0.4, cy + s * 0.2), (cx + s * 0.4, cy + s), (cx - s * 0.4, cy + s), (cx - s * 0.4, cy + s * 0.2), (cx - s, cy + s * 0.2)]
    elif direction == "left":
        pts = [(cx - s, cy), (cx - s * 0.2, cy - s), (cx - s * 0.2, cy - s * 0.4), (cx + s, cy - s * 0.4), (cx + s, cy + s * 0.4), (cx - s * 0.2, cy + s * 0.4), (cx - s * 0.2, cy + s)]
    else:  # right
        pts = [(cx + s, cy), (cx + s * 0.2, cy - s), (cx + s * 0.2, cy - s * 0.4), (cx - s, cy - s * 0.4), (cx - s, cy + s * 0.4), (cx + s * 0.2, cy + s * 0.4), (cx + s * 0.2, cy + s)]
    d.polygon(pts, fill=fill)


def ic_play(fill=GREEN):
    img, d = canvas()
    disc(d, 64, 64, 56, GOLD)
    disc(d, 64, 64, 48, fill)
    d.polygon([(50, 36), (50, 92), (94, 64)], fill=WHITE)
    return img


def ic_disabled():
    img, d = canvas()
    badge(d, (90, 90, 96, 255), (60, 60, 64, 255))
    d.rounded_rectangle([46, 60, 82, 92], radius=6, fill=(200, 200, 205, 255))
    d.arc([50, 34, 78, 70], 180, 360, fill=(200, 200, 205, 255), width=8)
    return img


def ic_book():
    img, d = canvas()
    d.polygon([(14, 34), (62, 26), (62, 104), (14, 112)], fill=(250, 240, 210, 255), outline=GOLD_D)
    d.polygon([(66, 26), (114, 34), (114, 112), (66, 104)], fill=(250, 240, 210, 255), outline=GOLD_D)
    d.polygon([(10, 32), (62, 22), (62, 26), (14, 34), (14, 112), (62, 104), (66, 104), (114, 112), (114, 34), (66, 26), (66, 22), (118, 32), (118, 118), (10, 118)], fill=(150, 30, 40, 255))
    for y in (46, 60, 74, 88):
        d.line([(22, y + 2), (54, y - 4)], fill=(120, 110, 90, 255), width=3)
        d.line([(74, y - 4), (106, y + 2)], fill=(120, 110, 90, 255), width=3)
    return img


def ic_clock():
    img, d = canvas()
    disc(d, 64, 64, 56, GOLD)
    disc(d, 64, 64, 46, (250, 248, 240, 255))
    for k in range(12):
        a = math.radians(k * 30)
        d.line([(64 + 38 * math.sin(a), 64 - 38 * math.cos(a)), (64 + 44 * math.sin(a), 64 - 44 * math.cos(a))], fill=BLACK, width=4)
    d.line([(64, 64), (64, 32)], fill=BLACK, width=7)
    d.line([(64, 64), (88, 76)], fill=RED, width=6)
    disc(d, 64, 64, 6, BLACK)
    return img


def ic_replay():
    img, d = canvas()
    disc(d, 64, 64, 56, GOLD)
    disc(d, 64, 64, 48, BLUE)
    d.arc([30, 30, 98, 98], 40, 330, fill=WHITE, width=12)
    d.polygon([(92, 28), (106, 64), (74, 52)], fill=WHITE)
    return img


def ic_cross(fill=RED):
    img, d = canvas()
    disc(d, 64, 64, 56, GOLD)
    disc(d, 64, 64, 48, fill)
    d.line([(44, 44), (84, 84)], fill=WHITE, width=14)
    d.line([(84, 44), (44, 84)], fill=WHITE, width=14)
    return img


def ic_check(fill=GREEN):
    img, d = canvas()
    disc(d, 64, 64, 56, GOLD)
    disc(d, 64, 64, 48, fill)
    d.line([(40, 66), (58, 84), (90, 46)], fill=WHITE, width=14, joint="curve")
    return img


def ic_lever():
    img, d = canvas()
    d.rounded_rectangle([30, 92, 98, 116], radius=8, fill=(90, 90, 100, 255))
    d.line([(64, 100), (88, 26)], fill=(210, 210, 220, 255), width=12)
    disc(d, 90, 24, 18, RED, outline=(120, 10, 10, 255), w=3)
    disc(d, 84, 18, 5, (255, 180, 180, 255))
    return img


def ic_star():
    img, d = canvas()
    pts = []
    for k in range(10):
        a = math.radians(k * 36)
        r = 58 if k % 2 == 0 else 26
        pts.append((64 + r * math.sin(a), 66 - r * math.cos(a)))
    d.polygon(pts, fill=GOLD, outline=GOLD_D)
    center_text(d, 64, 70, "777", font(28, FK), RED)
    return img


def ic_octagon():
    img, d = canvas()
    pts = [(64 + 56 * math.cos(math.radians(22.5 + k * 45)), 64 + 56 * math.sin(math.radians(22.5 + k * 45))) for k in range(8)]
    d.polygon(pts, fill=WHITE)
    pts = [(64 + 48 * math.cos(math.radians(22.5 + k * 45)), 64 + 48 * math.sin(math.radians(22.5 + k * 45))) for k in range(8)]
    d.polygon(pts, fill=RED)
    center_text(d, 64, 64, "STOP", font(30, FK), WHITE)
    return img


def ic_back():
    img, d = canvas()
    disc(d, 64, 64, 56, GOLD)
    disc(d, 64, 64, 48, (120, 20, 30, 255))
    arrow(d, 64, 64, 30, "left", WHITE)
    return img


def ic_door():
    img, d = canvas()
    d.rectangle([30, 12, 98, 118], fill=(110, 60, 25, 255), outline=GOLD_D, width=4)
    d.rectangle([40, 22, 88, 60], outline=(80, 40, 15, 255), width=4)
    d.rectangle([40, 70, 88, 108], outline=(80, 40, 15, 255), width=4)
    disc(d, 84, 66, 6, GOLD)
    return img


def ic_bubble():
    img, d = canvas()
    d.rounded_rectangle([10, 18, 118, 92], radius=24, fill=WHITE, outline=GOLD, width=5)
    d.polygon([(34, 88), (30, 116), (58, 90)], fill=WHITE)
    for x in (40, 64, 88):
        disc(d, x, 55, 8, (60, 60, 70, 255))
    return img


def ic_dealer():
    img, d = canvas()
    d.rectangle([34, 14, 94, 60], fill=BLACK)
    d.rectangle([20, 56, 108, 66], fill=BLACK)
    d.rectangle([34, 46, 94, 52], fill=RED)
    d.polygon([(36, 84), (64, 98), (36, 112)], fill=RED)
    d.polygon([(92, 84), (64, 98), (92, 112)], fill=RED)
    disc(d, 64, 98, 8, (150, 10, 20, 255))
    return img


def ic_deal():
    img, d = canvas()
    card_small(d, 16, 22, 50, 72, False, "A")
    card_small(d, 46, 32, 50, 72, True, "K")
    disc(d, 96, 96, 24, GREEN, outline=GOLD, w=4)
    d.polygon([(88, 84), (88, 108), (108, 96)], fill=WHITE)
    return img


def ic_hit():
    img, d = canvas()
    card_small(d, 26, 18, 62, 90, False, "")
    disc(d, 92, 92, 26, GREEN, outline=GOLD, w=4)
    d.line([(92, 76), (92, 108)], fill=WHITE, width=10)
    d.line([(76, 92), (108, 92)], fill=WHITE, width=10)
    return img


def ic_stand():
    img, d = canvas()
    disc(d, 64, 64, 56, GOLD)
    disc(d, 64, 64, 48, RED)
    # main levee
    d.rounded_rectangle([46, 52, 84, 96], radius=12, fill=WHITE)
    for i, x in enumerate((46, 56, 66, 76)):
        d.rounded_rectangle([x, 28 + (i % 2) * 4, x + 8, 64], radius=4, fill=WHITE)
    d.rounded_rectangle([32, 62, 48, 82], radius=6, fill=WHITE)
    return img


def ic_split():
    img, d = canvas()
    card_small(d, 10, 26, 50, 72, False, "8")
    card_small(d, 68, 26, 50, 72, True, "8")
    d.line([(64, 14), (64, 114)], fill=GOLD, width=6)
    return img


def ic_shield():
    img, d = canvas()
    d.polygon([(64, 10), (110, 26), (104, 78), (64, 118), (24, 78), (18, 26)], fill=GOLD)
    d.polygon([(64, 22), (98, 34), (94, 74), (64, 104), (34, 74), (30, 34)], fill=BLUE)
    center_text(d, 64, 62, "2:1", font(30, FK), WHITE)
    return img


def ic_target():
    img, d = canvas()
    for r, c in ((56, RED), (44, WHITE), (32, RED), (20, WHITE), (9, RED)):
        disc(d, 64, 64, r, c)
    return img


def ic_info():
    img, d = canvas()
    disc(d, 64, 64, 56, GOLD)
    disc(d, 64, 64, 48, BLUE)
    center_text(d, 64, 66, "i", font(70, FK), WHITE)
    return img


def ic_gold():
    img, d = canvas()
    for (x, y) in ((18, 70), (64, 70), (41, 42)):
        d.polygon([(x + 8, y), (x + 40, y), (x + 46, y + 26), (x + 2, y + 26)], fill=GOLD, outline=GOLD_D)
        d.line([(x + 12, y + 6), (x + 34, y + 6)], fill=(255, 240, 170, 255), width=3)
    return img


def ic_eye():
    img, d = canvas()
    d.ellipse([10, 34, 118, 94], fill=WHITE, outline=GOLD, width=5)
    disc(d, 64, 64, 22, (40, 140, 90, 255))
    disc(d, 64, 64, 10, BLACK)
    return img


def ic_gift(gray=False):
    img, d = canvas()
    c = (140, 140, 145, 255) if gray else RED
    r = (200, 200, 205, 255) if gray else GOLD
    d.rectangle([18, 52, 110, 116], fill=c)
    d.rectangle([12, 40, 116, 58], fill=c)
    d.rectangle([56, 40, 72, 116], fill=r)
    d.ellipse([34, 18, 64, 44], outline=r, width=8)
    d.ellipse([64, 18, 94, 44], outline=r, width=8)
    return img


def ic_shop():
    img, d = canvas()
    d.rounded_rectangle([20, 42, 108, 118], radius=10, fill=(150, 60, 200, 255), outline=GOLD, width=4)
    d.arc([40, 14, 88, 62], 180, 360, fill=GOLD, width=8)
    d.polygon([(64, 58), (84, 76), (64, 104), (44, 76)], fill=(120, 230, 255, 255), outline=WHITE)
    return img


def ic_crown():
    img, d = canvas()
    d.polygon([(14, 98), (14, 38), (40, 66), (64, 26), (88, 66), (114, 38), (114, 98)], fill=GOLD, outline=GOLD_D)
    d.rectangle([14, 98, 114, 112], fill=GOLD_D)
    for x, c in ((40, RED), (64, BLUE), (88, GREEN)):
        disc(d, x, 86, 7, c)
    return img


def ic_balls(n):
    img, d = canvas()
    pos = [(64, 64)] if n == 1 else [(64, 30), (40, 58), (88, 58), (52, 92), (80, 92)]
    r = 34 if n == 1 else 18
    for (x, y) in pos:
        disc(d, x, y, r, (250, 220, 90, 255), outline=GOLD_D, w=3)
        disc(d, x - r * 0.35, y - r * 0.35, r * 0.3, (255, 250, 220, 255))
    return img


def ic_seat():
    img, d = canvas()
    d.rounded_rectangle([30, 14, 98, 70], radius=14, fill=(130, 30, 40, 255), outline=GOLD, width=4)
    d.rounded_rectangle([22, 66, 106, 88], radius=8, fill=(150, 40, 50, 255), outline=GOLD, width=4)
    d.line([(34, 88), (30, 118)], fill=GOLD_D, width=6)
    d.line([(94, 88), (98, 118)], fill=GOLD_D, width=6)
    return img


def ic_arrow_badge(direction, fill=GOLD):
    img, d = canvas()
    arrow(d, 64, 64, 46, direction, fill)
    return img


def ic_fold():
    img, d = canvas()
    card_small(d, 20, 30, 50, 72, False, "")
    card_small(d, 54, 22, 50, 72, False, "")
    d.line([(16, 112), (112, 16)], fill=RED, width=12)
    return img


def ic_knock():
    img, d = canvas()
    disc(d, 64, 64, 56, GOLD)
    disc(d, 64, 64, 48, GREEN)
    center_text(d, 64, 66, "OK", font(40, FK), WHITE)
    return img


def ic_call():
    img, d = canvas()
    disc(d, 64, 64, 56, GOLD)
    disc(d, 64, 64, 48, GREEN)
    d.rectangle([38, 48, 90, 58], fill=WHITE)
    d.rectangle([38, 70, 90, 80], fill=WHITE)
    return img


def ic_tag():
    img, d = canvas()
    d.polygon([(10, 40), (90, 40), (118, 64), (90, 88), (10, 88)], fill=(240, 230, 200, 255), outline=GOLD_D)
    disc(d, 92, 64, 6, GOLD_D)
    for y in (54, 66, 78):
        d.line([(22, y), (70, y)], fill=(120, 110, 90, 255), width=4)
    return img


def ic_slot_machine():
    img, d = canvas()
    d.rounded_rectangle([20, 10, 100, 118], radius=10, fill=(180, 20, 30, 255), outline=GOLD, width=4)
    d.rectangle([28, 40, 92, 76], fill=WHITE, outline=BLACK, width=3)
    for i, x in enumerate((38, 60, 82)):
        center_text(d, x, 58, "7", font(30, FK), RED)
    d.rectangle([28, 16, 92, 34], fill=GOLD)
    center_text(d, 60, 25, "777", font(16, FK), RED)
    d.line([(104, 70), (116, 30)], fill=(200, 200, 210, 255), width=5)
    disc(d, 116, 28, 8, RED)
    return img


def ic_vp():
    img, d = canvas()
    d.rounded_rectangle([10, 14, 118, 114], radius=10, fill=(20, 40, 110, 255), outline=GOLD, width=4)
    for i in range(5):
        x = 18 + i * 20
        d.rounded_rectangle([x, 42, x + 17, 68], radius=3, fill=WHITE)
        center_text(d, x + 8.5, 55, "AKQJT"[i], font(14, FK), RED if i % 2 else BLACK)
    center_text(d, 64, 28, "POKER", font(18, FK), GOLD)
    return img


def ic_poker():
    img, d = canvas()
    card_small(d, 14, 14, 52, 74, False, "A")
    card_small(d, 40, 22, 52, 74, True, "A")
    for i, c in enumerate((RED, BLUE, BLACK)):
        y = 110 - i * 10
        d.ellipse([66, y - 12, 118, y + 8], fill=c, outline=WHITE, width=3)
    return img


def ic_cards21():
    img, d = canvas()
    card_small(d, 12, 20, 56, 82, False, "A")
    card_small(d, 56, 28, 56, 82, True, "K")
    disc(d, 92, 30, 22, GOLD, outline=GOLD_D, w=3)
    center_text(d, 92, 31, "21", font(22, FK), BLACK)
    return img


def from_texture(name, crop=None):
    im = Image.open(os.path.join(TEX, name + ".png")).convert("RGBA")
    if crop:
        im = im.crop(crop)
    im.thumbnail((S, S), Image.LANCZOS)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.alpha_composite(im, ((S - im.width) // 2, (S - im.height) // 2))
    return out


# --- symboles des machines a sous (aussi sur les rouleaux 3D)

def sym_cherry():
    img, d = canvas()
    d.line([(44, 80), (66, 22)], fill=(40, 120, 40, 255), width=7)
    d.line([(86, 84), (66, 22)], fill=(40, 120, 40, 255), width=7)
    d.ellipse([60, 14, 96, 30], fill=(60, 170, 60, 255))
    for cx, cy in ((40, 88), (88, 92)):
        disc(d, cx, cy, 24, (200, 15, 30, 255), outline=(120, 0, 10, 255), w=3)
        disc(d, cx - 8, cy - 9, 7, (255, 150, 160, 255))
    return img


def sym_melon():
    img, d = canvas()
    d.pieslice([8, -20, 120, 108], 20, 160, fill=(40, 140, 40, 255))
    d.pieslice([16, -14, 112, 98], 20, 160, fill=(240, 240, 200, 255))
    d.pieslice([22, -8, 106, 90], 20, 160, fill=(230, 50, 60, 255))
    for x, y in ((44, 60), (64, 66), (84, 60), (54, 48), (74, 48)):
        d.ellipse([x - 3, y - 5, x + 3, y + 5], fill=BLACK)
    return img


def sym_bell():
    img, d = canvas()
    d.polygon([(64, 14), (88, 30), (96, 80), (110, 96), (18, 96), (32, 80), (40, 30)], fill=GOLD, outline=GOLD_D)
    disc(d, 64, 104, 12, GOLD_D)
    d.line([(52, 36), (46, 80)], fill=(255, 245, 190, 255), width=6)
    return img


def sym_bar():
    img, d = canvas()
    d.rounded_rectangle([8, 36, 120, 92], radius=10, fill=BLACK, outline=GOLD, width=5)
    center_text(d, 64, 64, "BAR", font(40, FK), GOLD)
    return img


def sym_diamond():
    img, d = canvas()
    d.polygon([(30, 30), (98, 30), (118, 52), (64, 116), (10, 52)], fill=(90, 220, 255, 255), outline=(20, 110, 170, 255))
    d.polygon([(30, 30), (50, 52), (10, 52)], fill=(170, 240, 255, 255))
    d.polygon([(98, 30), (78, 52), (118, 52)], fill=(60, 180, 230, 255))
    d.line([(10, 52), (118, 52)], fill=(20, 110, 170, 255), width=3)
    d.polygon([(50, 52), (78, 52), (64, 116)], fill=(130, 230, 255, 255))
    return img


def sym_seven():
    img, d = canvas()
    center_text(d, 64, 66, "7", font(118, FK), (220, 20, 30, 255), outline=GOLD, ow=6)
    return img


def make_icons():
    os.makedirs(ICON_TEX, exist_ok=True)
    icons = {}
    icons["vide"] = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    chips = {"10": ((235, 235, 235, 255), (40, 40, 40, 255)), "50": (RED, WHITE), "100": (BLUE, WHITE), "500": (GREEN, WHITE),
             "1k": ((30, 30, 34, 255), GOLD), "5k": ((140, 50, 190, 255), WHITE), "10k": (GOLD, (120, 20, 20, 255))}
    for lab, (c, e) in chips.items():
        fg = BLACK if lab == "10" else WHITE
        icons["jeton_" + lab] = chip(c, lab.upper(), e, fg)
    icons["jetons"] = chip_stack([RED, BLUE, GREEN, (30, 30, 34, 255), GOLD])
    icons["mise"] = chip_stack([RED, BLUE])
    icons["regles"] = ic_book()
    icons["lancer"] = ic_play()
    icons["horloge"] = ic_clock()
    icons["rejouer"] = ic_replay()
    icons["effacer"] = ic_cross((160, 120, 120, 255))
    icons["desactive"] = ic_disabled()
    icons["levier"] = ic_lever()
    icons["jackpot"] = ic_star()
    icons["stop"] = ic_octagon()
    icons["retour"] = ic_back()
    icons["fermer"] = ic_cross()
    icons["porte"] = ic_door()
    icons["message"] = ic_bubble()
    icons["croupier"] = ic_dealer()
    icons["distribuer"] = ic_deal()
    icons["vert"] = ic_check()
    icons["rouge"] = ic_cross()
    icons["relance"] = ic_arrow_badge("up", GREEN)
    icons["or"] = ic_gold()
    icons["separer"] = ic_split()
    icons["assurance"] = ic_shield()
    icons["cible"] = ic_target()
    icons["info"] = ic_info()
    icons["oeil"] = ic_eye()
    icons["cadeau"] = ic_gift()
    icons["cadeau_pris"] = ic_gift(True)
    icons["boutique"] = ic_shop()
    icons["couronne"] = ic_crown()
    icons["bille"] = ic_balls(1)
    icons["billes"] = ic_balls(5)
    icons["siege"] = ic_seat()
    icons["etiquette"] = ic_tag()
    icons["tapis"] = text_badge("TAPIS", (30, 30, 34, 255), GOLD)
    icons["fleche_haut"] = ic_arrow_badge("up")
    icons["ligne"] = ic_arrow_badge("right", RED)
    icons["ligne_gauche"] = ic_arrow_badge("left", RED)
    icons["garder_non"] = text_badge("?", (90, 90, 96, 255))
    icons["garder_oui"] = text_badge("OK", GREEN)
    icons["bj_tirer"] = ic_hit()
    icons["bj_rester"] = ic_stand()
    icons["bj_doubler"] = text_badge("x2", (200, 140, 20, 255))
    icons["bj_separer"] = ic_split()
    icons["bj_assurance"] = ic_shield()
    icons["po_coucher"] = ic_fold()
    icons["po_parole"] = ic_knock()
    icons["po_suivre"] = ic_call()
    icons["po_relance"] = ic_arrow_badge("up", GREEN)
    icons["pack_1"] = chip_stack([RED])
    icons["pack_2"] = chip_stack([RED, BLUE])
    icons["pack_3"] = chip_stack([RED, BLUE, GREEN])
    icons["pack_4"] = chip_stack([RED, BLUE, GREEN, (30, 30, 34, 255)])
    icons["pack_5"] = chip_stack([RED, BLUE, GREEN, (30, 30, 34, 255), GOLD])
    icons["sym_cerise"] = sym_cherry()
    icons["sym_pasteque"] = sym_melon()
    icons["sym_cloche"] = sym_bell()
    icons["sym_bar"] = sym_bar()
    icons["sym_diamant"] = sym_diamond()
    icons["sym_sept"] = sym_seven()
    # roulette
    for n in range(37):
        col = (0, 140, 66, 255) if n == 0 else RED if n in REDS else BLACK
        icons["num_%d" % n] = text_badge(str(n), col, WHITE, GOLD, 64)
    rl = {"rl_red": ("ROUGE", RED), "rl_black": ("NOIR", BLACK), "rl_even": ("PAIR", (20, 100, 50, 255)), "rl_odd": ("IMPAIR", (20, 100, 50, 255)),
          "rl_low": ("1-18", (20, 100, 50, 255)), "rl_high": ("19-36", (20, 100, 50, 255)), "rl_d1": ("1-12", (20, 80, 60, 255)),
          "rl_d2": ("13-24", (20, 80, 60, 255)), "rl_d3": ("25-36", (20, 80, 60, 255)), "rl_c1": ("COL 1", (30, 60, 90, 255)),
          "rl_c2": ("COL 2", (30, 60, 90, 255)), "rl_c3": ("COL 3", (30, 60, 90, 255))}
    for k, (t, c) in rl.items():
        icons[k] = text_badge(t, c, WHITE, GOLD, 44)
    # roue de la fortune
    tiers = [("x0", (40, 40, 46, 255)), ("x0,5", (120, 80, 50, 255)), ("x1", (150, 150, 158, 255)), ("x1,5", BLUE),
             ("x2", GREEN), ("x3", (150, 60, 200, 255)), ("x5", (240, 120, 30, 255)), ("x10", GOLD)]
    for i, (t, c) in enumerate(tiers):
        icons["roue_%d" % i] = text_badge(t, c, WHITE, GOLD if i else (90, 90, 96, 255), 54)
    # plinko
    pm = [5.6, 2.1, 1.1, 0.9, 0.5, 0.9, 1.1, 2.1, 5.6]
    pc = [(210, 30, 70, 255), (230, 60, 50, 255), (245, 110, 40, 255), (250, 160, 40, 255), (250, 200, 40, 255)]
    for j, m in enumerate(pm):
        icons["pl_%d" % j] = text_badge(("x%g" % m).replace(".", ","), pc[abs(j - 4)], (30, 10, 20, 255), WHITE, 48)
    # jeux du menu principal
    icons["jeu_slots"] = ic_slot_machine()
    icons["jeu_blackjack"] = ic_cards21()
    icons["jeu_roulette"] = from_texture("roulette")
    icons["jeu_videopoker"] = ic_vp()
    icons["jeu_poker"] = ic_poker()
    icons["jeu_roue"] = from_texture("fortune")
    icons["jeu_plinko"] = from_texture("plinko", (40, 0, 472, 512))
    for name, im in icons.items():
        if im.size != (16, 16):
            im = im.resize((32, 32), Image.LANCZOS)
        im.save(os.path.join(ICON_TEX, name + ".png"))
        save_json(os.path.join(ICON_MOD, name + ".json"), {"parent": "minecraft:item/generated",
                                                           "textures": {"layer0": "eemicraft:item/casino/icone/" + name}})
        save_json(os.path.join(ICON_ITM, name + ".json"), {"model": {"type": "minecraft:model", "model": "eemicraft:item/casino/icone/" + name}})
    return list(icons)


# =============================================================================== fonds des menus

# ordre = index du caractere (U+E101 + i), identique a Gui.java
BACKGROUNDS = ["main", "slots", "blackjack", "roulette", "roulette-plein", "videopoker", "fortune", "plinko",
               "cashier", "shop", "top", "pokerlist", "poker-buyin", "poker"]

THEMES = {"red": ((96, 14, 26), (46, 6, 14)), "felt": ((18, 104, 54), (8, 58, 30)), "blue": ((22, 40, 100), (8, 16, 52)),
          "purple": ((60, 22, 92), (26, 8, 46)), "wood": ((92, 52, 26), (48, 26, 12))}

R = lambda a, b: list(range(a, b + 1))
SPECS = {
    "main": (5, "red", [4, 10, 12, 14, 16, 20, 22, 24, 28, 30, 32, 34, 40], []),
    "slots": (5, "red", [9, 17, 27, 29, 31, 33, 35] + R(37, 43), [("screen", 1, 0, 7, 2), ("reels", 2, 0, 2, 2), ("reels", 4, 0, 4, 2), ("reels", 6, 0, 6, 2),
                                                                  ("strip", 1, 4, 7, 4)]),
    "blackjack": (6, "felt", [0] + R(2, 8) + [13, 18] + R(20, 26) + [27] + R(29, 35) + [36] + R(37, 44) + [45, 46, 47, 48, 50, 51, 52, 53],
                  [("zone", 0, 0, 8, 0), ("strip", 0, 5, 8, 5)]),
    "roulette": (6, "felt", [9, 13] + R(18, 29) + [31, 33, 35] + R(37, 43) + [45, 47, 49, 51], [("wood", 0, 0, 8, 0), ("strip", 1, 4, 7, 4)]),
    "roulette-plein": (6, "felt", R(0, 36) + R(45, 53), [("strip", 1, 5, 7, 5)]),
    "videopoker": (5, "blue", [4] + R(20, 24) + [29, 31, 36] + R(37, 43), [("screen", 2, 1, 6, 1), ("strip", 1, 4, 7, 4)]),
    "fortune": (6, "red", [9, 13, 17] + R(19, 26) + R(37, 43) + [45, 49], [("wood", 0, 0, 8, 0), ("zone", 1, 2, 8, 2), ("strip", 1, 4, 7, 4)]),
    "plinko": (6, "purple", [4, 9] + R(37, 43) + [45, 48, 50, 53], [("zone", 0, 2, 8, 2), ("strip", 1, 4, 7, 4)]),
    "cashier": (4, "red", [4] + R(11, 15) + [22, 31], [("zone", 2, 1, 6, 1)]),
    "shop": (5, "purple", [4] + R(19, 25) + R(28, 34) + [40], [("zone", 1, 2, 7, 3)]),
    "top": (4, "red", [4, 12, 14] + R(19, 25) + [31], [("zone", 1, 2, 7, 2)]),
    "pokerlist": (3, "felt", [11, 13, 15, 22], []),
    "poker-buyin": (3, "felt", [4, 11, 13, 15, 22], []),
    "poker": (6, "felt", [1, 2, 3, 5, 6, 7, 10, 11, 12, 14, 15, 16, 4, 13] + R(20, 24) + [30, 31, 32, 36, 40] + R(45, 53),
              [("zone", 2, 2, 6, 2), ("strip", 0, 5, 8, 5)]),
}


def slot_xy(k):
    return 7 + 18 * (k % 9), 17 + 18 * (k // 9)


def make_background(kind, idx):
    rows, theme, used, panels = SPECS[kind]
    H = rows * 18 + 114
    c1, c2 = THEMES[theme]
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # fond degrade
    for y in range(H):
        t = y / max(1, H - 1)
        col = tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3)) + (255,)
        d.line([(0, y), (175, y)], fill=col)
    # motif discret (losanges)
    pat = Image.new("RGBA", (176, H), (0, 0, 0, 0))
    pd = ImageDraw.Draw(pat)
    for y in range(0, H, 12):
        for x in range((y // 12) % 2 * 6, 176, 12):
            pd.polygon([(x, y - 3), (x + 3, y), (x, y + 3), (x - 3, y)], fill=(255, 255, 255, 10))
    img.alpha_composite(pat)
    # cadre dore
    d.rounded_rectangle([0, 0, 175, H - 1], radius=5, outline=(70, 46, 8, 255), width=1)
    d.rounded_rectangle([1, 1, 174, H - 2], radius=4, outline=GOLD, width=2)
    d.rounded_rectangle([3, 3, 172, H - 4], radius=3, outline=(110, 74, 14, 255), width=1)
    # bandeau du titre
    d.rectangle([4, 4, 171, 15], fill=(0, 0, 0, 70))
    d.line([(4, 15), (171, 15)], fill=(200, 150, 40, 255))
    # panneaux
    for (style, ca, ra, cb, rb) in panels:
        x0, y0 = slot_xy(ra * 9 + ca)
        x1, y1 = slot_xy(rb * 9 + cb)
        x1 += 17
        y1 += 17
        if style == "screen":
            d.rounded_rectangle([x0 - 2, y0 - 2, x1 + 2, y1 + 2], radius=3, fill=(10, 10, 16, 255), outline=GOLD, width=2)
        elif style == "reels":
            d.rectangle([x0, y0, x1, y1], fill=(245, 240, 226, 255))
            d.line([(x0, y0 + 26), (x1, y0 + 26)], fill=(200, 30, 40, 150))
        elif style == "wood":
            d.rounded_rectangle([x0 - 1, y0 - 1, x1 + 1, y1 + 1], radius=2, fill=(70, 38, 16, 255), outline=GOLD, width=1)
        elif style == "zone":
            d.rounded_rectangle([x0 - 2, y0 - 2, x1 + 2, y1 + 2], radius=4, fill=(0, 0, 0, 60), outline=(230, 190, 90, 200), width=1)
        elif style == "strip":
            d.rounded_rectangle([x0 - 2, y0 - 2, x1 + 2, y1 + 2], radius=4, fill=(0, 0, 0, 90), outline=(200, 150, 40, 255), width=1)
    # cases utilisees
    for k in used:
        x, y = slot_xy(k)
        d.rectangle([x, y, x + 17, y + 17], fill=(0, 0, 0, 80))
        d.rectangle([x, y, x + 17, y + 17], outline=(170, 125, 35, 230))
        d.line([(x + 1, y + 1), (x + 16, y + 1)], fill=(0, 0, 0, 90))
    # inventaire du joueur
    inv_label = H - 94
    d.line([(6, inv_label - 3), (169, inv_label - 3)], fill=(200, 150, 40, 120))
    inv_y = (rows - 4) * 18 + 102
    for r in range(4):
        y = inv_y + r * 18 + (4 if r == 3 else 0)
        for c in range(9):
            x = 7 + 18 * c
            d.rectangle([x, y, x + 17, y + 17], fill=(0, 0, 0, 110), outline=(120, 90, 30, 220))
    os.makedirs(FONT_TEX, exist_ok=True)
    img.save(os.path.join(FONT_TEX, "bg_%s.png" % kind.replace("-", "_")))
    return {"type": "bitmap", "file": "eemicraft:font/casino/bg_%s.png" % kind.replace("-", "_"), "ascent": 13, "height": 256, "chars": [chr(0xE101 + idx)]}


def make_font():
    providers = [{"type": "space", "advances": {"\ue001": -8, "\ue002": -169}}]
    for i, k in enumerate(BACKGROUNDS):
        providers.append(make_background(k, i))
    save_json(os.path.join(FONT_DEF, "casino.json"), {"providers": providers})


# =============================================================================== machines 3D

def tex(name, w, h, draw):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw(img, d, w, h)
    img.save(os.path.join(TEX, "machine", name + ".png"))


def vgrad(d, w, h, c1, c2):
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3)) + (255,))


def make_machine_textures():
    os.makedirs(os.path.join(TEX, "machine"), exist_ok=True)
    tex("or", 16, 16, lambda i, d, w, h: vgrad(d, w, h, (250, 210, 90), (170, 120, 30)))
    tex("chrome", 16, 16, lambda i, d, w, h: vgrad(d, w, h, (235, 235, 245), (130, 130, 145)))
    tex("sombre", 16, 16, lambda i, d, w, h: vgrad(d, w, h, (40, 34, 38), (16, 12, 14)))
    tex("rouge", 16, 16, lambda i, d, w, h: vgrad(d, w, h, (230, 30, 40), (130, 8, 16)))

    def body(color1, color2):
        def f(img, d, w, h):
            vgrad(d, w, h, color1, color2)
            d.rectangle([0, 0, 3, h], fill=GOLD)
            d.rectangle([w - 4, 0, w, h], fill=GOLD)
            d.line([(6, 0), (6, h)], fill=(255, 255, 255, 50), width=2)
        return f
    tex("corps_rouge", 64, 64, body((190, 22, 34), (110, 8, 18)))
    tex("corps_bleu", 64, 64, body((40, 70, 170), (16, 28, 90)))

    def screen(img, d, w, h):
        d.rectangle([0, 0, w, h], fill=GOLD)
        d.rectangle([4, 4, w - 5, h - 5], fill=(14, 12, 16, 255))
        for cx in (0.229, 0.5, 0.771):
            x0 = int((cx - 0.115) * w)
            x1 = int((cx + 0.115) * w)
            d.rectangle([x0, int(0.15 * h), x1, int(0.85 * h)], fill=(245, 240, 226, 255), outline=GOLD_D, width=2)
        d.line([(6, h // 2), (w - 7, h // 2)], fill=(220, 30, 40, 200), width=2)
    tex("ecran_sous", 128, 96, screen)

    def marquee(img, d, w, h):
        vgrad(d, w, h, (60, 8, 16), (24, 2, 8))
        d.rectangle([0, 0, w - 1, h - 1], outline=GOLD, width=5)
        for k in range(0, w, 12):
            disc(d, k + 6, 6, 3, (255, 240, 170, 255))
            disc(d, k + 6, h - 7, 3, (255, 240, 170, 255))
        center_text(d, w / 2, h * 0.38, "JACKPOT", font(h * 0.26, FK), GOLD, outline=(90, 20, 0, 255), ow=2)
        center_text(d, w / 2, h * 0.72, "7 7 7", font(h * 0.3, FK), (230, 30, 40, 255), outline=WHITE, ow=2)
    tex("enseigne_sous", 128, 64, marquee)

    def panel(img, d, w, h):
        vgrad(d, w, h, (50, 44, 48), (24, 20, 24))
        for i, c in enumerate(((230, 40, 50), (240, 190, 40), (60, 190, 80), (60, 120, 230))):
            x = 10 + i * 28
            d.rounded_rectangle([x, h // 2 - 8, x + 20, h // 2 + 8], radius=4, fill=c + (255,), outline=WHITE)
    tex("tableau_sous", 128, 32, panel)

    def vp_screen(img, d, w, h):
        d.rectangle([0, 0, w, h], fill=GOLD)
        d.rectangle([4, 4, w - 5, h - 5], fill=(8, 20, 70, 255))
        center_text(d, w / 2, h * 0.14, "JACKS OR BETTER", font(h * 0.1, FK), GOLD)
    tex("ecran_vp", 128, 96, vp_screen)

    def vp_marquee(img, d, w, h):
        vgrad(d, w, h, (20, 40, 120), (6, 12, 50))
        d.rectangle([0, 0, w - 1, h - 1], outline=GOLD, width=5)
        center_text(d, w / 2, h * 0.36, "VIDEO", font(h * 0.28, FK), WHITE, outline=(0, 0, 60, 255), ow=2)
        center_text(d, w / 2, h * 0.72, "POKER", font(h * 0.3, FK), GOLD, outline=(60, 30, 0, 255), ow=2)
    tex("enseigne_vp", 128, 64, vp_marquee)

    def vp_panel(img, d, w, h):
        vgrad(d, w, h, (40, 40, 60), (18, 18, 30))
        for i in range(5):
            x = 8 + i * 24
            d.rounded_rectangle([x, h // 2 - 7, x + 18, h // 2 + 7], radius=3, fill=(250, 210, 60, 255), outline=WHITE)
        d.rounded_rectangle([w - 10, 4, w - 2, h - 4], radius=2, fill=GREEN)
    tex("tableau_vp", 128, 32, vp_panel)


def faces(spec, uv=(0, 0, 16, 16)):
    out = {}
    for f, t in spec.items():
        out[f] = {"uv": list(uv), "texture": "#" + t}
    return out


def all6(t, front=None, top=None):
    f = {"north": t, "south": front or t, "east": t, "west": t, "up": top or t, "down": t}
    return f


def make_machine_models():
    tx = lambda n: "eemicraft:item/casino/machine/" + n
    # machine a sous : face avant = sud (+z), reperes en unites de modele (centre 8,8,8)
    slot = {
        "credit": "EEMICRAFT - EemiCasino",
        "textures": {"or": tx("or"), "chrome": tx("chrome"), "sombre": tx("sombre"), "corps": tx("corps_rouge"),
                     "ecran": tx("ecran_sous"), "enseigne": tx("enseigne_sous"), "tableau": tx("tableau_sous"), "particle": tx("corps_rouge")},
        "elements": [
            {"from": [0, 0, 1], "to": [16, 5, 15], "faces": faces(all6("sombre", front="or", top="sombre"))},
            {"from": [1, 5, 2], "to": [15, 23, 14], "faces": faces(all6("corps", top="sombre"))},
            {"from": [2, 11, 14], "to": [14, 20, 14.6], "faces": faces({"south": "ecran", "east": "or", "west": "or", "up": "or", "down": "or"})},
            {"from": [1, 5, 14], "to": [15, 8, 17], "faces": faces({"up": "tableau", "south": "or", "east": "or", "west": "or", "down": "sombre"})},
            {"from": [0, 23, 1.5], "to": [16, 31, 15], "faces": faces({"south": "enseigne", "north": "corps", "east": "corps", "west": "corps", "up": "or", "down": "or"})},
            {"from": [15, 13, 7], "to": [16.6, 16, 10], "faces": faces(all6("chrome"))},
        ],
    }
    save_json(os.path.join(MOD, "machine_sous.json"), slot)
    lever = {
        "credit": "EEMICRAFT - EemiCasino",
        "textures": {"chrome": tx("chrome"), "rouge": tx("rouge"), "particle": tx("chrome")},
        "elements": [
            {"from": [7.5, 8, 7.5], "to": [8.5, 19, 8.5], "faces": faces(all6("chrome"))},
            {"from": [6.3, 18.5, 6.3], "to": [9.7, 21.9, 9.7], "faces": faces(all6("rouge"))},
        ],
    }
    save_json(os.path.join(MOD, "machine_levier.json"), lever)
    vp = {
        "credit": "EEMICRAFT - EemiCasino",
        "textures": {"or": tx("or"), "sombre": tx("sombre"), "corps": tx("corps_bleu"), "ecran": tx("ecran_vp"),
                     "enseigne": tx("enseigne_vp"), "tableau": tx("tableau_vp"), "particle": tx("corps_bleu")},
        "elements": [
            {"from": [1, 0, 2], "to": [15, 4, 14], "faces": faces(all6("sombre", front="or"))},
            {"from": [1, 4, 2], "to": [15, 24, 13], "faces": faces(all6("corps", top="sombre"))},
            {"from": [2, 12, 13], "to": [14, 21, 13.6], "faces": faces({"south": "ecran", "east": "or", "west": "or", "up": "or", "down": "or"})},
            {"from": [1, 4, 13], "to": [15, 9, 16], "faces": faces({"up": "tableau", "south": "or", "east": "or", "west": "or", "down": "sombre"})},
            {"from": [0, 24, 1.5], "to": [16, 30, 14], "faces": faces({"south": "enseigne", "north": "corps", "east": "corps", "west": "corps", "up": "or", "down": "or"})},
        ],
    }
    save_json(os.path.join(MOD, "video_poker.json"), vp)
    for n in ("machine_sous", "machine_levier", "video_poker"):
        save_json(os.path.join(ITM, n + ".json"), {"model": {"type": "minecraft:model", "model": "eemicraft:item/casino/" + n}})


def main():
    icons = make_icons()
    make_font()
    make_machine_textures()
    make_machine_models()
    print("%d icones, %d fonds de menus, 3 modeles de machines." % (len(icons), len(BACKGROUNDS)))


if __name__ == "__main__":
    main()
