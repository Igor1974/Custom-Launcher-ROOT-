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

CHANGELOG_TEXT="DeepNight v$VERSION_NAME (Build $VERSION_CODE)
- Новая игра: Limbo! Теперь знаменитая игра-головоломка доступна прямо в вашем лаунчере. Просто выберите иконку на главном экране, чтобы погрузиться в атмосферу игры. Никаких дополнительных скачиваний — все уже внутри!
- Улучшения и оптимизация: Мы повысили общую скорость работы интерфейса и стабильность системы, чтобы лаунчер работал максимально плавно на любом устройстве."

# 4. Поиск APK
APK_PATH=$(find app -name "*release*.apk" -printf '%T@ %p\n' | sort -n | tail -1 | cut -f2- -d" ")

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}Ошибка: APK не найден! Сначала выполните: ./gradlew assembleRelease${NC}"
    exit 1
fi

# Проверяем, не забыл ли ты сделать билд перед релизом
APK_TIME=$(stat -c %Y "$APK_PATH")
NOW_TIME=$(date +%s)
if [ $((NOW_TIME - APK_TIME)) -gt 600 ]; then
    echo -e "${RED}Предупреждение: Найденный APK старше 10 минут! Возможно, ты забыл пересобрать проект.${NC}"
    read -p "Продолжить со старым APK? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
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
git add "$UPDATE_JSON" release.sh
git commit -m "Update release info v$VERSION_NAME ($VERSION_CODE)"

# Убираем жесткий --force, заменяя его на безопасный pull перед отправкой
git pull origin main --rebase
git push origin main

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
    echo -e "    РЕЛИЗ v$VERSION_NAME УСПЕШНО ЗАВЕРШЕН!"
    echo -e "======================================${NC}"
else
    echo -e "${RED}Произошла ошибка при создании релиза.${NC}"
fi
