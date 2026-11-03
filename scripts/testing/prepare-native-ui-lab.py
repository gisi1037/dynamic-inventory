"""Prepare the existing, CLOSED storage UI lab using its own saved Forge IDs.

Requires nbtlib (pip install nbtlib). The local task can use --python-path for an
isolated dependency directory. Plan by default; --apply backs up and atomically
updates only affected region files. Never edits level.dat, players or other worlds.
"""
import argparse, copy, hashlib, io, json, math, os, struct, subprocess, sys, time, uuid, zlib
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--instance', type=Path, default=Path(os.environ['APPDATA'])/'PrismLauncher/instances/GTNH-Dev-Shared-2.9.0-J26')
parser.add_argument('--world', default='DI-Baseline-Storage-Panel-Lab-Qualification')
parser.add_argument('--python-path', type=Path)
parser.add_argument('--output', type=Path, default=Path('.setup/three-mod-ui-batch/offline-lab'))
parser.add_argument('--apply', action='store_true')
args = parser.parse_args()
if args.python_path: sys.path.insert(0, str(args.python_path.resolve()))
import nbtlib as n
from nbtlib import tag as T
assert args.instance.name == 'GTNH-Dev-Shared-2.9.0-J26', 'Only the separate development instance is allowed'
assert args.world == 'DI-Baseline-Storage-Panel-Lab-Qualification', 'Only the existing disposable storage lab is allowed'
world = args.instance.resolve()/'.minecraft/saves'/args.world
assert world.is_dir()
if os.name == 'nt':
    guard = "$p=@(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains('GTNH-Dev-Shared-2.9.0-J26')}); if($p.Count){exit 9}"
    status = subprocess.run(['powershell.exe','-NoProfile','-Command',guard], capture_output=True, creationflags=subprocess.CREATE_NO_WINDOW)
    assert status.returncode == 0, 'Development game must be fully closed before offline lab preparation'
out = args.output.resolve(); out.mkdir(parents=True, exist_ok=True)
receipt = out/'installed.json'
assert not receipt.exists(), 'This addition is already installed; existing stations are preserved'

sha = lambda b: hashlib.sha256(b).hexdigest()
def dump(f):
    stream = io.BytesIO(); f.write(stream); return stream.getvalue()
level_bytes = (world/'level.dat').read_bytes()
level = n.load(world/'level.dat')
ids = {str(v['K']): int(v['V']) for v in level['FML']['ItemData']}
blocks = {k[1:]: v for k,v in ids.items() if k.startswith('\x01')}
items = {k[1:]: v for k,v in ids.items() if k.startswith('\x02')}
items.update({k:v for k,v in blocks.items() if k not in items})
assert str(level['Data']['generatorName']) == 'flat'
assert all(blocks[k] == v for k,v in [('minecraft:air',0),('minecraft:standing_sign',63),('minecraft:chest',54)])
protected = {str(p.relative_to(world)): sha(p.read_bytes()) for p in world.rglob('*') if p.is_file() and (p.name.startswith('level.dat') or 'playerdata' in p.parts)}
session = (world/'session.lock').read_bytes()
regions, chunks, originals, original_raw = {}, {}, {}, {}
changed = set(); placements = []; labels = []

def get_chunk(cx,cz):
    key=(cx,cz)
    if key in chunks: return chunks[key]
    rp=world/'region'/f'r.{cx//32}.{cz//32}.mca'
    if rp not in regions:
        data=rp.read_bytes(); assert len(data)%4096 == 0
        regions[rp]=data
    data=regions[rp]; idx=(cx%32)+(cz%32)*32
    off=int.from_bytes(data[4*idx:4*idx+3],'big'); sectors=data[4*idx+3]
    assert off and sectors, f'Lab chunk {key} has not been generated; refusing to synthesize terrain'
    at=off*4096; length=struct.unpack_from('>I',data,at)[0]; mode=data[at+4]
    assert mode==2 and length+4 <= sectors*4096
    raw=zlib.decompress(data[at+5:at+4+length]); f=n.File.parse(io.BytesIO(raw))
    assert dump(f)==raw, f'NBT round trip changed existing bytes in {key}'
    assert int(f['Level']['xPos'])==cx and int(f['Level']['zPos'])==cz
    f=copy.deepcopy(f)  # Parsed NumPy-backed NBT arrays initially view immutable input bytes.
    chunks[key]=f; originals[key]=copy.deepcopy(f); original_raw[key]=raw
    return f

def section_at(x,y,z, create=False):
    chunk=get_chunk(x//16,z//16); sections=chunk['Level']['Sections']
    for section in sections:
        if int(section['Y']) == y//16: return section
    assert not create, 'This lab is flat and all targets must lie in an existing section'
    return None

def nibble(a,i): return (int(a[i//2]) & 255) >> (4*(i%2)) & 15

def set_nibble(a,i,v):
    j=i//2; shift=4*(i%2); b=(int(a[j]) & 255) & ~(15<<shift) | ((v&15)<<shift)
    a[j]=b if b<128 else b-256

def index(x,y,z): return (y%16)*256+(z%16)*16+x%16

def get_block(x,y,z):
    s=section_at(x,y,z)
    if s is None: return 0,0
    i=index(x,y,z); low=int(s['Blocks'][i])&255
    return low+(nibble(s['Add'],i)<<8 if 'Add' in s else 0),nibble(s['Data'],i)

def base_te(name,x,y,z,**fields):
    return T.Compound(dict(id=T.String(name),x=T.Int(x),y=T.Int(y),z=T.Int(z),**fields))

def stage_block(name,x,y,z,meta=0,te=None):
    assert -27 <= x <= 27 and 102 <= z <= 400 and 4 <= y <= 7
    assert name in blocks, f'Missing saved block mapping {name}'
    assert blocks[name] < 4096 and 0 <= meta < 16
    assert get_block(x,y,z)[0]==0, f'Occupied lab target {x},{y},{z}'
    c=get_chunk(x//16,z//16)
    assert not any(int(t['x'])==x and int(t['y'])==y and int(t['z'])==z for t in c['Level']['TileEntities'])
    assert not any(p['x']==x and p['y']==y and p['z']==z for p in placements), 'Duplicate planned target'
    placements.append(dict(block=name,x=x,y=y,z=z,meta=meta,te=te))

def sign(x,z,lines):
    lines=[str(v)[:15] for v in lines]
    while len(lines)<4: lines.append('')
    te=base_te('Sign',x,4,z,**{f'Text{i+1}':T.String(v) for i,v in enumerate(lines)})
    stage_block('minecraft:standing_sign',x,4,z,8,te)

def station(group,name,block,meta=0,kind=None,details='Right-click UI'):
    i=len(labels); x=-24+(i%7)*8; z=144+(i//7)*8
    enabled=block in blocks
    te=None
    if enabled and kind is not None and kind.startswith('shulker:'):
        tier=int(kind.split(':')[1]);te=base_te('etfuturum.shulker_box',x,4,z,Type=T.Byte(tier),Color=T.Byte(0),Facing=T.Byte(1),Items=T.List[T.Compound]([]))
    if enabled and kind=='conduit':
        te=base_te('blockConduitBundleTileEntity',x,4,z,nbtVersion=T.Short(1),facadeId=T.String('null'),facadeMeta=T.Int(0),conduits=T.List[T.Compound]([
            T.Compound({'conduitType':T.String('crazypants.enderio.conduit.item.ItemConduit'),'conduit':T.Compound({'connections':T.IntArray([]),'externalConnections':T.IntArray([3]),'metaData':T.Short(0)})})]))
        stage_block('minecraft:chest',x,4,z+1,0,base_te('Chest',x,4,z+1,Items=T.List[T.Compound]([])))
    if enabled: stage_block(block,x,4,z,meta,te)
    sign(x,z-2,[group,name,details if enabled else 'Pack disabled','UI test batch'])
    labels.append(dict(group=group,name=name,block=block,meta=meta,enabled=enabled,x=x,y=4,z=z,details=details))

for name,block in [('Brewing stand','brewing_stand'),('Enchanting','enchantment_table'),('Smithing table','smithing_table'),('Blast furnace','blast_furnace'),('Smoker','smoker'),('Beacon','beacon')]:
    station('Et Futurum',name,'etfuturum:'+block)
station('Et Futurum','Anvil','minecraft:anvil')
for tier in ['','iron_','gold_','diamond_','copper_','silver_','steel_','obsidian_','darksteel_','netherite_']:
    station('Et Futurum',(tier.replace('_',' ').title() or 'Wood ')+'Barrel','etfuturum:'+tier+'barrel')
for i,tier in enumerate(['Vanilla','Iron','Gold','Diamond','Copper','Silver','Crystal','Obsidian']):
    station('Et Futurum',tier+' Shulker','etfuturum:shulker_box',kind='shulker:'+str(i))

eio=[('Alloy Smelter','AlloySmelter'),('SAG Mill','SagMill'),('Painter','Painter'),('Stirling Gen','StirlingGenerator'),('Combustion Gen','CombustionGenerator'),('Zombie Gen','ZombieGenerator'),('Franken Gen','FrankenzombieGenerator'),('Ender Gen','EnderGenerator'),('Farming Station','FarmStation'),('Vat','Vat'),('Crafter','Crafter'),('Powered Spawner','PoweredSpawner'),('Slice and Splice','SliceAndSplice'),('Soul Binder','SoulBinder'),('Killer Joe','KillerJoe'),('Enchanter','Enchanter'),('Vacuum Chest','VacuumChest'),('Attractor','Attractor'),('Aversion','SpawnGuard'),('Inhibitor','InhibitorObelisk'),('Weather Obelisk','WeatherObelisk'),('Transceiver','Transceiver'),('Inventory Panel','InventoryPanel'),('Travel Anchor','TravelAnchor'),('TelePad','TelePad'),('Darksteel Anvil','DarkSteelAnvil')]
for name,suffix in eio: station('Ender IO',name,'EnderIO:block'+suffix)
for i,name in enumerate(['Simple Cap Bank','Capacitor Bank','Vibrant Cap Bank','Creative Cap']): station('Ender IO',name,'EnderIO:blockCapBank',i)
for i,name in enumerate(['Fluid Tank','Pressurized Tank']): station('Ender IO',name,'EnderIO:blockTank',i)
for i,name in enumerate(['Item Buffer','Omni Buffer']): station('Ender IO',name,'EnderIO:blockBuffer',i)
station('Ender IO','Conduit Config','EnderIO:blockConduitBundle',kind='conduit',details='Wrench: chest')
for name,suffix in [('Power Monitor','PowerMonitor'),('XP Obelisk','ExperienceObelisk'),('Hypercube','HyperCube'),('Ender IO','EnderIo')]:
    station('Ender IO',name,'EnderIO:block'+suffix,details='Native controls')

for meta,name in [(0,'Filing Cabinet'),(1,'Advanced Filing')]: station('Extra Utilities',name,'ExtraUtilities:filing',meta)
for name,block in [('Mini Chest','chestMini'),('Full Chest','chestFull'),('QED','endConstructor'),('Trading Post','trading_post')]: station('Extra Utilities',name,'ExtraUtilities:'+block)
for meta,name in [(0,'Item Trash'),(1,'Fluid Trash'),(2,'Energy Trash')]: station('Extra Utilities',name,'ExtraUtilities:trashcan',meta)
station('Extra Utilities','Sorting Pipe','ExtraUtilities:pipes',9)
for block,prefix in [('extractor_base','Transfer'),('extractor_base_remote','Retrieval')]:
    for meta,name in [(0,'Items'),(6,'Fluids')]: station('Extra Utilities',prefix+' '+name,'ExtraUtilities:'+block,meta)
for meta,name in [(12,'Energy Node'),(13,'Hyper Energy')]: station('Extra Utilities',name,'ExtraUtilities:extractor_base',meta)
names=['Survivalist','Furnace','Lava','Ender','Redstone','Culinary','Potions','Solar','TNT','Pink','Overclocked','Nether Star']
for tier,block in [('1x','generator'),('8x','generator.8'),('64x','generator.64')]:
    for meta,name in enumerate(names): station('Extra Utilities',name+' '+tier,'ExtraUtilities:'+block,meta)

supply_names=['EnderIO:itemYetaWrench','EnderIO:itemConduitProbe','EnderIO:itemBasicFilterUpgrade','EnderIO:itemBigFilterUpgrade','EnderIO:itemExistingItemFilter','EnderIO:itemLimitedItemFilter','EnderIO:itemModItemFilter','EnderIO:itemPowerItemFilter','EnderIO:itemItemConduit','EnderIO:itemPowerConduit','EnderIO:itemLiquidConduit','EnderIO:itemRedstoneConduit','ExtraUtilities:nodeUpgrade','ExtraUtilities:golden_bag']
supplies=[]; disabled_supplies=[]
for item in supply_names:
    if item not in items: disabled_supplies.append(item);continue
    supplies.append(T.Compound({'id':T.Short(items[item]),'Damage':T.Short(1 if item=='ExtraUtilities:nodeUpgrade' else 0),'Count':T.Byte(1),'Slot':T.Byte(len(supplies))}))
stage_block('minecraft:chest',24,4,104,0,base_te('Chest',24,4,104,Items=T.List[T.Compound](supplies),CustomName=T.String('UI tools and filters')))
sign(24,102,['UI test supplies','Tools + filters','Bag pack-disabled' if disabled_supplies else 'Item UI tools','Next rows: Z144'])
sign(0,136,['NEW UI TESTS','EtF + Ender IO','Extra Utilities','Start at Z144'])

# One stationary, persistent villager makes the Trading Post useful without touching player inventory.
trade=next(v for v in labels if v['block']=='ExtraUtilities:trading_post')
entity_chunk=get_chunk((trade['x']+2)//16,trade['z']//16)
uid=uuid.uuid4().int
signed64=lambda value:value if value<1<<63 else value-(1<<64)
villager=T.Compound({'id':T.String('Villager'),'Pos':T.List[T.Double]([trade['x']+2.5,4.0,trade['z']+0.5]),'Motion':T.List[T.Double]([0.0,0.0,0.0]),'Rotation':T.List[T.Float]([0.0,0.0]),'UUIDMost':T.Long(signed64(uid>>64)),'UUIDLeast':T.Long(signed64(uid&((1<<64)-1))),'Health':T.Float(20.0),'Age':T.Int(0),'Profession':T.Int(0),'PersistenceRequired':T.Byte(1),'Invulnerable':T.Byte(1),'CustomName':T.String('UI Test Trader'),'Attributes':T.List[T.Compound]([T.Compound({'Name':T.String('generic.movementSpeed'),'Base':T.Double(0.0)})]),'Offers':T.Compound({'Recipes':T.List[T.Compound]([T.Compound({'buy':T.Compound({'id':T.Short(items['minecraft:wheat']),'Count':T.Byte(20),'Damage':T.Short(0)}),'sell':T.Compound({'id':T.Short(items['minecraft:emerald']),'Count':T.Byte(1),'Damage':T.Short(0)}),'uses':T.Int(0),'maxUses':T.Int(9999)})])})})

# Prepare every chunk in memory and check exact preservation of existing NBT before touching files.
for p in placements:
    x,y,z=p['x'],p['y'],p['z']; key=(x//16,z//16); c=get_chunk(*key);s=section_at(x,y,z);i=index(x,y,z);bid=blocks[p['block']]
    s['Blocks'][i]=(bid&255) if (bid&255)<128 else (bid&255)-256
    if 'Add' not in s: s['Add']=T.ByteArray([0]*2048)
    set_nibble(s['Add'],i,bid>>8);set_nibble(s['Data'],i,p['meta'])
    set_nibble(s['BlockLight'],i,0);set_nibble(s['SkyLight'],i,0 if p['block']!='minecraft:standing_sign' else 15)
    if p['block']!='minecraft:standing_sign': c['Level']['HeightMap'][(z%16)*16+x%16]=max(int(c['Level']['HeightMap'][(z%16)*16+x%16]),y+1)
    c['Level']['LightPopulated']=T.Byte(0)
    if p['te'] is not None:c['Level']['TileEntities']=T.List[T.Compound]([*c['Level']['TileEntities'],p['te']])
    changed.add(key)
entity_chunk['Level']['Entities']=T.List[T.Compound]([*entity_chunk['Level']['Entities'],villager])
changed.add(((trade['x']+2)//16,trade['z']//16))
for key in changed:
    old=originals[key];new=chunks[key]
    old_tes=old['Level']['TileEntities'];assert len(new['Level']['TileEntities'])>=len(old_tes)
    for a,b in zip(old_tes,new['Level']['TileEntities']): assert dump(n.File({'v':a}))==dump(n.File({'v':b})), 'Existing block-entity NBT changed'
    for a,b in zip(old['Level']['Entities'],new['Level']['Entities']):assert dump(n.File({'v':a}))==dump(n.File({'v':b})), 'Existing entity NBT changed'
    allowed={(index(p['x'],p['y'],p['z'])) for p in placements if (p['x']//16,p['z']//16)==key}
    for a,b in zip(old['Level']['Sections'],new['Level']['Sections']):
        for i in range(4096):
            if int(a['Y'])!=0 or i not in allowed:
                assert int(a['Blocks'][i])==int(b['Blocks'][i])
                for tag in ['Add','Data','BlockLight','SkyLight']:
                    assert (nibble(a[tag],i) if tag in a else 0)==(nibble(b[tag],i) if tag in b else 0),f'Untouched cell changed: {key} {tag} {i}'
plan={'world':str(world),'stations':labels,'supplies':supply_names,'disabledSupplies':disabled_supplies,'blocksAndSigns':len(placements),'modifiedChunks':len(changed),'start':{'x':-24,'y':4,'z':144},'endZ':max(v['z'] for v in labels),'playerFilesPreserved':protected,'mode':'offline plan; not visually/gameplay qualified'}
(out/'stations.json').write_text(json.dumps(plan,indent=2))
print(f"PLAN: {len(labels)} stations, {len(placements)} blocks/signs, {len(changed)} existing chunks, Z144..{plan['endZ']}")
print('DISABLED SUPPLIES:',disabled_supplies)
if not args.apply:sys.exit(0)
assert (world/'level.dat').read_bytes()==level_bytes and (world/'session.lock').read_bytes()==session, 'World changed during planning'
backup=out/'world-before';backup.mkdir(exist_ok=False)
(backup/'level.dat').write_bytes(level_bytes)
written=[]
for rp,original in regions.items():
    affected=[key for key in changed if key[0]//32==int(rp.name.split('.')[1]) and key[1]//32==int(rp.name.split('.')[2])]
    if not affected: continue
    assert rp.read_bytes()==original, 'Region changed since it was read'
    (backup/rp.name).write_bytes(original)
    data=bytearray(original)
    for key in sorted(affected):
        raw=dump(chunks[key]); compressed=zlib.compress(raw); payload=struct.pack('>I',len(compressed)+1)+b'\x02'+compressed
        sectors=math.ceil(len(payload)/4096);assert sectors<=255
        off=len(data)//4096;assert off<1<<24
        data.extend(payload);data.extend(bytes(sectors*4096-len(payload)))
        idx=key[0]%32+(key[1]%32)*32
        data[4*idx:4*idx+4]=off.to_bytes(3,'big')+bytes([sectors]);struct.pack_into('>I',data,4096+4*idx,int(time.time()))
    for idx in range(1024):
        key=(int(rp.name.split('.')[1])*32+idx%32,int(rp.name.split('.')[2])*32+idx//32)
        if key not in changed:
            assert data[4*idx:4*idx+4]==original[4*idx:4*idx+4]
            assert data[4096+4*idx:4096+4*idx+4]==original[4096+4*idx:4096+4*idx+4]
    assert data[8192:len(original)]==original[8192:], 'Existing chunk payload changed'
    staged=rp.with_suffix('.dinv-staged');staged.write_bytes(data)
    written.append((rp,staged,sha(original),sha(data)))
assert (world/'session.lock').read_bytes()==session, 'World was opened while staging'
for rp,staged,before,after in written:
    assert sha(rp.read_bytes())==before
    os.replace(staged,rp)
    assert sha(rp.read_bytes())==after
# Read every written chunk from disk, rather than trusting in-memory stage objects.
regions.clear();chunks.clear()
for p in placements:assert get_block(p['x'],p['y'],p['z'])==(blocks[p['block']],p['meta']),f"Disk verification failed {p}"
for rel,digest in protected.items():assert sha((world/rel).read_bytes())==digest,'Player/level file changed'
plan.update({'applied':True,'backup':str(backup),'regionHashes':[{'file':str(rp),'before':before,'after':after} for rp,_,before,after in written],'verification':'All block IDs/metadata reread from disk; previous block-entity/entity NBT and every other block cell preserved; existing chunk payloads retained; level/player files unchanged'})
receipt.write_text(json.dumps(plan,indent=2))
print('PASS offline station installation and disk verification; level, players and existing contents preserved')
