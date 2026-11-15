"""Keep the Firestore catalog allowlist append-only, including retired stable keys."""
from pathlib import Path
import json
import re

root = Path(__file__).resolve().parents[1]
path = root / "firestore.rules"
rules = path.read_text(encoding="utf-8")
words = json.loads((root / "app/src/main/assets/words.json").read_text(encoding="utf-8"))
start = rules.index("      return key in [")
end = rules.index("      ];", start) + len("      ];")
existing = set(re.findall(r"v1_[0-9a-f]{64}", rules[start:end]))
keys = sorted(existing | {word["key"] for word in words})
replacement = "      return key in [\n        " + ",\n        ".join(repr(key) for key in keys) + "\n      ];"
path.write_text(rules[:start] + replacement + rules[end:], encoding="utf-8")
(root / "functions/progress-catalog.json").write_text(json.dumps(keys, indent=2) + "\n", encoding="utf-8")
print(f"Catalog allowlist: {len(keys)} keys, retained {len(existing)} previous keys")
