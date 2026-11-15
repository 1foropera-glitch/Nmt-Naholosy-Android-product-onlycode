"""Read-only source checks; never invokes Gradle, an Android build, or a device."""
from pathlib import Path
import argparse
import base64
import json
import os
import re
import sqlite3
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def check_catalog():
    words = json.loads((ROOT / "app/src/main/assets/words.json").read_text(encoding="utf-8-sig"))
    keys = [word["key"] for word in words]
    assert len(keys) == len(set(keys))
    rules = (ROOT / "firestore.rules").read_text(encoding="utf-8")
    backend_keys = set(json.loads((ROOT / "functions/progress-catalog.json").read_text(encoding="utf-8")))
    assert backend_keys == set(re.findall(r"v1_[a-f0-9]{64}", rules)), "Backend/rules catalogs differ"
    for word in words:
        assert re.fullmatch(r"v1_[a-f0-9]{64}", word["key"])
        assert word["key"] in rules
        assert word["stress_positions"]
        assert all(0 <= pos < len(word["plain"]) for pos in word["stress_positions"])
    print(f"PASS catalog: {len(words)} unique stable keys, backend allowlist matches retained rules catalog")


def check_migration_sql():
    db = sqlite3.connect(":memory:")
    db.executescript("""
      CREATE TABLE words (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, stressed TEXT NOT NULL,
        plain TEXT NOT NULL, normalized TEXT NOT NULL, stressPositions TEXT NOT NULL, letter TEXT NOT NULL,
        note TEXT, source TEXT NOT NULL);
      CREATE TABLE word_states (wordId INTEGER NOT NULL PRIMARY KEY, box INTEGER NOT NULL, dueAt INTEGER,
        status TEXT NOT NULL, correctCount INTEGER NOT NULL, wrongCount INTEGER NOT NULL,
        lapseCount INTEGER NOT NULL, lastReviewedAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, revision INTEGER NOT NULL);
      INSERT INTO words VALUES(7, 'test', 'test', 'test', '1', 'T', NULL, 'test');
      INSERT INTO word_states VALUES(7, 3, 1234, 'learning', 5, 2, 2, 1000, 1000, 7);
    """)
    source = (ROOT / "app/src/main/java/com/example/data/DatabaseMigrations.kt").read_text(encoding="utf-8-sig")
    statements = re.findall(r'db\.execSQL\(("""[\s\S]*?"""|"[^"\n]*")\)', source)
    assert len(statements) >= 9
    for statement in statements:
        db.execute(statement[3:-3] if statement.startswith('"""') else statement[1:-1])
    assert db.execute("SELECT profileId, box, correctCount, wrongCount, revision FROM word_states WHERE wordId = 7").fetchone() == ("legacy", 3, 5, 2, 7)
    # Composite identity allows two independent profiles for the same dictionary word.
    db.execute("INSERT INTO word_states SELECT wordId, box, dueAt, status, correctCount, wrongCount, lapseCount, lastReviewedAt, updatedAt, revision, 'other', deviceId, countersJson FROM word_states WHERE profileId = 'legacy'")
    assert db.execute("SELECT COUNT(*) FROM word_states WHERE wordId = 7").fetchone()[0] == 2
    db.execute("INSERT INTO sync_outbox VALUES('legacy', 7, 8)")
    db.execute("DELETE FROM sync_outbox WHERE profileId = 'legacy' AND wordId = 7 AND revision = 7")
    assert db.execute("SELECT COUNT(*) FROM sync_outbox").fetchone()[0] == 1
    print("PASS migration SQL: legacy progress preserved, profiles isolated, stale acknowledgement rejected")


def check_resources():
    for path in (ROOT / "app/src/main/res/font").glob("*.ttf"):
        assert path.read_bytes()[:4] in (b"\x00\x01\x00\x00", b"OTTO", b"ttcf"), f"Invalid Android font container: {path.name}"
    for path in (ROOT / "app/src/main").rglob("*.xml"):
        ET.parse(path)
    for path in [ROOT / "firebase.json", ROOT / "firestore.indexes.json", ROOT / "functions/package.json"]:
        json.loads(path.read_text(encoding="utf-8-sig"))
    source = "\n".join(path.read_text(encoding="utf-8-sig") for path in (ROOT / "app/src/main/java").rglob("*.kt"))
    assert "setPremiumForDev" not in source and "devTapCount" not in source
    assert "emoji_fire" not in source and "updateStreak" not in source
    bands = json.loads((ROOT / "app/src/main/assets/feedback-bands.json").read_text(encoding="utf-8"))
    assert len(bands) == 15
    assert all(len(base64.b64decode(band["iq"], validate=True)) % 4 == 0 and band["hopSamples"] > 0 for band in bands)
    print("PASS XML/JSON and removed development/streak paths")


def check_kotlin(parser_path):
    if parser_path:
        sys.path.insert(0, parser_path)
    from tree_sitter import Language, Parser
    import tree_sitter_kotlin
    parser = Parser(Language(tree_sitter_kotlin.language()))
    paths = list((ROOT / "app/src").rglob("*.kt")) + [ROOT / "app/build.gradle.kts", ROOT / "build.gradle.kts", ROOT / "settings.gradle.kts"]
    errors = []
    for path in paths:
        data = path.read_bytes()
        tree = parser.parse(data)
        stack = [tree.root_node]
        while stack:
            node = stack.pop()
            if node.type == "ERROR" or node.is_missing:
                errors.append(f"{path.relative_to(ROOT)}:{node.start_point.row + 1}: {node.type}")
            stack.extend(node.children)
    assert not errors, "\n".join(errors)
    print(f"PASS Kotlin grammar: {len(paths)} files (syntax only, not type checking)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--parser-path")
    parser.add_argument("--skip-kotlin", action="store_true")
    args = parser.parse_args()
    check_catalog()
    check_migration_sql()
    check_resources()
    if not args.skip_kotlin:
        check_kotlin(args.parser_path)
