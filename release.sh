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
ЗВУК И DSP:
- DeepNight DSP v2.0: Глубокая переработка аудио-движка и алгоритмов обработки сигнала.
- Loudness+: Обновленный интеллектуальный алгоритм тонкомпенсации — звук стал еще плотнее и чище на любой громкости.
- Hi-Fi Equalizer: Исправлена сетка частот 11-полосного эквалайзера (31Гц - 16кГц) для прецизионной настройки акустики.

ПРОИЗВОДИТЕЛЬНОСТЬ И GPU:
- Extreme Optimization: Устранена критическая нагрузка на сервис android.hardware.graphics.allocator, вызывавшая зависания интерфейса.
- Smart Rendering: Полное отключение отрисовки рабочего стола при работе заставки — 0% лишней нагрузки на GPU.
- Full AOT Support: Добавлен инструмент глубокой перекомпиляции всех установленных приложений для максимальной отзывчивости системы.

СИСТЕМА И ПАМЯТЬ:
- Auto-RAM Optimizer: Лаунчер автоматически очищает фоновые процессы и сбрасывает системные кэши (drop_caches), если свободная память падает ниже 300МБ (требуется Root).
- Modular Architecture: Весь код заставок и элементов интерфейса вынесен в отдельные модули для стабильности и снижения потребления ресурсов.

ИНТЕРФЕЙС И НАВИГАЦИЯ:
- Интерфейс: Новая тема «Чистый Android» (Classic Mode) — максимальный минимализм.
- Hidden Apps Manager: Полноценный интерфейс для скрытия ненужных программ (доступен по Long Click на любой иконке).
- Minimalist Status Bar: Обновлен дизайн виджетов часов и погоды.
- Focus Fix: Улучшена логика удержания фокуса при навигации и возврате из папок.

ЗАСТАВКИ (SCREENSAVERS):
- Cosmic Flow: В космосе появился маленький детеныш дракона!
- Aerial Dream: Исправлены ошибки декодеров и SSL-сертификатов при загрузке видео. Теперь заставка стабильно работает на любых процессорах.
- Cleaning: Полностью удалена устаревшая заставка Matrix для уменьшения размера APK.

🐞 ПАСХАЛКА:
Добавил новых багов, как всегда..."
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

# shellcheck disable=SC2181
if [ $? -eq 0 ]; then
    echo -e "\n${GREEN}======================================"
    echo -e "    РЕЛИЗ v$VERSION_NAME УСПЕШНО ЗАВЕРШЕН!"
    echo -e "======================================${NC}"
else
    echo -e "${RED}Произошла ошибка при создании релиза.${NC}"
fi
