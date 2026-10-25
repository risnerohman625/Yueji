"""Export the launcher artwork directly from its Android vector resources."""
from pathlib import Path
import html
import re
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "branding"
ANDROID = "{http://schemas.android.com/apk/res/android}"
DRAWABLE = ROOT / "app" / "src" / "main" / "res" / "drawable"


def outline(data):
    tokens = re.findall(r"[MLCZ]|-?\d+(?:\.\d+)?", data)
    points = []
    index = 0
    while index < len(tokens):
        command = tokens[index]
        index += 1
        if command in ("M", "L"):
            current = (float(tokens[index]), float(tokens[index + 1]))
            points.append(current)
            index += 2
        elif command == "C":
            values = list(map(float, tokens[index:index + 6]))
            index += 6
            start = current
            a, b, end = tuple(values[:2]), tuple(values[2:4]), tuple(values[4:])
            for step in range(1, 65):
                t = step / 64
                u = 1 - t
                points.append(tuple(u**3 * start[d] + 3 * u*u*t * a[d]
                                    + 3 * u*t*t * b[d] + t**3 * end[d]
                                    for d in (0, 1)))
            current = end
        elif command != "Z":
            raise ValueError(f"Unsupported path command: {command}")
    return points


def rounded(image, radius):
    mask = Image.new("L", image.size)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, image.width - 1, image.height - 1), radius, fill=255)
    result = image.convert("RGBA")
    result.putalpha(mask)
    return result


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    foreground = ET.parse(DRAWABLE / "ic_launcher_foreground.xml").getroot()
    paths = [(p.get(ANDROID + "pathData"), p.get(ANDROID + "fillColor"))
             for p in foreground.findall("path")]
    gradient = ET.parse(DRAWABLE / "ic_launcher_background.xml").find(".//gradient")
    start, end = (gradient.get(ANDROID + name) for name in ("startColor", "endColor"))
    svg_paths = "\n".join(
        f'    <path fill="{color}" d="{html.escape(data, quote=True)}"/>'
        for data, color in paths)
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 72 72">
  <title>Yueji - Balance Petals</title>
  <defs><linearGradient id="background" x1="0" y1="0" x2="1" y2="1">
    <stop stop-color="{start}"/><stop offset="1" stop-color="{end}"/>
  </linearGradient></defs>
  <path fill="url(#background)" d="M0 0h72v72H0z"/>
  <g transform="translate(-18 -18)">
{svg_paths}
  </g>
</svg>
'''
    (OUT / "yueji-icon.svg").write_text(svg, encoding="utf-8")
    size = 2048
    ramp = Image.linear_gradient("L").resize((size * 2, size * 2))
    diagonal = ramp.transform((size, size), Image.Transform.AFFINE, (1, 0, 0, 1, 1, 0))
    image = Image.composite(Image.new("RGB", (size, size), end),
                            Image.new("RGB", (size, size), start), diagonal)
    draw = ImageDraw.Draw(image)
    for data, color in paths:
        draw.polygon([((x - 18) * size / 72, (y - 18) * size / 72)
                      for x, y in outline(data)], fill=color)
    image = image.resize((1024, 1024), Image.Resampling.LANCZOS)
    image.save(OUT / "yueji-icon.png")
    board = Image.new("RGB", (1200, 720), "#F7F5FA")
    board.paste("#1C1924", (600, 0, 1200, 720))
    labels = ImageDraw.Draw(board)
    font = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 22)
    for offset, ink in ((0, "#635B73"), (600, "#D4CBDF")):
        large = rounded(image.resize((320, 320), Image.Resampling.LANCZOS), 74)
        board.paste(large, (offset + 140, 82), large)
        labels.text((offset + 140, 438), "Yueji / Balance Petals", font=font, fill=ink)
        for x, dimension in ((140, 96), (276, 64), (380, 48), (468, 32)):
            small = rounded(image.resize((dimension, dimension), Image.Resampling.LANCZOS),
                            dimension * .23)
            board.paste(small, (offset + x, 512 + (96 - dimension) // 2), small)
            labels.text((offset + x, 630), str(dimension), font=font, fill=ink)
    board.save(OUT / "yueji-icon-preview.png")
    for name in ("yueji-icon.svg", "yueji-icon.png", "yueji-icon-preview.png"):
        path = OUT / name
        assert path.is_file() and path.stat().st_size > 0
        print(f"{path}: {path.stat().st_size} bytes")


if __name__ == "__main__":
    main()
