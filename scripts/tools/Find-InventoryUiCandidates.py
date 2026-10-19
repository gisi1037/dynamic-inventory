#!/usr/bin/env python3
"""Read-only structural scanner for Minecraft inventory UI classes in JAR files."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import struct
import sys
import zipfile
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

MAX_JAR_BYTES = 2 * 1024 * 1024 * 1024
MAX_CLASS_BYTES = 8 * 1024 * 1024
MAX_CLASS_ENTRIES = 250_000
MAX_CLASS_BYTES_PER_JAR = 512 * 1024 * 1024


class ClassFormatError(ValueError):
    pass


class Reader:
    def __init__(self, data: bytes):
        self.data = data
        self.offset = 0

    def take(self, size: int) -> bytes:
        if size < 0 or size > len(self.data) - self.offset:
            raise ClassFormatError("truncated class file")
        start = self.offset
        self.offset += size
        return self.data[start:self.offset]

    def u1(self) -> int:
        return self.take(1)[0]

    def u2(self) -> int:
        return struct.unpack(">H", self.take(2))[0]

    def u4(self) -> int:
        return struct.unpack(">I", self.take(4))[0]


def parse_class(data: bytes) -> dict:
    reader = Reader(data)
    if reader.u4() != 0xCAFEBABE:
        raise ClassFormatError("bad class magic")
    reader.u2()  # minor
    reader.u2()  # major
    count = reader.u2()
    cp: list[object | None] = [None] * count
    index = 1
    while index < count:
        tag = reader.u1()
        if tag == 1:  # CONSTANT_Utf8
            length = reader.u2()
            cp[index] = reader.take(length).decode("utf-8", "replace")
        elif tag in (3, 4):
            reader.take(4)
        elif tag in (5, 6):
            reader.take(8)
            index += 1
        elif tag == 7:  # CONSTANT_Class
            cp[index] = ("class", reader.u2())
        elif tag in (8, 16, 19, 20):
            reader.take(2)
        elif tag in (9, 10, 11):  # field/method/interface refs
            reader.take(2)
            cp[index] = ("member", reader.u2())
        elif tag == 12:  # CONSTANT_NameAndType
            cp[index] = ("name_type", reader.u2(), reader.u2())
        elif tag in (17, 18):
            reader.take(4)
        elif tag == 15:
            reader.take(3)
        else:
            raise ClassFormatError(f"unsupported constant-pool tag {tag}")
        index += 1

    def utf8(idx: int) -> str | None:
        value = cp[idx] if 0 < idx < len(cp) else None
        return value if isinstance(value, str) else None

    def class_name(idx: int) -> str | None:
        value = cp[idx] if 0 < idx < len(cp) else None
        if not isinstance(value, tuple) or value[0] != "class":
            return None
        name = utf8(value[1])
        return name.replace("/", ".") if name else None

    reader.u2()  # access flags
    this_name = class_name(reader.u2())
    super_index = reader.u2()
    super_name = class_name(super_index) if super_index else None
    interface_count = reader.u2()
    interfaces = [class_name(reader.u2()) for _ in range(interface_count)]

    method_names = set()
    for entry in cp:
        if isinstance(entry, tuple) and entry[0] == "member":
            nt_index = entry[1]
            nt = cp[nt_index] if 0 < nt_index < len(cp) else None
            if isinstance(nt, tuple) and nt[0] == "name_type":
                name = utf8(nt[1])
                if name:
                    method_names.add(name)

    # Use each constant-pool index because repeated CONSTANT_Class values are legal.
    class_refs = set()
    for cp_index, entry in enumerate(cp):
        if isinstance(entry, tuple) and entry[0] == "class":
            name = class_name(cp_index)
            if name:
                class_refs.add(name)
    if not this_name:
        raise ClassFormatError("missing this_class name")
    return {
        "className": this_name,
        "superName": super_name,
        "interfaces": [name for name in interfaces if name],
        "classRefs": class_refs,
        "methodNames": method_names,
    }


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def scan_jar(path: Path, display_path: str) -> tuple[list[dict], list[dict]]:
    errors: list[dict] = []
    records: list[dict] = []
    jar_size = path.stat().st_size
    if jar_size > MAX_JAR_BYTES:
        return records, [{"jar": display_path, "error": f"JAR exceeds {MAX_JAR_BYTES} byte safety limit"}]
    digest = sha256_file(path)
    try:
        with zipfile.ZipFile(path, "r") as archive:
            class_entries = [item for item in archive.infolist() if item.filename.endswith(".class")]
            if len(class_entries) > MAX_CLASS_ENTRIES:
                raise ClassFormatError(f"JAR contains more than {MAX_CLASS_ENTRIES} class entries")
            total_uncompressed = 0
            for item in class_entries:
                if item.file_size > MAX_CLASS_BYTES:
                    errors.append({"jar": display_path, "entry": item.filename, "error": "class entry exceeds size limit"})
                    continue
                total_uncompressed += item.file_size
                if total_uncompressed > MAX_CLASS_BYTES_PER_JAR:
                    errors.append({"jar": display_path, "error": "class entries exceed per-JAR uncompressed limit"})
                    break
                try:
                    data = archive.read(item)
                    parsed = parse_class(data)
                    parsed["jar"] = display_path
                    parsed["jarSha256"] = digest
                    records.append(parsed)
                except (OSError, RuntimeError, zipfile.BadZipFile, ClassFormatError) as exc:
                    errors.append({"jar": display_path, "entry": item.filename, "error": str(exc)[:256]})
    except (OSError, zipfile.BadZipFile, zipfile.LargeZipFile, ClassFormatError) as exc:
        errors.append({"jar": display_path, "error": str(exc)[:256]})
    return records, errors


def lineage(record: dict, by_name: dict[str, list[dict]], active: set[str], depth: int = 0) -> list[str]:
    if depth > 64:
        return []
    current = record["className"]
    if current in active:
        return []
    active = set(active)
    active.add(current)
    parent = record.get("superName")
    if not parent:
        return []
    if parent in ("net.minecraft.inventory.Container", "net.minecraft.client.gui.inventory.GuiContainer"):
        return [parent]
    for candidate in by_name.get(parent, []):
        found = lineage(candidate, by_name, active, depth + 1)
        if found:
            return [parent] + found
    return []


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mods-dir", required=True, type=Path, help="Directory of mod JARs to inspect recursively")
    parser.add_argument("--artifact", action="append", default=[], type=Path, help="Optional additional JAR; may be repeated")
    parser.add_argument("--json-out", required=True, type=Path, help="Write full candidate report JSON")
    parser.add_argument("--csv-out", type=Path, help="Optionally write candidate rows as CSV")
    parser.add_argument("--pack-target", default="unspecified", help="Label for the scanned environment")
    args = parser.parse_args()

    mods_dir = args.mods_dir.resolve()
    if not mods_dir.is_dir():
        parser.error(f"mods directory does not exist: {mods_dir}")
    paths: dict[str, tuple[Path, str]] = {}
    for jar in sorted(mods_dir.rglob("*.jar")):
        if jar.is_file():
            absolute = str(jar.resolve()).casefold()
            paths[absolute] = (jar.resolve(), str(jar.resolve().relative_to(mods_dir)))
    for artifact in args.artifact:
        resolved = artifact.resolve()
        if not resolved.is_file():
            parser.error(f"artifact JAR does not exist: {resolved}")
        paths[str(resolved).casefold()] = (resolved, str(resolved))

    all_records: list[dict] = []
    errors: list[dict] = []
    jar_hashes: dict[str, str] = {}
    for path, display in paths.values():
        records, jar_errors = scan_jar(path, display)
        all_records.extend(records)
        errors.extend(jar_errors)
        if records:
            jar_hashes[display] = records[0]["jarSha256"]

    unique_records = {}
    duplicate_class_definitions = 0
    for record in all_records:
        key = (record["jar"].casefold(), record["className"])
        if key in unique_records:
            duplicate_class_definitions += 1
            continue
        unique_records[key] = record
    all_records = list(unique_records.values())

    by_name: dict[str, list[dict]] = defaultdict(list)
    for record in all_records:
        by_name[record["className"]].append(record)

    candidates = []
    for record in all_records:
        refs = record["classRefs"]
        frameworks = []
        if any(name.startswith("com.gtnewhorizons.modularui.") for name in refs):
            frameworks.append("MUI1")
        if any(name.startswith("com.cleanroommc.modularui.") for name in refs):
            frameworks.append("MUI2")
        found_lineage = lineage(record, by_name, set())
        if found_lineage:
            frameworks.append("NATIVE")
        method_names = record["methodNames"]
        uses_player_builder = bool(
            "bindPlayerInventory" in method_names
            or ("playerInventory" in method_names and ("MUI1" in frameworks or "MUI2" in frameworks))
            or any(name.endswith("PlayerSlotGroup") for name in refs)
        )
        reasons = []
        if found_lineage:
            reasons.append("native superclass chain reaches " + " -> ".join(found_lineage))
        if "MUI1" in frameworks:
            reasons.append("constant pool references ModularUI1 classes")
        if "MUI2" in frameworks:
            reasons.append("constant pool references ModularUI2 classes")
        if uses_player_builder:
            reasons.append("constant pool references a player inventory builder/sync symbol")
        if not reasons:
            continue
        candidates.append({
            "className": record["className"],
            "jar": record["jar"],
            "jarSha256": record["jarSha256"],
            "superName": record["superName"],
            "framework": "+".join(frameworks) if frameworks else "REFERENCE_ONLY",
            "frameworks": frameworks,
            "usesPlayerBuilder": uses_player_builder,
            "candidate": True,
            "reasons": reasons,
            "nativeLineage": found_lineage,
        })

    candidates.sort(key=lambda item: (item["jar"].casefold(), item["className"].casefold()))
    report = {
        "schemaVersion": 1,
        "capturedAtUtc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "packTarget": args.pack_target,
        "scanner": "Find-InventoryUiCandidates.py (constant-pool/superclass parser; classes are not loaded)",
        "safetyLimits": {
            "maxJarBytes": MAX_JAR_BYTES,
            "maxClassBytes": MAX_CLASS_BYTES,
            "maxClassEntriesPerJar": MAX_CLASS_ENTRIES,
            "maxUncompressedClassBytesPerJar": MAX_CLASS_BYTES_PER_JAR,
        },
        "jarCount": len(paths),
        "classCount": len(all_records),
        "duplicateClassDefinitionsSkipped": duplicate_class_definitions,
        "candidateCount": len(candidates),
        "jarHashes": jar_hashes,
        "candidates": candidates,
        "scanErrors": errors,
        "interpretation": "Structural leads only. This scan does not establish slot mapping, transfer safety, rendering compatibility, runtime behavior, or permission to register/activate an adapter.",
    }
    args.json_out.parent.mkdir(parents=True, exist_ok=True)
    args.json_out.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    if args.csv_out:
        args.csv_out.parent.mkdir(parents=True, exist_ok=True)
        with args.csv_out.open("w", newline="", encoding="utf-8-sig") as stream:
            writer = csv.DictWriter(stream, fieldnames=(
                "className", "jar", "jarSha256", "superName", "framework", "usesPlayerBuilder", "candidate", "reasons", "nativeLineage"
            ), extrasaction="ignore")
            writer.writeheader()
            for row in candidates:
                exported = dict(row)
                exported["reasons"] = "; ".join(row["reasons"])
                exported["nativeLineage"] = " -> ".join(row["nativeLineage"])
                writer.writerow(exported)
    print(f"Scanned {len(paths)} JARs and {len(all_records)} classes; found {len(candidates)} structural candidates.")
    print(f"JSON report: {args.json_out}")
    if args.csv_out:
        print(f"CSV report: {args.csv_out}")
    print(f"Read errors: {len(errors)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
