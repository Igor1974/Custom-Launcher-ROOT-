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

CHANGELOG_TEXT="Список изменений v$VERSION_NAME (Build $VERSION_CODE):
• 🧠 Интеллектуальный апгрейд: Полностью переписана архитектура ИИ-модуля. Теперь поддержка нейросетей (Gemini 3.1, DeepSeek, Groq, Mistral) стала модульной, быстрой и независимой.
• 🎬 Dolby Vision Profile 7: Добавлен второй движок воспроизведения — Google Media3 (ExoPlayer). Теперь лаунчер поддерживает динамические метаданные Dolby Vision в MKV, обеспечивая эталонную цветопередачу в тяжелых 4K рипах.
• ⚡ Мгновенные новинки: Оптимизирован алгоритм поиска фильмов и сериалов. Категории теперь загружаются параллельно, что сократило время ожидания в несколько раз.
• 🗜 Оптимизация веса: Перешли на Multi-ABI сборку. Основной «вес» приложения для пользователя снижен со 107 МБ до 37-39 МБ за счет исключения лишних библиотек под чужие процессоры.
• 📺 Стабильность ТВ: Исправлены проблемы с «черным экраном» в превью каналов и устранено растягивание изображения. Теперь режим «Оригинал» корректно вписывает видео в экран.
• 🛡 Безопасность: Личные ключи ИИ вынесены из кода, что делает лаунчер готовым к открытой публикации.
• 🕹 Управление: Доработана логика кнопок «Назад» и «Домой» — теперь случайный выход из лаунчера исключен."

# 4. Поиск APK для разных архитектур
APK_ARM=$(find app/build/outputs/apk/release -name "*armeabi-v7a-release.apk" | head -n 1)
APK_ARM64=$(find app/build/outputs/apk/release -name "*arm64-v8a-release.apk" | head -n 1)
APK_UNIVERSAL=$(find app/build/outputs/apk/release -name "*universal-release.apk" | head -n 1)

if [ -z "$APK_UNIVERSAL" ]; then
    # Fallback if universal is disabled
    APK_UNIVERSAL=$(find app -name "*release*.apk" -printf '%T@ %p\n' | sort -n | tail -1 | cut -f2- -d" ")
fi

if [ -z "$APK_ARM" ] && [ -z "$APK_ARM64" ]; then
    echo -e "${RED}Ошибка: APK не найдены! Сначала выполните: ./gradlew assembleRelease${NC}"
    exit 1
fi

# Проверка свежести файлов
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

echo -e "Найдены файлы:"
[ -f "$APK_ARM" ] && echo -e "- ARM (32bit): ${GREEN}$(basename "$APK_ARM")${NC}"
[ -f "$APK_ARM64" ] && echo -e "- ARM64 (64bit): ${GREEN}$(basename "$APK_ARM64")${NC}"
[ -f "$APK_UNIVERSAL" ] && echo -e "- Universal: ${GREEN}$(basename "$APK_UNIVERSAL")${NC}"

# 5. Обновление update.json (всегда используем Universal для авто-обновления)
UPDATE_JSON="update.json"
if [ ! -f "$UPDATE_JSON" ]; then
    echo '{"versionCode": 0, "versionName": "0", "link": "", "changelog": ""}' > "$UPDATE_JSON"
fi

APK_UNIVERSAL_FILENAME=$(basename "$APK_UNIVERSAL")
DOWNLOAD_URL="https://github.com/Igor1974/Custom-Launcher-ROOT-/releases/download/v$VERSION_NAME/$APK_UNIVERSAL_FILENAME"

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
git add "$UPDATE_JSON" release.sh
git commit -m "Prepare release v$VERSION_NAME ($VERSION_CODE) with multi-ABI support"
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

# Собираем список файлов для загрузки
UPLOAD_FILES=""
[ -f "$APK_ARM" ] && UPLOAD_FILES="$UPLOAD_FILES \"$APK_ARM\""
[ -f "$APK_ARM64" ] && UPLOAD_FILES="$UPLOAD_FILES \"$APK_ARM64\""
[ -f "$APK_UNIVERSAL" ] && UPLOAD_FILES="$UPLOAD_FILES \"$APK_UNIVERSAL\""

eval "gh release create \"$TAG\" $UPLOAD_FILES \
    --title \"Release $VERSION_NAME\" \
    --notes \"$CHANGELOG_TEXT\" \
    --target main"

# shellcheck disable=SC2181
if [ $? -eq 0 ]; then
    echo -e "\n${GREEN}======================================"
    echo -e "    РЕЛИЗ v$VERSION_NAME (Multi-ABI) УСПЕШНО ЗАВЕРШЕН!"
    echo -e "======================================${NC}"
else
    echo -e "${RED}Произошла ошибка при создании релиза.${NC}"
fi
