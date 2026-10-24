# Sonómetro 2.0.0

Aplicación Android + hardware (ESP32 y micrófono MAX4466) que **mide el sonido ambiental en dB(A)** y lo **compara con la normativa de ruidos de la ciudad de Resistencia (Chaco)**: la Ordenanza 3593/98 del Concejo Municipal.

Proyecto académico de la Tecnicatura Superior en Desarrollo de Software.

> **Importante:** es un instrumento **indicativo**. Sirve para detectar y documentar un posible exceso de ruido, pero **no reemplaza una medición oficial** (la ordenanza exige un medidor IRAM 4074 tipo 2). Ver [Limitaciones](#limitaciones).

---

## Cómo funciona

```
 MAX4466 ──► ESP32 ──WiFi──► Firebase Realtime Database ◄──► App Android
 (micrófono)  mide dB(A)      /ruido   /control               gráfico + veredicto
```

1. El **ESP32** toma ~16.000 muestras por segundo del micrófono, las pasa por un **filtro de ponderación A** (IEC 61672), calcula el nivel RMS de 1 segundo y le suma un **offset de calibración**. El resultado (dB(A)) se envía a Firebase una vez por segundo.
2. Mientras tanto envía un "latido" (`control/lastSeen`) cada 2 s, para que la app sepa si está encendido, y lee `control/monitoreo` para saber si debe medir.
3. La **app** se autentica en Firebase, muestra las últimas 300 lecturas en un gráfico con la hora real, el mínimo y el máximo, y compara cada lectura con el límite de la ordenanza según el **ámbito** elegido y la **franja horaria** (día 06:00–22:00, noche 22:00–06:00).

### Estructura de la base de datos

| Nodo | Contenido | Quién escribe |
|---|---|---|
| `ruido/<id>` | `nivel_ruido` (dB(A), entero 0–140) y `fecha_hora` (ms desde 1970) | solo el ESP32 (la app puede borrar el nodo) |
| `control/monitoreo` | `true` / `false`: iniciar o detener la medición | la app |
| `control/lastSeen` | hora del último latido del ESP32 | el ESP32 |

---

## Hardware

| Pin del MAX4466 | Pin del ESP32 |
|---|---|
| VCC | 3V3 (3,3 V, **no** 5 V) |
| GND | GND |
| OUT | GPIO 34 (ADC1, funciona con el WiFi encendido) |

Placa probada: ESP32-WROOM-32 (perfil *ESP32 Dev Module*).

---

## Instalación

### 1. Firebase

1. Crear un proyecto en la [consola de Firebase](https://console.firebase.google.com) y una base **Realtime Database**.
2. **Authentication → Método de acceso:** habilitar **Anónimo** (lo usa la app) y **Correo electrónico/contraseña** (lo usa el ESP32).
3. **Authentication → Usuarios:** crear el usuario del ESP32 (por ejemplo `esp32@sonometro.app`) con una contraseña de letras y números.
4. **Realtime Database → Reglas:** pegar el contenido de [`firebase/database.rules.json`](firebase/database.rules.json) y publicar. Si cambiás el correo del ESP32, cambialo también en las reglas.
5. Registrar la app Android con el paquete `com.example.sonometro` y descargar `google-services.json` a la carpeta `app/`.
6. Registrar también una app web (ícono `</>`) para obtener la **clave de API web** que usa el ESP32.

### 2. ESP32 (Arduino IDE 2)

1. Instalar el paquete de placas **esp32 by Espressif Systems** y la biblioteca **Firebase Arduino Client Library for ESP8266 and ESP32** (Mobizt).
2. Copiar `arduino/sonometro/secrets.example.h` como `secrets.h` (misma carpeta) y completar WiFi (**2,4 GHz**), URL de la base, clave de API web y usuario/contraseña del ESP32.
3. Abrir `arduino/sonometro/sonometro.ino`, elegir *ESP32 Dev Module* y subirlo.

### 3. App Android

1. Abrir el proyecto en Android Studio (Gradle JDK 17 a 21). Gradle 8.11.1, AGP 8.10.0, `compileSdk` 36, `minSdk` 24.
2. Verificar que `app/google-services.json` esté en su lugar y ejecutar en un dispositivo físico.

> `secrets.h` y `google-services.json` están en `.gitignore` y **no deben subirse al repositorio**.

---

## Calibración

El nivel que calcula el ESP32 es relativo; para que sean dB(A) reales hay que calibrarlo **una vez** con una referencia, y repetirlo si se toca la ganancia (el trimmer) del MAX4466.

1. Poner un **tono de 1 kHz** (a 1 kHz la ponderación A no suma ni resta) y, al lado del micrófono, un sonómetro de referencia o una app de sonómetro con dB(A), a unos 30 cm del parlante y a un nivel de 70–76 dB.
2. En el **Monitor Serie** (115200 baudios, "Nueva línea") escribir `cal <valor>`, por ejemplo `cal 76`.
3. El ESP32 mide 1 segundo, calcula el offset y lo **guarda en su memoria flash** (sobrevive a reinicios).

Comandos del Monitor Serie: `ayuda`, `m` (medir una vez), `cal <dB>`, `offset`, `reset`.

El sketch avisa si la señal se satura (bajar la ganancia) o si el ESP32 no logra muestrear a tiempo (poner `PERIODO_MUESTREO_US` en 125 en `filtro_a.h`).

---

## Normativa aplicada

**Ordenanza 3593/98 del Concejo Municipal de Resistencia**, Art. 12 (mediciones en dB(A)). Límites de **ruido ambiente**, que es contra los que la app compara:

| Ámbito | Día (06–22 h) | Noche (22–06 h) |
|---|---|---|
| I – Hospitalario / educativo (radio de 100 m) | 45 | 35 |
| II – Zonas habitadas (residencial) | 55 | 45 |
| III – Comercial y de entretenimiento | 60 | 50 |
| IV – Industrial | 60 | 50 |

Un nivel **igual** al límite se considera dentro del límite; solo lo que lo supera se marca como superado. La ordenanza también fija límites para *picos frecuentes* (7 a 60 por hora) y *poco frecuentes* (1 a 6 por hora); están cargados en `Normativa.java` pero la app todavía no los evalúa.

---

## Limitaciones

- **No es un instrumento certificado.** Micrófono electret de bajo costo, calibrado en **un solo punto** (1 kHz) contra una app de celular, cuya incertidumbre típica es de ±3 a 5 dB.
- La ordenanza pide respuesta "lenta" del medidor; el equipo promedia la energía de ventanas de 1 s, que es una aproximación.
- El **piso de ruido del equipo no está caracterizado**: en el ambiente donde se probó, el celular indicó 64 dB y el equipo 67–68 dB(A); hace falta medir en un lugar muy silencioso para conocerlo. Hasta entonces, no se deben interpretar lecturas por debajo de ~60 dB(A).
- El micrófono MAX4466 no tiene respuesta plana y el ADC del ESP32 no es lineal; el filtro A se verificó contra la tabla de la norma (error ≤ 0,5 dB), pero el conjunto no se midió en cámara anecoica.
- No se verificó si la Ordenanza 3593/98 fue modificada o reemplazada después de 1998, ni se encontró una norma provincial específica del Chaco.
- La app asume que el teléfono está en hora argentina para decidir si es de día o de noche.

---

## Historial de versiones

- **2.0.0**
  - Seguridad: Firebase Authentication y reglas de base de datos; sin credenciales en el repositorio.
  - Conexión real del ESP32 (latido), lectura limitada a las últimas 300 mediciones, gráfico con hora real, listeners liberados al cerrar.
  - Medición en **dB(A)** con filtro de ponderación A, calibración persistente y avisos de saturación.
  - Comparación con la Ordenanza 3593/98 (selector de ámbito, veredicto y línea de límite).
- **1.0.0** – Versión inicial: medición aproximada y gráfico en tiempo real.

---

## Autores

- Lucas López
- Tania Bacik
- Juan Arellano
