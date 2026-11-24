"""Run standalone pack checks against this checkout's offline 2.9.1 Gradle classpath."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


def run(manifest, mods):
    root = Path(__file__).resolve().parents[2]
    launch = json.loads(manifest.read_text(encoding="utf-8-sig"))
    if launch.get("offlineOnly") is not True or launch.get("profile") != "gtnh-2.9.1":
        raise ValueError("Expected the offline GTNH 2.9.1 fixture manifest")
    output = root / "build"
    compiled = output / "classes/java/packTest"
    if not launch.get("packTestClasspath"):
        raise ValueError("Regenerate writeFixtureClientLaunch with -PinventoryStorageTests")
    # These JVMs run deobfuscated classes directly, before FML's LWJGL3 transformer.
    # Prefer the Minecraft compile APIs (including LWJGL2's DisplayMode constructor).
    entries = (launch["packTestClasspath"] + os.pathsep + launch["classpath"]).split(os.pathsep)
    classpath = list(dict.fromkeys(Path(entry).resolve() for entry in entries if entry))
    if compiled not in classpath:
        raise ValueError("Export writeFixtureClientLaunch with -PinventoryStorageTests in this checkout")
    classpath = [output / "classes/java/main", output / "resources/main", compiled, *classpath]
    arguments = {"IntegrationProfileFixtures": ["gtnh-2.9.1", "2.9.1", "1.8.18", "true", "true"]}
    # PackCompatibilityFixtures requires the mutable Forge Loader in test-integration-profiles.ps1.
    skip = {"PackCompatibilityFixtures"}
    if mods:
        jars = []
        for prefix in ("enderio-", "extrautilities-", "forestry-", "railcraft-", "thaumcraft-1.7.10-"):
            matches = [p for p in mods.glob("*.jar") if p.name.lower().startswith(prefix)]
            if len(matches) != 1:
                raise ValueError(f"Expected one {prefix} jar in {mods}")
            jars.append(str(matches[0].resolve()))
        arguments["NativeBatchFixtures"] = jars
    else:
        skip.add("NativeBatchFixtures")  # Requires the two stock pack jars.
    sources = root / "src/packTest/java"
    count = 0
    with tempfile.TemporaryDirectory(prefix="dynamicinventory-pack-") as temp:
        directory = Path(temp)
        args_file = directory / "classpath.args"
        # Java reads the classpath from a file so a full Windows pack also fits.
        paths = os.pathsep.join(p.as_posix() for p in classpath)
        args_file.write_text('-cp\n"' + paths + '"\n', encoding="utf-8")
        for source in sorted(sources.rglob("*Fixtures.java")):
            if source.stem in skip or not re.search(r"public static void main\s*\(", source.read_text(encoding="utf-8")):
                continue
            main = ".".join(source.relative_to(sources).with_suffix("").parts)
            work = directory / source.stem
            work.mkdir()
            print(f"Running {main}", flush=True)
            subprocess.run([launch["executable"], "@" + str(args_file), "-Djava.awt.headless=true",
                            "-Ddynamicinventory.profile=gtnh-2.9.1", main, *arguments.get(source.stem, [])],
                           cwd=work, check=True, timeout=120)
            count += 1
    if not count:
        raise RuntimeError("No standalone fixtures found")
    print(f"Passed {count} GTNH 2.9.1 fixtures")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--mods", type=Path, help="Include the native host check against stock pack jars")
    args = parser.parse_args()
    run(args.manifest, args.mods)
