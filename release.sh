#!/bin/bash

# Цвета для терминала
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}=== DeepNight Launcher Release Tool (Multi-ABI) ===${NC}"

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

echo -e "Готовим мульти-релиз: ${GREEN}v$VERSION_NAME ($VERSION_CODE)${NC}"

CHANGELOG_TEXT="Список изменений v$VERSION_NAME (Build $VERSION_CODE) - Lite & Smart:
Производительность: Концепция «Lite Start» — макс. легкий запуск без фоновой нагрузки.
Обучение: Новый 9-шаговый Мастер Настройки (подробно про Root, AOT, DSP) с отложенным запросом суперпользователя.
Реактивность: Мгновенное обновление статусбара (погода, CPU, RAM) при переключении настроек без перезапуска.
Интерфейс: Улучшена боковая панель с всплывающими подсказками, вызовом ИИ и проверкой PRO.
Настройки: Добавлен тумблер «Автозапуск при загрузке» и уточнен диалог TorrServe.
PRO: Нейросетевая озвучка ElevenLabs, память ИИ, DSP-движок и 4K обои.
Стабильность: Оптимизирована работа на слабых ТВ и проекторах с 1ГБ RAM."

# 4. Поиск APK для разных архитектур
APK_ARM=$(find app/build/outputs/apk/release -name "*armeabi-v7a-release.apk" | head -n 1)
APK_ARM64=$(find app/build/outputs/apk/release -name "*arm64-v8a-release.apk" | head -n 1)
APK_UNIVERSAL=$(find app/build/outputs/apk/release -name "*universal-release.apk" | head -n 1)

if [ -z "$APK_UNIVERSAL" ]; then
    # Fallback if universal is disabled
    APK_UNIVERSAL=$(find app -name "*release*.apk" -printf '%T@ %p\n' | sort -n | tail -1 | cut -f2- -d" ")
fi

if [ -z "$APK_ARM" ] && [ -z "$APK_ARM64" ] && [ -z "$APK_UNIVERSAL" ]; then
    echo -e "${RED}Ошибка: APK не найдены! Сначала выполните: ./gradlew assembleRelease${NC}"
    exit 1
fi

# Проверка свежести файлов (если есть хотя бы один файл)
if [ -n "$APK_UNIVERSAL" ] && [ -f "$APK_UNIVERSAL" ]; then
    APK_TIME=$(stat -c %Y "$APK_UNIVERSAL")
    NOW_TIME=$(date +%s)
    if [ $((NOW_TIME - APK_TIME)) -gt 600 ]; then
        echo -e "${RED}Предупреждение: APK старше 10 минут!${NC}"
        read -p "Продолжить со старыми файлами? (y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
fi

echo -e "Найдены файлы:"
[ -f "$APK_ARM" ] && echo -e "- ARM (32bit): ${GREEN}$(basename "$APK_ARM")${NC}"
[ -f "$APK_ARM64" ] && echo -e "- ARM64 (64bit): ${GREEN}$(basename "$APK_ARM64")${NC}"
[ -f "$APK_UNIVERSAL" ] && echo -e "- Universal: ${GREEN}$(basename "$APK_UNIVERSAL")${NC}"

# 5. Обновление update.json (всегда используем Universal для авто-обновления)
UPDATE_JSON="update.json"
if [ ! -f "$UPDATE_JSON" ]; then
    echo '{"versionCode": 0, "versionName": "0", "link": "", "changelog": ""}' > "$UPDATE_JSON"
fi

DOWNLOAD_URL="https://github.com/Igor1974/Custom-Launcher-ROOT-/releases/download/v$VERSION_NAME"
if [ -n "$APK_UNIVERSAL" ]; then
    APK_UNIVERSAL_FILENAME=$(basename "$APK_UNIVERSAL")
    DOWNLOAD_URL="$DOWNLOAD_URL/$APK_UNIVERSAL_FILENAME"
fi

echo -e "${BLUE}Обновляю $UPDATE_JSON (ссылка на Universal)...${NC}"
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
git add -A
git commit -m "Prepare release v$VERSION_NAME ($VERSION_CODE) - AI Studio Update"
git pull origin main --rebase
git push origin main

# 7. Создание релиза на GitHub
TAG="v$VERSION_NAME"
echo -e "${BLUE}Публикация релиза $TAG в GitHub...${NC}"

if gh release view "$TAG" &>/dev/null; then
    echo "Релиз $TAG уже существует. Перезаписываю..."
    gh release delete "$TAG" --yes
    git push --delete origin "$TAG" 2>/dev/null
    sleep 3 # Даем GitHub время на удаление
fi

# Собираем список файлов для загрузки через массив (самый надежный способ в bash)
files=()
[ -f "$APK_ARM" ] && files+=("$APK_ARM")
[ -f "$APK_ARM64" ] && files+=("$APK_ARM64")
[ -f "$APK_UNIVERSAL" ] && files+=("$APK_UNIVERSAL")

echo "Загружаю файлы: ${files[*]}"

gh release create "$TAG" "${files[@]}" \
    --title "Release $VERSION_NAME - AI Studio" \
    --notes "$CHANGELOG_TEXT" \
    --target main

# shellcheck disable=SC2181
if [ $? -eq 0 ]; then
    echo -e "\n${GREEN}======================================"
    echo -e "    РЕЛИЗ v$VERSION_NAME (AI STUDIO) УСПЕШНО ЗАВЕРШЕН!"
    echo -e "======================================${NC}"
else
    echo -e "${RED}Произошла ошибка при создании релиза.${NC}"
fi
