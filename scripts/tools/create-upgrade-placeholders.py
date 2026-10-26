"""Extend our existing 16px belt icon with GT voltage colors; preserves the existing item artwork."""
from pathlib import Path
from PIL import Image, ImageDraw
root = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/dynamicinventory/textures/items'
base = Image.open(root / 'inventory_belt.png').convert('RGBA')
# Dark outlines, small metal highlights, no smoothing; colors follow GTValues.TIER_COLORS.
colors = {'hv': (218, 190, 58), 'iv': (76, 94, 187), 'zpm': (67, 185, 192), 'uhv': (157, 48, 56)}
for name, accent in colors.items():
    image = base.copy()
    for y in range(16):
        for x in range(16):
            r, g, b, a = image.getpixel((x, y))
            if a and b > r and b > 70 and (r, g, b) not in [(197, 205, 208), (133, 142, 146)]:
                level = (r + g + b) / 3 / 118
                image.putpixel((x, y), tuple(min(255, int(c * level)) for c in accent) + (255,))
    # One-to-four tiny tier marks on the strap remain visible when pack palettes are similar.
    draw = ImageDraw.Draw(image)
    for mark in range(list(colors).index(name) + 1):
        draw.point((4 + mark * 2, 12), fill=(*accent, 255))
    image.save(root / f'inventory_belt_{name}.png')
image = Image.open(root / 'capacity_module.png').convert('RGBA')
draw = ImageDraw.Draw(image)
draw.rectangle((4, 5, 11, 10), fill=(33, 37, 43, 255))
for point in [(4, 7), (5, 6), (6, 6), (7, 7), (8, 8), (9, 9), (10, 9), (11, 8),
              (4, 8), (5, 9), (6, 9), (7, 8), (8, 7), (9, 6), (10, 6), (11, 7)]:
    draw.point(point, fill=(196, 99, 196, 255))
image.save(root / 'creative_inventory_controller.png')
