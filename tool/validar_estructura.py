from pathlib import Path
import json, xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
required = [
    'pubspec.yaml', 'lib/main.dart', 'lib/app.dart',
    'android/settings.gradle.kts', 'android/build.gradle.kts',
    'android/app/build.gradle.kts', 'android/app/src/main/AndroidManifest.xml',
    'android/app/src/main/kotlin/com/example/monitoreodeplagas/MainActivity.kt',
    '.vscode/launch.json', '.vscode/tasks.json'
]
missing = [x for x in required if not (root/x).exists()]
if missing:
    raise SystemExit('FALTAN ARCHIVOS: ' + ', '.join(missing))

ET.parse(root/'android/app/src/main/AndroidManifest.xml')
json.load(open(root/'.vscode/launch.json', encoding='utf-8'))
json.load(open(root/'.vscode/tasks.json', encoding='utf-8'))

# Verifica imports relativos de Dart.
errors = []
for p in (root/'lib').rglob('*.dart'):
    for line in p.read_text(encoding='utf-8').splitlines():
        s=line.strip()
        if s.startswith("import '") and 'package:' not in s and 'dart:' not in s:
            rel=s.split("'")[1]
            if not (p.parent/rel).resolve().exists():
                errors.append(f'{p.relative_to(root)} -> {rel}')
if errors:
    raise SystemExit('IMPORTS DART ROTOS:\n' + '\n'.join(errors))
print('OK: estructura Android/Flutter/VS Code completa y referencias locales válidas.')
