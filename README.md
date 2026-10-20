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
1. Abrir `arduino/sonometro.ino` en Arduino IDE.
2. Configurar el **WiFi** y las credenciales de Firebase dentro del código.
3. Subir el sketch al ESP32.

### 2. Android (Aplicación móvil)
1. Abrir el proyecto en **Android Studio**.
2. Configurar `google-services1.json` con tu proyecto de Firebase.
3. Ejecutar la app en un dispositivo físico o emulador.

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