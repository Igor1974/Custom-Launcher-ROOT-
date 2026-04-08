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

# 3. Поиск APK
# Ищем последний измененный .apk в папке app, который относится к релизу
APK_PATH=$(find app -name "*release*.apk" -printf '%T@ %p\n' | sort -n | tail -1 | cut -f2- -d" ")

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}Ошибка: APK не найден!${NC}"
    echo "Я искал в папке 'app' файлы, содержащие 'release' и '.apk'"
    echo "Убедитесь, что вы собрали APK: Build -> Generate Signed Bundle / APK"
    exit 1
fi

APK_FILENAME=$(basename "$APK_PATH")
echo -e "Найден файл: ${GREEN}$APK_FILENAME${NC}"

# 4. Обновление update.json
UPDATE_JSON="update.json"
# Если файла нет, создаем базовый
if [ ! -f "$UPDATE_JSON" ]; then
    echo -e "${BLUE}Создаю новый $UPDATE_JSON...${NC}"
    echo '{"versionCode": 0, "versionName": "0", "link": "", "changelog": ""}' > "$UPDATE_JSON"
fi

echo -e "${BLUE}Обновляю $UPDATE_JSON...${NC}"
DOWNLOAD_URL="https://github.com/Igor1974/Custom-Launcher-ROOT-/releases/download/v$VERSION_NAME/$APK_FILENAME"

# Используем временный файл для чистой замены через python (так надежнее с JSON)
python3 -c "
import json
with open('$UPDATE_JSON', 'r') as f:
    data = json.load(f)
data['versionCode'] = $VERSION_CODE
data['versionName'] = '$VERSION_NAME'
data['link'] = '$DOWNLOAD_URL'
data['changelog'] = '1. Исправлена ошибка WorkerStoppedException в WallpaperWorker.\n2. Добавлены Accept заголовки для загрузки ИИ-обоев.\n3. Нормализованы постеры через TMDB Mirror для всех источников.\n4. Исправлена потеря фокуса в поиске при обновлении.\n5. Восстановлен marquee-скроллинг названий.\n6. Оптимизированы отступы UI (устранено перекрытие поисковой строки).\n7. Исправлена локализация погоды и AI-промптов.\n8. Версия в AboutDialog теперь обновляется автоматически.'
with open('$UPDATE_JSON', 'w') as f:
    json.dump(data, f, indent=4)
"

# 5. Git commit & push
echo -e "${BLUE}Синхронизация с Git (только update.json)...${NC}"
git add "$UPDATE_JSON" .gitignore release.sh
git commit -m "Update OTA info v$VERSION_NAME ($VERSION_CODE)"
git push origin main --force

# 6. Создание релиза на GitHub
TAG="v$VERSION_NAME"
echo -e "${BLUE}Публикация релиза $TAG в GitHub...${NC}"

# Проверяем, существует ли уже такой тег, если да - удаляем (перезапись)
if gh release view "$TAG" &>/dev/null; then
    echo "Релиз $TAG уже существует. Перезаписываю..."
    gh release delete "$TAG" --yes
    git push --delete origin "$TAG" 2>/dev/null
fi

gh release create "$TAG" "$APK_PATH" \
    --title "Release $VERSION_NAME" \
    --notes "Автоматическое обновление списка приложений, OTA и оптимизация интерфейса." \
    --target main

if [ $? -eq 0 ]; then
    echo -e "\n${GREEN}======================================"
    echo -e "   РЕЛИЗ v$VERSION_NAME УСПЕШНО ЗАВЕРШЕН!"
    echo -e "======================================${NC}"
    echo "Файл $UPDATE_JSON обновлен и отправлен."
    echo "APK загружен в релизы."
else
    echo -e "${RED}Произошла ошибка при создании релиза.${NC}"
fi
