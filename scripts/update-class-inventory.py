"""Document every production Kotlin file and named class, without building Android."""
from pathlib import Path
from datetime import date
import re

root = Path(__file__).resolve().parents[1]
files = sorted(path for variant in ("main", "debug", "release") for path in (root / "app/src" / variant / "java").rglob("*.kt"))
declaration = re.compile(r"^\s*(?:(?:private|internal|public|data|sealed|open|abstract|enum|annotation|value|fun)\s+)*(?:class|interface|object)\s+(\w+)", re.MULTILINE)
roles = {
    "data": "Локальні дані, профілі, транзакції, міграції або каталог",
    "domain": "Чисті правила SRS та лічильників",
    "sync": "Протокол, транспорт, mapping або фонове відправлення",
    "viewmodel": "Feature presentation, навігація або retained owner",
    "billing": "Play Billing, локальна перевірка підпису та статус покупки",
    "privacy": "UMP, приватність і відновлюване клієнтське видалення",
    "auth": "Credential Manager / Firebase Auth / локальний fallback",
    "reminders": "Планування, policy, профіль і показ нагадувань",
    "ads": "Життєвий цикл AdMob та конфігурація unit ID",
    "telemetry": "Продуктові події та діагностика без тексту відповідей",
    "util": "Навчальні/текстові допоміжні функції або звук",
    "ui": "Compose UI, адаптація, тема або accessibility",
}
lines = ["# Реєстр класів і файлів після змін", "", f"Оновлено: {date.today().isoformat()}.", "",
    "До реєстру включено всі production Kotlin-файли main/debug/release, зокрема класи без змін. Перевірка синтаксису охоплює кожен файл. Цей перелік не є підтвердженням компіляції, роботи кожного методу або перевірки на пристрої.", "",
    "| Файл | Іменовані класи / interfaces / objects | Відповідальність |", "|---|---|---|"]
count = 0
for path in files:
    source = path.read_text(encoding="utf-8-sig")
    names = declaration.findall(source)
    count += len(names)
    relative = path.relative_to(root).as_posix()
    package_part = relative.split("/com/example/")[-1].split("/")[0]
    role = roles.get(package_part, "Application lifecycle, composition root або build-specific App Check provider")
    lines.append(f"| `{relative}` | {', '.join(names) or 'Top-level functions / declarations'} | {role} |")
lines += ["", f"Разом: **{len(files)} production Kotlin-файлів, {count} іменованих оголошень класів/interfaces/objects**.", "",
    "Залишені UI-компоненти та policy класи не розбивалися штучно, коли їхня відповідальність уже була вузькою. Значні блоки виділено з AppViewModel, WordRepository, ProgressSyncRepository, SettingsScreen, LearningSessionScreen та SoundFeedbackManager.", "",
    "Окремо перевіряються файли app/src/test та app/src/androidTest. Виконані збірки, Room schema export, результати unit/device тестів і невиконані сценарії наведено в [release-verification.md](release-verification.md).", ""]
(root / "docs/architecture-class-inventory.md").write_text("\n".join(lines), encoding="utf-8")
print(f"Inventory: {len(files)} files, {count} named declarations")
