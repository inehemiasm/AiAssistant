"""Generates Google Play Store listing visual assets and promo videos using Pillow + FFmpeg."""

import os
import subprocess
import tempfile
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import imageio_ffmpeg

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST_DIR = os.path.join(ROOT, "distribution")
SCREENSHOTS_SRC = os.path.join(ROOT, "app", "screenshots")
SCREENSHOTS_OUT = os.path.join(DIST_DIR, "screenshots")
ICON_PATH = os.path.join(DIST_DIR, "play_store_icon_512.png")

os.makedirs(SCREENSHOTS_OUT, exist_ok=True)


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = (
        [
            r"C:\Windows\Fonts\segoeuib.ttf",
            r"C:\Windows\Fonts\arialbd.ttf",
            r"C:\Windows\Fonts\calibrib.ttf",
        ]
        if bold
        else [
            r"C:\Windows\Fonts\segoeui.ttf",
            r"C:\Windows\Fonts\arial.ttf",
            r"C:\Windows\Fonts\calibri.ttf",
        ]
    )
    for path in candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def create_gradient_bg(width: int, height: int) -> Image.Image:
    bg = Image.new("RGBA", (width, height), (5, 9, 20, 255))
    draw = ImageDraw.Draw(bg)
    for y in range(height):
        t = y / max(1, height - 1)
        r = int(5 * (1 - t) + 12 * t)
        g = int(10 * (1 - t) + 22 * t)
        b = int(22 * (1 - t) + 46 * t)
        draw.line([(0, y), (width, y)], fill=(r, g, b, 255))

    glow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    gdraw.ellipse(
        (-int(width * 0.15), -int(height * 0.25), int(width * 0.55), int(height * 0.65)),
        fill=(0, 229, 255, 42),
    )
    gdraw.ellipse(
        (int(width * 0.45), int(height * 0.35), int(width * 1.15), int(height * 1.15)),
        fill=(124, 77, 255, 48),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(radius=60))
    return Image.alpha_composite(bg, glow)


def round_corners(im: Image.Image, radius: int) -> Image.Image:
    mask = Image.new("L", im.size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle((0, 0, im.size[0], im.size[1]), radius=radius, fill=255)
    out = im.copy().convert("RGBA")
    out.putalpha(mask)
    return out


def prepare_screen_image(filename: str) -> Image.Image:
    """Loads screenshot and updates any legacy sparkle center icon with the C-Robot emblem."""
    img = Image.open(os.path.join(SCREENSHOTS_SRC, filename)).convert("RGBA")
    if filename == "chat_screen.png":
        # Replace legacy sparkle hero badge at center (272, 312, 448, 488) with the new C-Robot icon
        icon = round_corners(
            Image.open(ICON_PATH).convert("RGBA").resize((156, 156), Image.Resampling.LANCZOS), 38
        )
        img.alpha_composite(icon, (282, 322))
    return img


def make_phone_mockup(screen_img: Image.Image, target_w: int, target_h: int) -> Image.Image:
    """Wraps a 9:16 app screenshot inside a sleek dark hardware phone frame with cyan rim glow."""
    pad = 18
    total_w = target_w + pad * 2
    total_h = target_h + pad * 2
    canvas = Image.new("RGBA", (total_w + 60, total_h + 60), (0, 0, 0, 0))

    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow)
    sdraw.rounded_rectangle(
        (24, 28, 24 + total_w, 28 + total_h),
        radius=54,
        fill=(0, 180, 255, 70),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=20))
    canvas = Image.alpha_composite(canvas, shadow)

    fdraw = ImageDraw.Draw(canvas)
    fdraw.rounded_rectangle(
        (30, 30, 30 + total_w, 30 + total_h),
        radius=52,
        fill=(15, 23, 42, 255),
        outline=(56, 189, 248, 200),
        width=3,
    )

    resized_screen = screen_img.convert("RGBA").resize((target_w, target_h), Image.Resampling.LANCZOS)
    rounded_screen = round_corners(resized_screen, 38)
    canvas.alpha_composite(rounded_screen, (30 + pad, 30 + pad))

    fdraw.rounded_rectangle(
        (30 + total_w // 2 - 42, 30 + 6, 30 + total_w // 2 + 42, 30 + 13),
        radius=4,
        fill=(30, 41, 59, 255),
    )
    return canvas


def generate_feature_graphic():
    """Creates 1024x500 Feature Graphic required by Google Play."""
    w, h = 1024, 500
    canvas = create_gradient_bg(w, h)

    icon = Image.open(ICON_PATH).convert("RGBA").resize((240, 240), Image.Resampling.LANCZOS)
    icon_rounded = round_corners(icon, 56)

    iglow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    idraw = ImageDraw.Draw(iglow)
    idraw.ellipse((45, 105, 335, 395), fill=(0, 229, 255, 90))
    iglow = iglow.filter(ImageFilter.GaussianBlur(radius=32))
    canvas = Image.alpha_composite(canvas, iglow)
    canvas.alpha_composite(icon_rounded, (70, 130))

    rgb = canvas.convert("RGB")
    draw = ImageDraw.Draw(rgb)
    font_badge = load_font(16, bold=True)
    font_title = load_font(62, bold=True)
    font_sub = load_font(23, bold=False)
    font_pill = load_font(17, bold=True)

    # Top badge with dark navy interior and crisp cyan border + text
    draw.rounded_rectangle(
        (350, 95, 608, 133),
        radius=19,
        fill=(11, 27, 50),
        outline=(0, 229, 255),
        width=2,
    )
    draw.text((370, 104), "PRIVATE ON-DEVICE AI", font=font_badge, fill=(0, 229, 255))

    # Title & Subtitle
    draw.text((350, 145), "CHEVERE AI", font=font_title, fill=(248, 250, 252))
    draw.text(
        (350, 228),
        "Local LLM Chat, Vision Analysis, Document RAG\n& Hardware Sensor Tools — Zero Cloud Lock-In.",
        font=font_sub,
        fill=(186, 204, 228),
        spacing=6,
    )

    # Feature pills
    pills = ["Gemma 4 LiteRT", "Vision & Docs", "Sensor Radar", "100% Private"]
    px = 350
    py = 334
    for pill in pills:
        bbox = draw.textbbox((0, 0), pill, font=font_pill)
        pw = (bbox[2] - bbox[0]) + 32
        ph = 40
        draw.rounded_rectangle(
            (px, py, px + pw, py + ph),
            radius=20,
            fill=(15, 25, 48),
            outline=(99, 102, 241),
            width=2,
        )
        draw.text((px + 16, py + 9), pill, font=font_pill, fill=(226, 232, 240))
        px += pw + 12

    out_path = os.path.join(DIST_DIR, "feature_graphic_1024x500.png")
    rgb.save(out_path, "PNG", optimize=True)
    print(f"Saved feature graphic: {out_path}")
    return out_path


SLIDES = [
    {
        "filename": "01_private_on_device_ai.png",
        "src": "chat_screen.png",
        "tag": "LOCAL-FIRST PRIVACY",
        "title": "Private AI, Powered\nby Your Device",
        "subtitle": "Run Gemma 4 & LiteRT language models\ndirectly on your phone—even offline.",
    },
    {
        "filename": "02_agentic_tools_and_chat.png",
        "src": "chat_screen_with_tool_running.png",
        "tag": "AGENTIC WORKFLOWS",
        "title": "Smart Assistant &\nConfirmed Actions",
        "subtitle": "Search docs, check weather, draft emails,\nand run MCP tools under your control.",
    },
    {
        "filename": "03_local_model_hub.png",
        "src": "marketplace_screen.png",
        "tag": "ON-DEVICE MODEL HUB",
        "title": "Download & Switch\nLocal AI Models",
        "subtitle": "Choose compact or multimodal LiteRT models\nverified with SHA-256 integrity.",
    },
    {
        "filename": "04_hardware_sensor_radar.png",
        "src": "sensors_radar_screen.png",
        "tag": "HARDWARE SENSOR SUITE",
        "title": "Live Sensor Radar &\nStud / Metal Finder",
        "subtitle": "Inspect magnetic fields, ambient noise dB,\nspirit level, light lux, and pressure.",
    },
    {
        "filename": "05_tasks_and_checklists.png",
        "src": "tasks_screen.png",
        "tag": "PRODUCTIVITY & TASKS",
        "title": "Actionable Tasks\nFrom Any Chat",
        "subtitle": "Extract checklists from conversations and\nmanage your to-dos locally on-device.",
    },
    {
        "filename": "06_hardware_benchmark.png",
        "src": "benchmark_screen.png",
        "tag": "NEURAL DIAGNOSTICS",
        "title": "Real-Time Hardware\nSpeed Benchmarks",
        "subtitle": "Measure Time-to-First-Token (TTFT),\nGPU/CPU acceleration, and memory.",
    },
]


def generate_phone_screenshots():
    """Creates 6 1080x1920 (9:16) framed marketing screenshots for Google Play."""
    w, h = 1080, 1920
    font_tag = load_font(22, bold=True)
    font_title = load_font(62, bold=True)
    font_sub = load_font(31, bold=False)
    icon_small = round_corners(
        Image.open(ICON_PATH).convert("RGBA").resize((68, 68), Image.Resampling.LANCZOS), 16
    )

    out_paths = []
    for slide in SLIDES:
        canvas = create_gradient_bg(w, h)
        canvas.alpha_composite(icon_small, (72, 68))

        src_img = prepare_screen_image(slide["src"])
        mockup = make_phone_mockup(src_img, 748, 1330)
        mx = (w - mockup.size[0]) // 2
        my = 492
        canvas.alpha_composite(mockup, (mx, my))

        rgb = canvas.convert("RGB")
        draw = ImageDraw.Draw(rgb)

        tb = draw.textbbox((0, 0), slide["tag"], font=font_tag)
        tag_w = (tb[2] - tb[0]) + 44
        draw.rounded_rectangle(
            (158, 79, 158 + tag_w, 125),
            radius=23,
            fill=(11, 27, 50),
            outline=(0, 229, 255),
            width=2,
        )
        draw.text((180, 88), slide["tag"], font=font_tag, fill=(0, 229, 255))

        draw.text((72, 154), slide["title"], font=font_title, fill=(248, 250, 252), spacing=10)
        draw.text((72, 330), slide["subtitle"], font=font_sub, fill=(186, 204, 228), spacing=8)

        out_path = os.path.join(SCREENSHOTS_OUT, slide["filename"])
        rgb.save(out_path, "PNG", optimize=True)
        out_paths.append(out_path)
        print(f"Saved phone screenshot: {out_path}")
    return out_paths


def render_landscape_video_slide(slide: dict, index: int, total: int) -> Image.Image:
    """Renders a 1920x1080 16:9 presentation frame for the promo video."""
    w, h = 1920, 1080
    canvas = create_gradient_bg(w, h)

    icon = round_corners(
        Image.open(ICON_PATH).convert("RGBA").resize((110, 110), Image.Resampling.LANCZOS), 26
    )
    canvas.alpha_composite(icon, (130, 140))

    src_img = prepare_screen_image(slide["src"])
    mockup = make_phone_mockup(src_img, 495, 880)
    canvas.alpha_composite(mockup, (1210, (h - mockup.size[1]) // 2))

    rgb = canvas.convert("RGB")
    draw = ImageDraw.Draw(rgb)

    font_brand = load_font(38, bold=True)
    font_tag = load_font(23, bold=True)
    font_title = load_font(72, bold=True)
    font_sub = load_font(34, bold=False)

    draw.text((262, 156), "CHEVERE AI", font=font_brand, fill=(248, 250, 252))
    draw.text((262, 204), "On-Device AI Assistant", font=load_font(24, False), fill=(56, 189, 248))

    tb = draw.textbbox((0, 0), slide["tag"], font=font_tag)
    tag_w = (tb[2] - tb[0]) + 52
    draw.rounded_rectangle(
        (130, 320, 130 + tag_w, 372),
        radius=26,
        fill=(11, 27, 50),
        outline=(0, 229, 255),
        width=2,
    )
    draw.text((156, 332), slide["tag"], font=font_tag, fill=(0, 229, 255))

    draw.text((130, 410), slide["title"], font=font_title, fill=(255, 255, 255), spacing=12)
    draw.text((130, 625), slide["subtitle"], font=font_sub, fill=(186, 204, 228), spacing=10)

    dot_x = 130
    dot_y = 880
    for i in range(total):
        if i == index:
            draw.rounded_rectangle((dot_x, dot_y, dot_x + 54, dot_y + 14), radius=7, fill=(0, 229, 255))
            dot_x += 70
        else:
            draw.ellipse((dot_x, dot_y, dot_x + 14, dot_y + 14), fill=(71, 85, 105))
            dot_x += 30

    return rgb


def render_intro_card() -> Image.Image:
    """Renders 1920x1080 hero intro card with the C-Robot icon."""
    w, h = 1920, 1080
    canvas = create_gradient_bg(w, h)

    icon = round_corners(
        Image.open(ICON_PATH).convert("RGBA").resize((280, 280), Image.Resampling.LANCZOS), 64
    )
    iglow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    idraw = ImageDraw.Draw(iglow)
    idraw.ellipse((w // 2 - 180, 140, w // 2 + 180, 500), fill=(0, 229, 255, 95))
    iglow = iglow.filter(ImageFilter.GaussianBlur(radius=45))
    canvas = Image.alpha_composite(canvas, iglow)
    canvas.alpha_composite(icon, (w // 2 - 140, 180))

    rgb = canvas.convert("RGB")
    draw = ImageDraw.Draw(rgb)
    f_title = load_font(92, bold=True)
    f_sub = load_font(38, bold=False)
    f_tag = load_font(25, bold=True)

    title = "CHEVERE AI"
    tb = draw.textbbox((0, 0), title, font=f_title)
    draw.text(((w - (tb[2] - tb[0])) // 2, 510), title, font=f_title, fill=(248, 250, 252))

    sub = "Private On-Device LLM • Vision • Document RAG • Sensor Tools"
    sb = draw.textbbox((0, 0), sub, font=f_sub)
    draw.text(((w - (sb[2] - sb[0])) // 2, 635), sub, font=f_sub, fill=(186, 204, 228))

    badge = "100% LOCAL NEURAL INFERENCE ON ANDROID"
    bb = draw.textbbox((0, 0), badge, font=f_tag)
    bw = (bb[2] - bb[0]) + 64
    bx = (w - bw) // 2
    draw.rounded_rectangle(
        (bx, 740, bx + bw, 798),
        radius=29,
        fill=(11, 27, 50),
        outline=(0, 229, 255),
        width=2,
    )
    draw.text((bx + 32, 753), badge, font=f_tag, fill=(0, 229, 255))

    return rgb


def generate_promo_videos(portrait_paths):
    """Uses FFmpeg to build smooth 1080p 30fps MP4 promo videos (16:9 for YouTube/Play Store and 9:16 vertical)."""
    ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
    tmp_dir = tempfile.mkdtemp(prefix="chevere_video_")

    cards = [render_intro_card()] + [
        render_landscape_video_slide(s, idx, len(SLIDES)) for idx, s in enumerate(SLIDES)
    ]

    fps = 30
    hold_frames = 75       # 2.5s per card
    fade_frames = 15       # 0.5s crossfade

    frame_idx = 0
    for i, card in enumerate(cards):
        w, h = card.size
        for f in range(hold_frames):
            t = f / max(1, hold_frames - 1)
            scale = 1.0 + 0.018 * t
            nw, nh = int(w * scale), int(h * scale)
            zoomed = card.resize((nw, nh), Image.Resampling.BILINEAR)
            left = (nw - w) // 2
            top = (nh - h) // 2
            cropped = zoomed.crop((left, top, left + w, top + h))
            cropped.save(os.path.join(tmp_dir, f"frame_{frame_idx:05d}.jpg"), "JPEG", quality=95)
            frame_idx += 1

        if i + 1 < len(cards):
            next_card = cards[i + 1]
            for f in range(fade_frames):
                alpha = (f + 1) / (fade_frames + 1)
                blended = Image.blend(cropped, next_card, alpha)
                blended.save(os.path.join(tmp_dir, f"frame_{frame_idx:05d}.jpg"), "JPEG", quality=95)
                frame_idx += 1

    landscape_mp4 = os.path.join(DIST_DIR, "chevere_store_promo_1080p.mp4")
    cmd_landscape = [
        ffmpeg_exe,
        "-y",
        "-framerate",
        str(fps),
        "-i",
        os.path.join(tmp_dir, "frame_%05d.jpg"),
        "-f",
        "lavfi",
        "-i",
        "anullsrc=channel_layout=stereo:sample_rate=44100",
        "-c:v",
        "libx264",
        "-pix_fmt",
        "yuv420p",
        "-profile:v",
        "high",
        "-crf",
        "18",
        "-c:a",
        "aac",
        "-shortest",
        "-movflags",
        "+faststart",
        landscape_mp4,
    ]
    subprocess.run(cmd_landscape, check=True)
    print(f"Saved 16:9 YouTube/Play Store promo video: {landscape_mp4}")

    tmp_v_dir = tempfile.mkdtemp(prefix="chevere_vvideo_")
    v_cards = [Image.open(p).convert("RGB") for p in portrait_paths]
    v_idx = 0
    for i, card in enumerate(v_cards):
        for f in range(60):
            card.save(os.path.join(tmp_v_dir, f"vframe_{v_idx:05d}.jpg"), "JPEG", quality=95)
            v_idx += 1
        if i + 1 < len(v_cards):
            next_card = v_cards[i + 1]
            for f in range(15):
                alpha = (f + 1) / 16.0
                blended = Image.blend(card, next_card, alpha)
                blended.save(os.path.join(tmp_v_dir, f"vframe_{v_idx:05d}.jpg"), "JPEG", quality=95)
                v_idx += 1

    vertical_mp4 = os.path.join(DIST_DIR, "chevere_store_promo_vertical_1080x1920.mp4")
    cmd_vertical = [
        ffmpeg_exe,
        "-y",
        "-framerate",
        str(fps),
        "-i",
        os.path.join(tmp_v_dir, "vframe_%05d.jpg"),
        "-f",
        "lavfi",
        "-i",
        "anullsrc=channel_layout=stereo:sample_rate=44100",
        "-c:v",
        "libx264",
        "-pix_fmt",
        "yuv420p",
        "-profile:v",
        "high",
        "-crf",
        "18",
        "-c:a",
        "aac",
        "-shortest",
        "-movflags",
        "+faststart",
        vertical_mp4,
    ]
    subprocess.run(cmd_vertical, check=True)
    print(f"Saved 9:16 vertical promo video: {vertical_mp4}")


if __name__ == "__main__":
    generate_feature_graphic()
    shots = generate_phone_screenshots()
    generate_promo_videos(shots)
