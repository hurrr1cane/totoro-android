# Totoro Android — голосовий асистент для планшета

Native Android додаток, який слухає **«Гей, Тоторо»** (або чисте **«Тоторо»** з Porcupine) і
виконує команди: відкриває YouTube/Music/Spotify, керує Home Assistant, шле в Telegram,
записує нотатки в Obsidian, вмикає таймери/будильники, читає погоду/курс валют.

## Структура

- `app/src/main/java/com/totoro/assistant/`
  - `MainActivity.kt`         — Compose UI (кнопка старт/стоп)
  - `TotoroApp.kt`             — Application, канал нотифікацій
  - `service/TotoroListenerService.kt` — ForegroundService (завжди слухає)
  - `service/WakeListener.kt`  — Wake-word (Porcupine + fallback на SpeechRecognizer)
  - `service/CommandRouter.kt` — Виконавець команд (intent/HA/Telegram/Obsidian)
  - `hermes/HermesClient.kt`   — AI fallback у Hermes
  - `prefs/TotorPrefs.kt`      — Налаштування
  - `receiver/BootReceiver.kt` — Автозапуск після перезавантаження
- `app/build.gradle.kts`      — Залежності (Porcupine, OkHttp, Compose)

## Збірка

### Варіант 1 — Android Studio
Відкрий папку `android-totoro/` у Android Studio Hedgehog (2023.1.1) або новіше.
Збирай через `Build → Build Bundle(s)/APK(s) → Build APK(s)`.

### Варіант 2 — через GitHub Actions (рекомендовано)
Цей репозиторій містить `.github/workflows/build.yml`, який безкоштовно
збирає debug-signed APK у cloud. Після push у GitHub → Actions → останній workflow
→ Artifacts → `totoro-debug.apk`.

### Варіант 3 — локально через gradlew (Linux/macOS)
```bash
echo 'sdk.dir=/opt/android-sdk' > local.properties
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## Встановлення

1. Увімкни на планшеті **«Установка з невідомих джерел»** (Налаштування → Безпека)
2. Перекинь APK на планшет, відкрий файловий менеджер → тапни на APK → Встановити
3. Запусти **Тоторо** → дозволь мікрофон, notifications, "поверх інших вікон"
4. Налаштуй: відкрий Налаштування (іконка в MainActivity) — введи Hermes endpoint, HA URL, тощо
5. Натисни **▶ Увімкнути** → запуститься ForegroundService
6. Скажи **«Гей, Тоторо, увімкни YouTube»**

## Wake-word без "Гей" (Porcupine)

1. Зареєструйся на https://console.picovoice.ai (free для personal use)
2. Згенеруй wake-word "Торо" / "Totoro" → отримай `totoro_android.ppn`
3. Поклади `.ppn` у `app/src/main/assets/`
4. Встав AccessKey у `~/.gradle/local.properties`:
   ```
   PICOVOICE_KEY=your_access_key_here
   ```
5. Перебудуй. У додатку постав галочку «Використовувати Porcupine».

## Інтеграції

- **Hermes** — будь-яка невідома команда летить у Hermes endpoint, я можу
  повернути JSON `{reply, action, speak, url}` і виконати дію.
- **Home Assistant** — toggle lights/switches/climate; читати стан сенсорів.
- **Telegram** — надсилати нотатки, нагадування.
- **Obsidian** — зберігати нотатки через API.
- **YouTube/YT Music/Spotify** — інтент-апи магазину застосунків
  (якщо застосунок не встановлений → fallback на web URL).
