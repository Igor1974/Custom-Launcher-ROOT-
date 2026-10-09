# DeepNight Ultimate (Custom Launcher ROOT)

<p align="center">
  <img src="app/src/main/res/drawable/banner.png" alt="DeepNight Ultimate Banner" width="100%" />
</p>

<p align="center">
  <a href="https://github.com/Igor1974/Custom-Launcher-ROOT-/releases"><img src="https://img.shields.io/github/v/release/Igor1974/Custom-Launcher-ROOT-?style=for-the-badge&color=blue" alt="Latest Release"></a>
  <a href="https://android.com"><img src="https://img.shields.io/badge/Android_TV-9.0%2B-green?style=for-the-badge&logo=android" alt="Android TV 9.0+"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.0-purple?style=for-the-badge&logo=kotlin" alt="Kotlin"></a>
  <a href="https://isocpp.org"><img src="https://img.shields.io/badge/C%2B%2B-20_(NDK)-blue?style=for-the-badge&logo=cplusplus" alt="C++20 Engine"></a>
  <a href="https://github.com/Igor1974/Custom-Launcher-ROOT-/blob/main/LICENSE"><img src="https://img.shields.io/github/license/Igor1974/Custom-Launcher-ROOT-?style=for-the-badge" alt="License"></a>
</p>

<p align="center">
  <b>Интегрированная медиа-экосистема и высокопроизводительная оболочка для Android TV и ТВ-приставок на нативном C++20 ядре и Jetpack Compose.</b>
</p>

<p align="center">
  <a href="https://github.com/Igor1974/Custom-Launcher-ROOT-/releases"><b>📦 Скачать APK</b></a> •
  <a href="#-ключевые-возможности"><b>⚡ Возможности</b></a> •
  <a href="#-сравнение-с-аналогами"><b>📊 Сравнение</b></a> •
  <a href="#-требования-к-системе"><b>📋 Требования</b></a> •
  <a href="#-deepnight-sdk-для-разработчиков"><b>🛠 SDK</b></a>
</p>

---

## 📸 Скриншоты

<p align="center">
  <img src="screenshots/Screenshot_20260630_190040.png" width="90%" alt="Главный экран DeepNight Ultimate" />
</p>

---

## ⚡ Ключевые возможности

### 🤖 1. ИИ-Экосистема и Голосовой Ассистент
- **Deep Night Offline AI (On-Device):** Первый в мире автономный квантованный ИИ `Qwen 2.5 0.5B GGUF Q4_K_M` (380 МБ), работающий 100% на процессоре ТВ без интернета.
- **Интеграция с Яндекс Фактами & Поиском:** Быстрый подтяг информации на любые бытовые и научные вопросы.
- **Мульти-модельность (BYOK):** Поддержка Yandex GPT, Cerebras AI (1800 токенов/сек), SambaNova, OpenRouter, Together AI, Gemini, DeepSeek, Groq, Mistral, GigaChat.
- **Запоминание контекста:** Автоматическое запоминание имени пользователя, вкусов и персональной истории.

### 🎨 2. Единый Кастомизатор и Live Preview
- **6 подвкладок стилей:** Пресеты (Apple TV, Cyberpunk), Настройка иконок, Цветовая палитра, Системные шрифты, Уведомления, Оверлей громкости.
- **Динамический Эквалайзер:** 5-полосный анимационный индикатор уровня сигнала с возможностью выбора цвета и позиции.

### ⚙️ 3. Производительность и C++20 Ядро
- **Асинхронные шрифты:** Сканирование системных шрифтов вынесено в `Dispatchers.IO` с кешированием в `ConcurrentHashMap`.
- **Оптимизированный фокус:** Безупречная D-Pad навигация в подменю активных модулей без потерь фокуса.

---

## 🛠 DeepNight SDK (для разработчиков)

Проект включает в себя открытые модули **DeepNight SDK**, которые можно использовать в сторонних Android TV приложениях и Unity-играх:
- **`:sdk:tv-input`**: Умная навигация и обработка D-Pad фокуса для Jetpack Compose TV.
- **`:sdk:dap-core`**: Нативный C++20 аудио-пайплайн (FFT, VAD) с минимальной задержкой.
- **`:sdk:ai-commands`**: Лингвистический модуль для русскоязычного поиска и стемминга.

> Подробнее о структуре SDK смотрите в файле [`sdk/README.md`](sdk/README.md) и [`sdk/PITCH_4PDA_HABR.md`](sdk/PITCH_4PDA_HABR.md).

---

## 📋 Требования к системе

- **Платформа:** Android TV / ТВ-приставка / Smart TV (Android 9.0 и выше)
- **Базовый режим:** Работает на любом устройстве без необходимости Root-прав.
- **PRO / Root-режим (опционально):** Наличие Root (Magisk / APatch) для функции Boost, T-Guard и управления питанием.
- **Для ИИ-модулей:** Пользовательские API-ключи (Gemini / OpenAI / Groq / GigaChat).
- **Для Торрент-плеера:** Совместимый TorrServe-клиент (TorrServer).

---

## 📦 Установка и запуск

1. Перейдите в раздел **[Releases](https://github.com/Igor1974/Custom-Launcher-ROOT-/releases)** и скачайте нужную версию:
    - **`armeabi-v7a`** — Для большинства ТВ-приставок и Smart TV (32-бит).
    - **`arm64-v8a`** — Для 64-битных устройств (Nvidia Shield, Chromecast with Google TV 4K).
    - **`universal`** — Универсальная сборка.
2. Установите APK на Android TV устройство (через Файловый менеджер или `adb install`).
3. При первом запуске назначьте DeepNight Ultimate основным лаунчером по умолчанию.
4. *(Опционально)* Предоставьте Root-права в Magisk/APatch для активации системного Boost.

---

## ⚠️ Дисклеймер

> **ВАЖНО:** Root-функции, изменение системных разделов (`/product`, `/vendor`, `/odm`) и использование низкоуровневых скриптов предназначены для продвинутых пользователей. Пользователь несёт полную ответственность за использование торрент-источников, сторонних API и соблюдение местного законодательства.

---

## 🛠 Технический стек

- **Языки:** Kotlin, C++20 (NDK Engine)
- **UI:** Jetpack Compose TV / Material 3
