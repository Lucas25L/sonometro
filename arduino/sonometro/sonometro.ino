#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

// ------------------ CONFIGURACIÓN FIREBASE ------------------
// URL de la base de datos en Firebase (Realtime Database)
#define DATABASE_URL "https://sonometro-5375d-default-rtdb.firebaseio.com/"
// Token heredado (Database Secret) para autenticación en Firebase
#define DATABASE_SECRET "ISW1epvsg1yKiVhsYFDQrn1KU5cTwzOSdKrJa92B"

// ------------------ CONFIGURACIÓN WIFI ------------------
// Credenciales de la red Wi-Fi a la que se conectará el ESP32
#define WIFI_SSID "juanma"
#define WIFI_PASSWORD "juanma2022"

// ------------------ SENSOR ------------------
// Pin de entrada analógica al que se conecta la salida del sensor MAX4466
const int pinSensor = 34;
// Ventana de muestreo en milisegundos (tiempo durante el cual se recolectan datos)
int sampleWindow = 50;
// Variables auxiliares para medir amplitud de la señal
unsigned long startMillis = 0;
unsigned int peakToPeak = 0;

// ------------------ OBJETOS FIREBASE ------------------
// Objeto que gestiona la comunicación con la base de datos
FirebaseData fbdo;
// Objetos de autenticación y configuración de Firebase
FirebaseAuth auth;
FirebaseConfig config;

void setup() {
  Serial.begin(115200);

  // Conectar a Wi-Fi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Conectando a Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\n✅ Conectado a Wi-Fi!");
  Serial.print("IP: ");
  Serial.println(WiFi.localIP());

  // Configurar Firebase con la URL y el token heredado
  config.database_url = DATABASE_URL;
  config.signer.tokens.legacy_token = DATABASE_SECRET;

  // Inicializar conexión con Firebase
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
}

void loop() {
  // Guardar el momento de inicio de la ventana de muestreo
  startMillis = millis();
  // Valores iniciales para la señal leída (ADC de 12 bits: 0-4095 en ESP32)
  unsigned int signalMax = 0;
  unsigned int signalMin = 4095;

  // Recolectar datos del sensor durante la ventana de muestreo
  while (millis() - startMillis < sampleWindow) {
    int sample = analogRead(pinSensor);
    if (sample > 0) {
      if (sample > signalMax) signalMax = sample;   // actualizar máximo
      else if (sample < signalMin) signalMin = sample; // actualizar mínimo
    }
  }

  // Diferencia entre el máximo y el mínimo: amplitud pico a pico
  peakToPeak = signalMax - signalMin;

  // Conversión aproximada a decibelios (escala de 40 a 100 dB)
  int dB = map(peakToPeak, 0, 4095, 40, 100);

  // Crear objeto JSON con los datos a enviar
  FirebaseJson content;
  content.set("nivel_ruido", dB);                // valor en dB
  content.set("fecha_hora/.sv", "timestamp");    // marca temporal generada por Firebase

  // Enviar los datos a Firebase en la ruta "/ruido"
  if (Firebase.ready() && Firebase.RTDB.pushJSON(&fbdo, "/ruido", &content)) {
    Serial.println("✅ Datos enviados a Firebase.");
  } else {
    Serial.print("❌ Error al enviar datos: ");
    Serial.println(fbdo.errorReason());
  }

  // Esperar 5 segundos antes de la siguiente medición
  delay(5000);
}