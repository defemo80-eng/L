# DeiAI — Asistente Virtual Inteligente para Android

![Compilar APK de DeiAI](../../actions/workflows/android-build.yml/badge.svg)

Aplicación móvil nativa para Android desarrollada en **Kotlin** y **Jetpack Compose (Material 3)** con integración de **Gemini** mediante **Firebase AI Logic**, **Cloud Firestore**, **Google Sign-In**, reconocimiento/síntesis de voz en español y servicio en primer plano para activación por voz («Hola DeiAI»).

## Características principales

- **Inteligencia Artificial (Gemini):** Conversaciones naturales en español con contexto, resolución de problemas, redacción, traducción y respuestas JSON estructuradas vía `Firebase.ai` (`gemini-3.8-flash`) protegido con **Firebase App Check**.
- **Personalidad y Emociones Simuladas:** Avatar animado (`DeiAiAvatar`) que cambia su expresión y modulación de voz entre *Alegría*, *Tristeza*, *Enojo divertido*, *Sorpresa*, *Curiosidad*, *Entusiasmo* y *Tranquilidad*.
- **Voz en Español y «Hola DeiAI»:** Reconocimiento de voz (`SpeechRecognizer`), lectura en voz alta (`TextToSpeech`) con selector de voces, velocidad, volumen, interrupción inmediata, modo de conversación continua y servicio `DeiAiVoiceService` en primer plano con notificación visible.
- **Memoria Inteligente e Historial:** Persistencia aislada por usuario en Cloud Firestore (con caché local sin conexión) y preferencias locales con Jetpack DataStore.
- **Acciones del Teléfono:** Consulta de hora/fecha, apertura de aplicaciones y ajustes, creación de alarmas/recordatorios y marcación telefónica previa confirmación explícita.

## Descargar el APK desde GitHub Actions

Al subir el proyecto a GitHub, el flujo **Compilar APK de DeiAI** en la pestaña **Acciones (Actions)** compila automáticamente la app y genera el artefacto descargable **`DeiAI-debug-apk`** (`app-debug.apk`).

## Compilación y pruebas locales

```bash
# Compilar APK de depuración
gradle :app:assembleDebug

# Ejecutar pruebas unitarias y Robolectric
gradle :app:testDebugUnitTest
```

El APK generado se guarda en `app/build/outputs/apk/debug/app-debug.apk`.
