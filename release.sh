#!/bin/bash

# Цвета для терминала
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}=== DeepNight Launcher Release Tool ===${NC}"

# 1. Проверка gh CLI
if ! command -v gh &> /dev/null; then
    echo -e "${RED}Ошибка: GitHub CLI (gh) не установлен.${NC}"
    echo "Установите его: sudo apt install gh && gh auth login"
    exit 1
fi

# 2. Читаем версию из build.gradle.kts
VERSION_NAME=$(grep "val appVersionName =" app/build.gradle.kts | cut -d '"' -f 2)
VERSION_CODE=$(grep "val appVersionCode =" app/build.gradle.kts | awk '{print $4}')

if [ -z "$VERSION_NAME" ]; then
    echo -e "${RED}Ошибка: Не удалось прочитать версию из build.gradle.kts${NC}"
    exit 1
fi

echo -e "Готовим релиз: ${GREEN}v$VERSION_NAME ($VERSION_CODE)${NC}"

# 3. Список изменений
CHANGELOG_TEXT="Hotfix v5.2.1 (Update 618):
- Добавлено визуальное уведомление 'Установка...' при обновлении через Root.
- Перенесен путь загрузки APK в публичную папку для стабильной работы на Android TV.
- Исправлена критическая ошибка в механизме автообновления (suspend function call)."

# 4. Поиск APK
APK_PATH=$(find app -name "*release*.apk" -printf '%T@ %p\n' | sort -n | tail -1 | cut -f2- -d" ")

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}Ошибка: APK не найден! Сначала выполните: ./gradlew assembleRelease${NC}"
    exit 1
fi

APK_FILENAME=$(basename "$APK_PATH")
echo -e "Найден файл: ${GREEN}$APK_FILENAME${NC}"

# 5. Обновление update.json
UPDATE_JSON="update.json"
if [ ! -f "$UPDATE_JSON" ]; then
    echo '{"versionCode": 0, "versionName": "0", "link": "", "changelog": ""}' > "$UPDATE_JSON"
fi

echo -e "${BLUE}Обновляю $UPDATE_JSON...${NC}"
DOWNLOAD_URL="https://github.com/Igor1974/Custom-Launcher-ROOT-/releases/download/v$VERSION_NAME/$APK_FILENAME"

# Передаем чейнджлог в python через переменную окружения для избежания проблем с кавычками
export CHANGELOG_ENV="$CHANGELOG_TEXT"
python3 -c "
import json, os
with open('$UPDATE_JSON', 'r') as f:
    data = json.load(f)
data['versionCode'] = $VERSION_CODE
data['versionName'] = '$VERSION_NAME'
data['link'] = '$DOWNLOAD_URL'
data['changelog'] = os.environ.get('CHANGELOG_ENV', '')
with open('$UPDATE_JSON', 'w') as f:
    json.dump(data, f, indent=4, ensure_ascii=False)
"

# 6. Git commit & push
echo -e "${BLUE}Синхронизация с Git...${NC}"
git add -f "$UPDATE_JSON" app/build.gradle.kts release.sh \
    app/proguard-rules.pro \
    app/src/main/AndroidManifest.xml \
    app/src/main/java/com/deepnight/launcher/MainActivity.kt \
    app/src/main/java/com/deepnight/launcher/SystemInfoRepository.kt \
    app/src/main/java/com/deepnight/launcher/radio/RadioScreen.kt
git commit -m "Release v$VERSION_NAME ($VERSION_CODE)"
git push origin main --force

# 7. Создание релиза на GitHub
TAG="v$VERSION_NAME"
echo -e "${BLUE}Публикация релиза $TAG в GitHub...${NC}"

if gh release view "$TAG" &>/dev/null; then
    echo "Релиз $TAG уже существует. Перезаписываю..."
    gh release delete "$TAG" --yes
    git push --delete origin "$TAG" 2>/dev/null
fi

gh release create "$TAG" "$APK_PATH" \
    --title "Release $VERSION_NAME" \
    --notes "$CHANGELOG_TEXT" \
    --target main

if [ $? -eq 0 ]; then
    echo -e "\n${GREEN}======================================"
    echo -e "   РЕЛИЗ v$VERSION_NAME УСПЕШНО ЗАВЕРШЕН!"
    echo -e "======================================${NC}"
else
    echo -e "${RED}Произошла ошибка при создании релиза.${NC}"
fi
