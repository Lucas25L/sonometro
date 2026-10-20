---

## ⚙️ Tecnologías utilizadas

- **Hardware**
  - ESP32
  - Sensor MAX4466 (micrófono)
- **Software**
  - Android Studio (Java)
  - Arduino IDE (C++)
  - Firebase Realtime Database

---

## 🚀 Instrucciones de uso

### 1. Arduino (ESP32 + MAX4466)
1. Abrir `arduino/sonometro/sonometro.ino` en Arduino IDE.
2. Copiar `secrets.example.h` como `secrets.h` (misma carpeta) y completar WiFi, URL de la base, API key y el usuario/contraseña del ESP32 (Firebase Authentication). `secrets.h` está en `.gitignore`.
3. Subir el sketch al ESP32.

### 2. Android (Aplicación móvil)
1. Abrir el proyecto en **Android Studio** (Gradle JDK 17 a 21).
2. Descargar `google-services.json` desde tu proyecto de Firebase (app Android `com.example.sonometro`) y copiarlo en `app/`. Está en `.gitignore`.
3. Habilitar el método **Anónimo** en Firebase Authentication y publicar las reglas de Realtime Database.
4. Ejecutar la app en un dispositivo físico.

---

## 📊 Funcionamiento

- El ESP32 mide el nivel de ruido (dB) con el MAX4466 y envía los datos a Firebase.
- La app Android consulta la base de datos y muestra los valores en pantalla.
- Se puede expandir para:
    - Guardar histórico de mediciones.
    - Mostrar gráficos.
    - Generar alertas si el ruido supera cierto umbral.

---

## 👨‍💻 Autor

- **Lucas López** – Proyecto académico (Desarrollo de Software + IoT)
- **Tania Bacik** -
- **Juan Arellano** -
---