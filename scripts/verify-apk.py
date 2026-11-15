"""Check class definitions in the actual APK, including generated Room classes.

Usage: python scripts/verify-apk.py app/build/outputs/apk/debug/app-debug.apk
Uses only the standard library; does not infer success from compiler output.
"""
import argparse
import hashlib
from pathlib import Path
import struct
import zipfile


def defined_classes(dex):
    assert dex.startswith(b"dex\n"), "Unexpected DEX header"
    uint = lambda offset: struct.unpack_from("<I", dex, offset)[0]
    strings_offset = uint(60)
    types_offset = uint(68)
    count, definitions_offset = uint(96), uint(100)
    result = set()
    for index in range(count):
        type_index = uint(definitions_offset + index * 32)
        string_index = uint(types_offset + type_index * 4)
        offset = uint(strings_offset + string_index * 4)
        # Skip the ULEB128 UTF-16 length before the MUTF-8 descriptor.
        while dex[offset] & 0x80:
            offset += 1
        offset += 1
        result.add(dex[offset:dex.index(b"\0", offset)].decode("utf-8"))
    return result


def verify(path, project_dex_dir=None):
    with zipfile.ZipFile(path) as apk:
        dex_files = [name for name in apk.namelist() if name.endswith(".dex")]
        classes = set().union(*(defined_classes(apk.read(name)) for name in dex_files))
        if project_dex_dir is not None:
            expected = list(project_dex_dir.rglob("*.dex"))
            assert expected, f"No merged project DEX files in {project_dex_dir}"
            packaged = {hashlib.sha256(apk.read(name)).digest() for name in dex_files}
            missing_dex = [str(p.relative_to(project_dex_dir)) for p in expected
                           if hashlib.sha256(p.read_bytes()).digest() not in packaged]
            assert not missing_dex, f"APK contains stale project DEX: missing current buckets {missing_dex}"
    required = {
        "NaholosyApplication", "MainActivity", "AppContainer",
        "data/AppDatabase_Impl", "data/WordDao_Impl", "data/LocalSyncDao_Impl",
        "data/SessionRepository", "domain/SrsPolicy", "viewmodel/AppViewModel",
        "viewmodel/SessionViewModel", "sync/ProgressSyncWorker",
    }
    missing = sorted(name for name in required if f"Lcom/example/{name};" not in classes)
    assert not missing, f"Missing APK class definitions: {', '.join(missing)}"
    print(f"PASS APK: {len(dex_files)} DEX files, {len(classes)} class definitions; all {len(required)} entry/domain/Room classes present")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--project-dex-dir", type=Path)
    args = parser.parse_args()
    verify(args.apk, args.project_dex_dir)
