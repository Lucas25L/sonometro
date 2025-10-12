#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

// ------------------ CONFIGURACIÓN FIREBASE ------------------
#define DATABASE_URL "https://sonometro-5375d-default-rtdb.firebaseio.com/"
#define DATABASE_SECRET "ISW1epvsg1yKiVhsYFDQrn1KU5cTwzOSdKrJa92B"

// ------------------ CONFIGURACIÓN WIFI ------------------
#define WIFI_SSID "nombre_wifi"
#define WIFI_PASSWORD "contraseña_wifi"

// ------------------ SENSOR ------------------
const int pinSensor = 34;
const int sampleWindow = 50; // ms
unsigned long startMillis = 0;
unsigned int peakToPeak = 0;

// ------------------ OBJETOS FIREBASE ------------------
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// ------------------ VARIABLES DE CONTROL ------------------
unsigned long previousMillis = 0;
const long interval = 2000; // Actualización del lastSeen cada 2 segundos
bool monitoreoActivo = false;

void setup() {
  Serial.begin(115200);
  pinMode(pinSensor, INPUT);

  // --- CONEXIÓN WIFI ---
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Conectando a WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(500);
  }
  Serial.println("\n✅ WiFi conectado");
  Serial.print("IP local: ");
  Serial.println(WiFi.localIP());

  // --- SINCRONIZACIÓN NTP ---
  configTime(-3 * 3600, 0, "pool.ntp.org", "time.nist.gov"); // UTC-3 Argentina

  Serial.println("⏳ Sincronizando hora NTP...");
  time_t now = time(nullptr);
  int intentos = 0;

  while (now < 1700000000 && intentos < 20) { // 1700000000 ≈ enero 2024
    delay(1000);
    Serial.print(".");
    now = time(nullptr);
    intentos++;
  }

  if (now >= 1700000000) {
    Serial.println("\n✅ Hora NTP sincronizada correctamente");
    struct tm timeinfo;
    getLocalTime(&timeinfo);
    Serial.printf("🕒 Hora actual: %02d:%02d:%02d %02d/%02d/%04d\n",
                  timeinfo.tm_hour, timeinfo.tm_min, timeinfo.tm_sec,
                  timeinfo.tm_mday, timeinfo.tm_mon + 1, timeinfo.tm_year + 1900);
  } else {
    Serial.println("\n⚠️ No se pudo sincronizar la hora NTP, usando millis() como respaldo");
  }

  // --- CONFIGURAR FIREBASE ---
  config.database_url = DATABASE_URL;
  config.signer.tokens.legacy_token = DATABASE_SECRET;

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  Serial.println("🔥 Conectado a Firebase");
}

void loop() {
  // --- ACTUALIZAR NODO CONTROL/LASTSEEN ---
  if (Firebase.ready() && WiFi.status() == WL_CONNECTED) {
    unsigned long currentMillis = millis();
    if (currentMillis - previousMillis >= interval) {
      previousMillis = currentMillis;

      unsigned long long timestamp = (unsigned long long)time(nullptr) * 1000ULL;

      if (!Firebase.RTDB.setDouble(&fbdo, "control/lastSeen", (double)timestamp)) {
        Serial.printf("⚠️ Error actualizando lastSeen: %s\n", fbdo.errorReason().c_str());
      } else {
        Serial.printf("📡 lastSeen actualizado: %llu\n", timestamp);
      }
    }
  }

  // --- LEER ESTADO DE MONITOREO ---
  if (Firebase.RTDB.getBool(&fbdo, "control/monitoreo")) {
    bool estadoMonitoreo = fbdo.boolData();

    // Detectar cambios de estado
    if (estadoMonitoreo != monitoreoActivo) {
      monitoreoActivo = estadoMonitoreo;
      Serial.println(monitoreoActivo ? "🎙️ Monitoreo ACTIVADO" : "🛑 Monitoreo DETENIDO");
    }

    // Si el monitoreo está activo → medir y enviar datos
    if (monitoreoActivo) {
      int nivel = medirRuido();
      unsigned long long timestamp = (unsigned long long)time(nullptr) * 1000ULL;

      FirebaseJson dato;
      dato.set("nivel_ruido", nivel);
      dato.set("fecha_hora", (double)timestamp);

      if (Firebase.RTDB.pushJSON(&fbdo, "ruido", &dato)) {
        Serial.printf("✅ Nivel: %d dB enviado a Firebase\n", nivel);
      } else {
        Serial.printf("❌ Error enviando dato: %s\n", fbdo.errorReason().c_str());
      }

      delay(1000); // 1 segundo entre muestras
    }

  } else {
    Serial.printf("⚠️ Error leyendo control/monitoreo: %s\n", fbdo.errorReason().c_str());
    delay(1000);
  }
}

// ------------------------------------------------------------
// FUNCIONES AUXILIARES
// ------------------------------------------------------------

// Función para medir el nivel de ruido (aproximado en dB)
int medirRuido() {
  unsigned int signalMax = 0;
  unsigned int signalMin = 4095;
  startMillis = millis();

  while (millis() - startMillis < sampleWindow) {
    int sample = analogRead(pinSensor);
    if (sample > signalMax) signalMax = sample;
    else if (sample < signalMin) signalMin = sample;
  }

  peakToPeak = signalMax - signalMin;
  int nivel = map(peakToPeak, 0, 4095, 40, 100);
  return nivel;
}