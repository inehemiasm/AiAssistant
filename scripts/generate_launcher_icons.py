"""Generate all Android mipmap launcher WebP icons and a 512x512 Play Store PNG for the C-Robot identity."""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
RES_DIR = ROOT / "app" / "src" / "main" / "res"
DIST_DIR = ROOT / "distribution"

MIPMAP_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def lerp_color(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a + (b - a) * t)) for a, b in zip(c1, c2))


def multi_gradient(stops, t):
    t = max(0.0, min(1.0, t))
    for i in range(len(stops) - 1):
        p0, col0 = stops[i]
        p1, col1 = stops[i + 1]
        if p0 <= t <= p1:
            local_t = (t - p0) / max(1e-6, (p1 - p0))
            return lerp_color(col0, col1, local_t)
    return stops[-1][1]


def render_master_canvas(size=1024):
    """Render the 108x108 coordinate space onto a high-res master canvas."""
    s = size / 108.0
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    bg_stops = [
        (0.0, (6, 11, 30)),
        (0.52, (12, 31, 79)),
        (1.0, (0, 92, 138)),
    ]
    cx_glow, cy_glow = 58.0 * s, 52.0 * s
    r_glow = 52.0 * s

    for y in range(size):
        for x in range(size):
            t_diag = (x + y) / (2.0 * (size - 1))
            r, g, b = multi_gradient(bg_stops, t_diag)
            dist = math.hypot(x - cx_glow, y - cy_glow)
            if dist < r_glow:
                u = 1.0 - (dist / r_glow)
                glow_strength = (u * u) * 0.25
                g = min(255, int(g + 229 * glow_strength))
                b = min(255, int(b + 255 * glow_strength))
            px[x, y] = (r, g, b, 255)

    # 1. Soft cyan-indigo ambient aura behind the C-Robot
    aura = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    aura_draw = ImageDraw.Draw(aura, "RGBA")
    aura_draw.ellipse(
        [int(22 * s), int(22 * s), int(86 * s), int(86 * s)],
        fill=(0, 187, 249, 48),
    )
    aura = aura.filter(ImageFilter.GaussianBlur(radius=int(8 * s)))
    img = Image.alpha_composite(img, aura)
    draw = ImageDraw.Draw(img, "RGBA")

    # 2. Top robotic antenna stem & glowing orb
    stem_w = max(2, int(3.2 * s))
    draw.line(
        [(54 * s, 19 * s), (54 * s, 28 * s)],
        fill=(0, 187, 249, 255),
        width=stem_w,
    )
    orb_r = 3.5 * s
    draw.ellipse(
        [54 * s - orb_r, 17.5 * s - orb_r, 54 * s + orb_r, 17.5 * s + orb_r],
        fill=(0, 245, 212, 255),
    )
    inner_orb_r = 1.5 * s
    draw.ellipse(
        [54 * s - inner_orb_r, 17.5 * s - inner_orb_r, 54 * s + inner_orb_r, 17.5 * s + inner_orb_r],
        fill=(255, 255, 255, 240),
    )

    # 3. Left robotic sensor/ear module
    draw.rounded_rectangle(
        [22.5 * s, 43 * s, 29.5 * s, 65 * s],
        radius=3.5 * s,
        fill=(0, 187, 249, 255),
    )

    # 4. Recessed dark obsidian robot visor screen (tucked cleanly inside the C chassis)
    draw.rounded_rectangle(
        [35 * s, 35 * s, 75 * s, 73 * s],
        radius=15 * s,
        fill=(8, 13, 31, 255),
        outline=(0, 229, 255, 70),
        width=max(1, int(1.0 * s)),
    )

    # 5. Bold geometric "C" Robot Helmet Chassis with Cyan -> Sky -> Indigo gradient
    c_stops = [
        (0.0, (0, 245, 212)),
        (0.5, (0, 187, 249)),
        (1.0, (67, 97, 238)),
    ]
    cx, cy = 54.0 * s, 54.0 * s
    radius = 24.5 * s
    half_stroke = (11.5 * 0.5) * s
    steps = 900
    start_deg = 45.0
    sweep_deg = 270.0
    for i in range(steps + 1):
        u = i / float(steps)
        deg = start_deg + u * sweep_deg
        rad = math.radians(deg)
        x = cx + radius * math.cos(rad)
        y = cy + radius * math.sin(rad)
        grad_t = ((x - 30 * s) + (y - 28 * s)) / ((46 + 52) * s)
        col = multi_gradient(c_stops, grad_t)
        draw.ellipse(
            [x - half_stroke, y - half_stroke, x + half_stroke, y + half_stroke],
            fill=(*col, 255),
        )

    # 6. Glowing optical pill eyes inside the visor with smooth vertical gradient
    eye_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    eye_px = eye_layer.load()
    eye_y0 = int(round(46.5 * s))
    eye_y1 = int(round(61.5 * s))
    for eye_cx in (52.5, 65.5):
        eye_mask = Image.new("L", (size, size), 0)
        m_draw = ImageDraw.Draw(eye_mask)
        x0 = (eye_cx - 4.0) * s
        x1 = (eye_cx + 4.0) * s
        m_draw.rounded_rectangle([x0, 46.5 * s, x1, 61.5 * s], radius=4.0 * s, fill=255)
        m_px = eye_mask.load()
        for y in range(eye_y0, eye_y1 + 1):
            t_y = (y - eye_y0) / max(1.0, float(eye_y1 - eye_y0))
            col = lerp_color((0, 245, 212), (0, 187, 249), t_y)
            for x in range(int(x0) - 1, int(x1) + 2):
                a = m_px[x, y]
                if a > 0:
                    eye_px[x, y] = (col[0], col[1], col[2], a)

    img = Image.alpha_composite(img, eye_layer)
    draw = ImageDraw.Draw(img, "RGBA")

    for eye_cx in (52.5, 65.5):
        sp_r = 1.3 * s
        sp_x = (eye_cx - 1.2) * s
        sp_y = 49.8 * s
        draw.ellipse(
            [sp_x - sp_r, sp_y - sp_r, sp_x + sp_r, sp_y + sp_r],
            fill=(255, 255, 255, 225),
        )

    return img


def make_masked_icon(master, out_size, circle=False):
    resized = master.resize((out_size, out_size), Image.Resampling.LANCZOS)
    mask = Image.new("L", (out_size * 4, out_size * 4), 0)
    mdraw = ImageDraw.Draw(mask)
    pad = int(out_size * 4 * 0.04)
    box = [pad, pad, out_size * 4 - pad, out_size * 4 - pad]
    if circle:
        mdraw.ellipse(box, fill=255)
    else:
        mdraw.rounded_rectangle(box, radius=int(out_size * 4 * 0.22), fill=255)
    mask = mask.resize((out_size, out_size), Image.Resampling.LANCZOS)
    out = Image.new("RGBA", (out_size, out_size), (0, 0, 0, 0))
    out.paste(resized, (0, 0), mask)
    return out


def main():
    master = render_master_canvas(1024)
    DIST_DIR.mkdir(parents=True, exist_ok=True)

    # Save 512x512 full-bleed Play Store listing icon
    play_icon = master.resize((512, 512), Image.Resampling.LANCZOS)
    play_icon_path = DIST_DIR / "play_store_icon_512.png"
    play_icon.save(play_icon_path, format="PNG", optimize=True)
    print(f"Saved Play Store 512x512 icon: {play_icon_path}")

    # Save legacy mipmap WebP icons for all densities
    for folder, px_size in MIPMAP_SIZES.items():
        target_dir = RES_DIR / folder
        target_dir.mkdir(parents=True, exist_ok=True)
        square_icon = make_masked_icon(master, px_size, circle=False)
        round_icon = make_masked_icon(master, px_size, circle=True)
        square_icon.save(target_dir / "ic_launcher.webp", format="WEBP", quality=95, method=6)
        round_icon.save(target_dir / "ic_launcher_round.webp", format="WEBP", quality=95, method=6)
        print(f"Generated {folder}: {px_size}x{px_size}")


if __name__ == "__main__":
    main()
