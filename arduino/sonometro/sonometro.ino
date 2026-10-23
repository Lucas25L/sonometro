#include <WiFi.h>
#include <Preferences.h>
#include <Firebase_ESP_Client.h>
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

// ------------------ CREDENCIALES ------------------
// WiFi, URL de la base, API key y usuario del ESP32 viven en secrets.h,
// que NO se sube a git. Copiar secrets.example.h a secrets.h y completarlo.
#include "secrets.h"

// Núcleo de medición dB(A): filtro de ponderación A + nivel RMS (ver filtro_a.h)
#include "filtro_a.h"

// ------------------ SENSOR ------------------
const int pinSensor = 34;  // GPIO34 = ADC1, sigue funcionando con el WiFi encendido

// ------------------ CALIBRACIÓN ------------------
// Offset (dB) que convierte el nivel bruto del filtro en dB(A) reales. El valor inicial
// es solo una estimación para un MAX4466 con la ganancia a mitad de recorrido: hay que
// calibrar (comando "cal" por el Monitor Serie). El offset calibrado queda guardado en
// la memoria flash del ESP32 y sobrevive a reinicios y a nuevas cargas del sketch.
const float CAL_OFFSET_POR_DEFECTO = 40.0f;
float calOffsetDb = CAL_OFFSET_POR_DEFECTO;
Preferences prefs;

// Resultado de una medición de 1 segundo
struct Medicion {
  float nivelBruto;      // dB sin calibrar
  uint32_t saturadas;    // muestras pegadas al tope/piso del ADC (señal recortada)
  uint32_t atrasadas;    // muestras que el ESP32 no llegó a tomar a tiempo
};

// Declaraciones adelantadas (las funciones están más abajo)
Medicion medirLAeq();
int nivelCalibrado(float nivelBruto);
void avisosDeMedicion(const Medicion& med);
void procesarComandosSerie();

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
unsigned long ultimoControl = 0;

void setup() {
  Serial.begin(115200);
  pinMode(pinSensor, INPUT);
  analogSetPinAttenuation(pinSensor, ADC_11db);  // rango de entrada ~0 a 3,3 V

  // Offset de calibración guardado (si nunca se calibró, usa el valor por defecto)
  prefs.begin("sonometro", false);
  float guardado = prefs.getFloat("calOffset", NAN);  // NAN = nunca se calibró
  bool calibrado = !isnan(guardado);
  calOffsetDb = calibrado ? guardado : CAL_OFFSET_POR_DEFECTO;
  Serial.printf("🎚️ Offset de calibración: %+.1f dB%s\n", calOffsetDb,
                calibrado ? "" : " (por defecto, SIN CALIBRAR)");
  Serial.println("ℹ️ Comandos por el Monitor Serie: ayuda | m | cal <dB> | offset | reset");

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
  // Los comandos de calibración funcionan aunque Firebase todavía no esté listo
  procesarComandosSerie();

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

  // --- 3. MEDICIÓN: una lectura de dB(A) por segundo mientras el monitoreo está activo ---
  // Cada medición toma ~1,1 s de muestreo (más lo que tarde el envío), así que se mide
  // de corrido, sin esperar un intervalo extra.
  if (monitoreoActivo) {
    if (time(nullptr) < 1700000000) {
      Serial.println("⚠️ Hora NTP no disponible, se omite la muestra");
      delay(500);
      return;
    }

    Medicion med = medirLAeq();
    int nivel = nivelCalibrado(med.nivelBruto);

    // La hora se toma al terminar de medir, para que el punto quede en su instante real
    time_t ahoraEpoch = time(nullptr);
    FirebaseJson dato;
    dato.set("nivel_ruido", nivel);
    dato.set("fecha_hora", (double)((unsigned long long)ahoraEpoch * 1000ULL));

    if (Firebase.RTDB.pushJSON(&fbdo, "ruido", &dato)) {
      Serial.printf("✅ %d dB(A) enviado a Firebase (bruto %.1f)\n", nivel, med.nivelBruto);
    } else {
      Serial.printf("❌ Error enviando dato: %s\n", fbdo.errorReason().c_str());
    }
    avisosDeMedicion(med);
  }
}

// ------------------------------------------------------------
// FUNCIONES AUXILIARES
// ------------------------------------------------------------

// Mide 1 segundo de audio y devuelve el nivel bruto con ponderación A.
// Toma una muestra cada PERIODO_MUESTREO_US microsegundos (espera activa, sin delay)
// para que el espaciado sea uniforme, condición necesaria para que el filtro A sea válido.
Medicion medirLAeq() {
  MedidorA medidor;
  Medicion med = {0.0f, 0, 0};

  uint32_t proxima = micros();
  for (uint32_t i = 0; i < MUESTRAS_TOTALES; i++) {
    proxima += PERIODO_MUESTREO_US;
    while ((int32_t)(micros() - proxima) < 0) { /* espera activa hasta el instante de la muestra */ }
    if ((int32_t)(micros() - proxima) > (int32_t)PERIODO_MUESTREO_US) med.atrasadas++;

    int crudo = analogRead(pinSensor);
    if (crudo <= 8 || crudo >= 4087) med.saturadas++;
    medidor.agregar((float)crudo);
  }

  med.nivelBruto = medidor.nivelBrutoDb();
  return med;
}

// Convierte el nivel bruto a dB(A) calibrados, redondeado y acotado a 0..140
// (las reglas de Firebase rechazan valores fuera de ese rango).
int nivelCalibrado(float nivelBruto) {
  int nivel = (int)lroundf(nivelBruto + calOffsetDb);
  if (nivel < 0) nivel = 0;
  if (nivel > 140) nivel = 140;
  return nivel;
}

// Advertencias útiles para ajustar el hardware
void avisosDeMedicion(const Medicion& med) {
  if (med.saturadas > 2) {
    Serial.printf("⚠️ Señal saturada (%lu muestras al tope): bajar la ganancia del MAX4466 (trimmer) y recalibrar\n",
                  (unsigned long)med.saturadas);
  }
  if (med.atrasadas > MUESTRAS_TOTALES / 100) {
    Serial.printf("⚠️ Muestreo lento (%lu de %lu muestras atrasadas): poner PERIODO_MUESTREO_US en 125 en filtro_a.h\n",
                  (unsigned long)med.atrasadas, (unsigned long)MUESTRAS_TOTALES);
  }
}

// ------------------------------------------------------------
// COMANDOS POR MONITOR SERIE (calibración)
// ------------------------------------------------------------
// Configurar el Monitor Serie en "Nueva línea" y 115200 baudios.
//   ayuda       muestra esta lista
//   m           mide 1 segundo y muestra nivel bruto, calibrado y avisos
//   cal <dB>    mide ahora y calibra: offset = <dB de referencia> - nivel bruto medido
//   offset      muestra el offset actual
//   reset       vuelve al offset por defecto
void procesarComandosSerie() {
  if (!Serial.available()) return;

  String linea = Serial.readStringUntil('\n');
  linea.trim();
  linea.toLowerCase();
  if (linea.length() == 0) return;

  if (linea == "ayuda") {
    Serial.println("Comandos: ayuda | m | cal <dB> | offset | reset");
  } else if (linea == "m") {
    Medicion med = medirLAeq();
    Serial.printf("📏 bruto %.1f dB  ->  %d dB(A) (offset %+.1f)\n", med.nivelBruto,
                  nivelCalibrado(med.nivelBruto), calOffsetDb);
    avisosDeMedicion(med);
  } else if (linea.startsWith("cal ")) {
    float referencia = linea.substring(4).toFloat();
    if (referencia < 30.0f || referencia > 130.0f) {
      Serial.println("❌ Referencia inválida: usar un valor entre 30 y 130, por ejemplo: cal 78");
      return;
    }
    Serial.printf("🎚️ Midiendo 1 s con referencia %.1f dB(A)... mantener el sonido estable\n", referencia);
    Medicion med = medirLAeq();
    avisosDeMedicion(med);
    if (med.saturadas > 2) {
      Serial.println("❌ Calibración cancelada: la señal está saturada. Bajar la ganancia y repetir.");
      return;
    }
    calOffsetDb = referencia - med.nivelBruto;
    prefs.putFloat("calOffset", calOffsetDb);
    Serial.printf("✅ Calibrado y guardado: bruto %.1f dB, offset %+.1f dB\n", med.nivelBruto, calOffsetDb);
  } else if (linea == "offset") {
    Serial.printf("🎚️ Offset actual: %+.1f dB\n", calOffsetDb);
  } else if (linea == "reset") {
    prefs.remove("calOffset");
    calOffsetDb = CAL_OFFSET_POR_DEFECTO;
    Serial.printf("↩️ Offset restablecido al valor por defecto: %+.1f dB\n", calOffsetDb);
  } else {
    Serial.println("❓ Comando no reconocido. Escribir: ayuda");
  }
}
