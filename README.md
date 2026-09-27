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
 
</p>

---

## ⚡ Ключевые возможности

### ⚙️ Нативное C++20 ядро (DeepNightEngine)
- **Минимальная нагрузка на ресурсы:** Тяжелые математические вычисления (FFT-анализ аудио, фонетический поиск, стемминг, парсинг медиаданных) вынесены в C++20 NDK код. Это обеспечивает плавные 60 FPS даже на бюджетных ТВ-приставах.
- **Инфо-панель (Dashboard):** Мониторинг температуры процессора (CPU), оперативной памяти (RAM) и скорости сети в реальном времени.
- **Нулевое потребление CPU в режиме ожидания.**

### 🛠 Системные функции и ROOT-инструменты
- **Оптимизация (Boost):** Очистка фоновых процессов и системного кэша в один клик.
- **T-Guard & RW-remount:** Расширенные инструменты для опытных пользователей (правка системных партиций `/product`, `/vendor`, `/odm`, оптимизация системных лимитов).
- **Управление питанием:** Быстрый доступ к перезагрузке (обычной, Recovery, Bootloader) и выключению устройства.

### 🌐 Сеть, Трафик и VPN
- **Встроенный VPN (АнтиЗапрет):** Интегрированный OpenVPN-клиент для обхода блокировок кинематографических ресурсов «из коробки».
- **DeepTunnel PRO:** Локальная фрагментация пакетов для стабилизации передачи потокового 4K/HDR видео.
- **Мониторинг соединений:** Автоматическое определение активных сетей и аудиовыходов (HDMI, ARC, Bluetooth, USB, AUX).

### 🤖 ИИ-Студия и Голос
- **Мульти-модельная ИИ-студия:** Интеграция с Gemini, OpenAI, Groq, GigaChat для умного ассистента, генерации обоев и рекомендаций.
- **Офлайн-TTS и VAD:** Нативный C++ детектор голоса и озвучивание текста без сетевых задержек.

### 🎬 Медиа, Поиск и Интеграции
- **Все включено:** Встроенные модули IPTV, Радио, Торрент-агрегатора (TorrServe), Игр и Погоды.
- **C++ DSP Аудио:** Нативный эквалайзер и неоновые спектрограммы/визуализаторы.
- **Глобальный фонетический поиск:** Находит фильмы, даже если название введено с ошибкой или транслитом.

---

## 📊 Сравнение с аналогами

| Функция / Возможность | **DeepNight Ultimate** | **Projectivy Launcher** | **ATV Launcher** | **Стоковый Leanback** |
| :--- | :---: | :---: | :---: | :---: |
| **UI Фреймворк** | **Jetpack Compose TV** | Custom Android Views | Legacy Views | Leanback / Android TV |
| **C++20 NDK Движок (DSP / FFT)** | ✅ **Да** | ❌ Нет | ❌ Нет | ❌ Нет |
| **Встроенный VPN (АнтиЗапрет)** | ✅ **Да** | ❌ Нет | ❌ Нет | ❌ Нет |
| **Мульти-модельная ИИ-студия** | ✅ **Gemini / Groq / GigaChat** | ❌ Нет | ❌ Нет | ❌ Ограничен Google Assistant |
| **Root-инструменты (Boost / T-Guard)** | ✅ **Да** | ❌ Нет | ❌ Нет | ❌ Нет |
| **Офлайн TTS & VAD (C++)** | ✅ **Да** | ❌ Нет | ❌ Нет | ❌ Нет |
| **Фонетический поиск медиа** | ✅ **Да** | ❌ Нет | ❌ Нет | ❌ Базовый |

---

## 🛠 DeepNight SDK (для разработчиков)

Проект включает в себя открытые модули **DeepNight SDK**, которые можно использовать в сторонних Android TV приложениях и Unity-играх:
- **`:sdk:tv-input`**: Умная навигация и обработка D-Pad фокуса для Jetpack Compose TV.
- **`:sdk:dap-core`**: Нативный C++20 аудио-пайплайн (FFT, VAD) с минимальной задержкой.
- **`:sdk:ai-commands`**: Лингвистический модуль для русскоязычного поиска и стемминга.

> Подробнее о структуре SDK смотрите в файле (https://github.com/Igor1974/DeepNightSDK/blob/main/README.md)) и (https://github.com/Igor1974/DeepNightSDK/blob/main/PITCH_4PDA_HABR.md)).

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
- **Network & Audio:** OkHttp 4, OpenVPN Core, C++ FFT/DSP Audio Pipeline
- **Shell & Root:** libsu (ROOT interop)

---

<p align="center">
  <b>Разработчик:</b> Igor1974 • <b>Версия:</b> DeepNight Ultimate 5.5.7<br/>
  <i>Сделано с любовью для сообщества Android TV 🚀</i>
</p>
