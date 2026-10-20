#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

// ------------------ CREDENCIALES ------------------
// WiFi, URL de la base, API key y usuario del ESP32 viven en secrets.h,
// que NO se sube a git. Copiar secrets.example.h a secrets.h y completarlo.
#include "secrets.h"

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

// Temporizadores no bloqueantes (en milisegundos)
const long intervaloControl = 1000;  // cada cuánto se consulta control/monitoreo
const long intervaloMuestra = 1000;  // cada cuánto se envía una medición
unsigned long ultimoControl = 0;
unsigned long ultimaMuestra = 0;

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
  // Autenticación con el usuario del ESP32 (correo/contraseña de Firebase Auth).
  // Reemplaza al antiguo "database secret", que daba acceso total sin reglas.
  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;
  auth.user.email = FIREBASE_USER_EMAIL;
  auth.user.password = FIREBASE_USER_PASSWORD;
  config.token_status_callback = tokenStatusCallback; // de addons/TokenHelper.h

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  Serial.println("🔥 Iniciando sesión en Firebase (ver estado del token arriba)");
}

void loop() {
  // Sin WiFi o sin sesión de Firebase lista no hay nada que hacer
  if (!Firebase.ready() || WiFi.status() != WL_CONNECTED) {
    delay(200);
    return;
  }

  unsigned long ahora = millis();

  // --- 1. LATIDO: control/lastSeen cada 2 s (la app lo usa para saber si el ESP32 está vivo) ---
  if (ahora - previousMillis >= interval) {
    previousMillis = ahora;
    unsigned long long timestamp = (unsigned long long)time(nullptr) * 1000ULL;

    if (!Firebase.RTDB.setDouble(&fbdo, "control/lastSeen", (double)timestamp)) {
      Serial.printf("⚠️ Error actualizando lastSeen: %s\n", fbdo.errorReason().c_str());
    }
  }

  // --- 2. ESTADO DE MONITOREO: se consulta cada 1 s, sin bloquear el loop ---
  if (ahora - ultimoControl >= intervaloControl) {
    ultimoControl = ahora;

    if (Firebase.RTDB.getBool(&fbdo, "control/monitoreo")) {
      bool estadoMonitoreo = fbdo.boolData();
      if (estadoMonitoreo != monitoreoActivo) {
        monitoreoActivo = estadoMonitoreo;
        Serial.println(monitoreoActivo ? "🎙️ Monitoreo ACTIVADO" : "🛑 Monitoreo DETENIDO");
      }
    } else {
      // Mientras la app no haya iniciado nunca el monitoreo, el nodo no existe: es normal
      Serial.printf("ℹ️ control/monitoreo: %s\n", fbdo.errorReason().c_str());
    }
  }

  // --- 3. MEDICIÓN: una muestra por segundo mientras el monitoreo está activo ---
  if (monitoreoActivo && ahora - ultimaMuestra >= intervaloMuestra) {
    ultimaMuestra = ahora;

    time_t ahoraEpoch = time(nullptr);
    if (ahoraEpoch < 1700000000) {
      Serial.println("⚠️ Hora NTP no disponible, se omite la muestra");
      return;
    }

    int nivel = medirRuido();
    FirebaseJson dato;
    dato.set("nivel_ruido", nivel);
    dato.set("fecha_hora", (double)((unsigned long long)ahoraEpoch * 1000ULL));

    if (Firebase.RTDB.pushJSON(&fbdo, "ruido", &dato)) {
      Serial.printf("✅ Nivel: %d dB enviado a Firebase\n", nivel);
    } else {
      Serial.printf("❌ Error enviando dato: %s\n", fbdo.errorReason().c_str());
    }
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