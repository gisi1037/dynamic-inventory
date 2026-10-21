"""Original 16px GTNH-style placeholders: hard outlines, shaded metal, PCB and leather.
No upstream pixels are copied. Requires Pillow; no filtering or antialiasing.
"""
from pathlib import Path
from PIL import Image, ImageColor
ROOT = Path(__file__).resolve().parents[2]
PALETTE = {'.':(0,0,0,0),'X':'#22272b','s':'#4c555b','m':'#858e92','h':'#c5cdd0',
           'w':'#e7e5cf','g':'#254c36','G':'#487450','l':'#75a36a','y':'#e4bc5c',
           'o':'#a67833','b':'#243c52','B':'#487892','c':'#80b6c5',
           'd':'#32271f','r':'#674a32','t':'#9e7850'}
SPRITES = {
'capacity_module': [
'................', '...XXXXXXXXXX...', '..XhhhhhhhhhhX..', '..XhmssssssmhX..',
'..XmsggGGggsmX..', '..XmsglXXlgsmX..', '..XmsGlwwlGsmX..', '..XmsGlwwlGsmX..',
'..XmsglXXlgsmX..', '..XmsggGGggsmX..', '..XmssssssssmX..', '..XhhmmmmmmhhX..',
'...XXyoyoyyXX...', '.....yoyoyy.....', '................', '................'],
'inventory_belt': [
'................', '................', '..XXXXXXXXXXXX..', '.XtttrrrrrrrrtX.',
'.XrddddddddddrX.', '.XrdXXXhhXXXdrX.', '.XrdXhhmmhhXdrX.', '.XrdXhBbBmhXdrX.',
'.XrdXmBcbBmXdrX.', '.XrdXmbBbmmXdrX.', '.XrdXXmmmmXXdrX.', '.XrddddddddddrX.',
'.XrrrrrrrrrrrrX.', '..XXXXXXXXXXXX..', '................', '................']}
for name, rows in SPRITES.items():
    assert len(rows) == 16 and all(len(row) == 16 for row in rows)
    icon = Image.new('RGBA', (16,16))
    for y,row in enumerate(rows):
        for x,pixel in enumerate(row):
            color = PALETTE[pixel]
            icon.putpixel((x,y), color if isinstance(color,tuple) else ImageColor.getrgb(color)+(255,))
    target=ROOT/'src/main/resources/assets/dynamicinventory/textures/items'/f'{name}.png'
    target.parent.mkdir(parents=True,exist_ok=True)
    icon.save(target)
preview=Image.new('RGBA',(40,20),'#666666')
for i,name in enumerate(SPRITES): preview.alpha_composite(Image.open(ROOT/'src/main/resources/assets/dynamicinventory/textures/items'/f'{name}.png'),(2+i*20,2))
preview_path = ROOT/'.setup/upgrades/icons-preview.png'
preview_path.parent.mkdir(parents=True, exist_ok=True)
preview.resize((640,320),Image.Resampling.NEAREST).save(preview_path)
