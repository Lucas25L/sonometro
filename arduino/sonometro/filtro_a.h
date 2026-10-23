// filtro_a.h - Núcleo de medición de nivel sonoro con ponderación A (dB(A)).
//
// C++ puro (sin dependencias de Arduino) para poder probarse también en una PC.
// Archivo generado a partir del diseño analógico de la norma IEC 61672 (polos en
// 20,6 / 107,7 / 737,9 / 12194 Hz) transformado a digital (bilineal) y llevado a
// 0 dB en 1 kHz. Error contra la tabla de la norma: <= 0,3 dB hasta 2 kHz.
//
// El nivel que devuelve es "bruto": 20*log10(RMS de la señal ponderada, en cuentas
// del ADC). Para convertirlo a dB(A) reales hay que sumarle el offset de calibración.
#pragma once

#include <math.h>
#include <stdint.h>

// Período entre muestras en microsegundos. 62 us ~ 16,13 kHz (recomendado).
// Si el monitor serie avisa "muestreo lento", cambiar a 125 (8 kHz).
#ifndef PERIODO_MUESTREO_US
#define PERIODO_MUESTREO_US 62
#endif

// Descarte inicial (deja que se estabilicen los filtros) y ventana de medición.
#define DESCARTE_US 100000UL
#define VENTANA_US 1000000UL

#define MUESTRAS_DESCARTE (DESCARTE_US / PERIODO_MUESTREO_US)
#define MUESTRAS_VENTANA (VENTANA_US / PERIODO_MUESTREO_US)
#define MUESTRAS_TOTALES (MUESTRAS_DESCARTE + MUESTRAS_VENTANA)

// Coeficientes {b0, b1, b2, a1, a2} de 3 secciones biquad (a0 = 1).
#if PERIODO_MUESTREO_US == 62
// fs = 16129.03 Hz
static const float COEF_A[3][5] = {
    {5.272804335e-01f, 1.054560867e+00f, 5.272804335e-01f, 8.148760121e-01f, 1.660057288e-01f},
    {1.000000000e+00f, -2.000000000e+00f, 1.000000000e+00f, -1.707604328e+00f, 7.179274514e-01f},
    {1.000000000e+00f, -2.000000000e+00f, 1.000000000e+00f, -1.984015148e+00f, 9.840790269e-01f}
};
#elif PERIODO_MUESTREO_US == 125
// fs = 8000.00 Hz
static const float COEF_A[3][5] = {
    {6.193487339e-01f, 1.238697468e+00f, 6.193487339e-01f, 1.038180498e-01f, -3.604174382e-01f},
    {1.000000000e+00f, -2.000000000e+00f, 1.000000000e+00f, -2.643824308e-01f, -6.014034617e-01f},
    {1.000000000e+00f, -2.000000000e+00f, 1.000000000e+00f, -1.967902812e+00f, 9.681603694e-01f}
};
#else
#error "PERIODO_MUESTREO_US debe ser 62 o 125 (no hay coeficientes para otro valor)"
#endif

// Seguidor de continua: filtro pasa-bajos de ~5 Hz que estima el nivel de reposo del
// micrófono (~1,65 V). Se resta antes del filtro A.
#define ALFA_DC 0.002f

class MedidorA {
 public:
  MedidorA() { reiniciar(); }

  void reiniciar() {
    for (int s = 0; s < 3; s++) z1_[s] = z2_[s] = 0.0f;
    dc_ = 0.0f;
    primera_ = true;
    contadas_ = 0;
    utiles_ = 0;
    suma_ = 0.0f;
  }

  // Procesa una muestra cruda del ADC (0..4095). Las primeras MUESTRAS_DESCARTE
  // solo estabilizan los filtros y no entran en el nivel.
  void agregar(float muestra) {
    if (primera_) {
      dc_ = muestra;
      primera_ = false;
    }
    dc_ += (muestra - dc_) * ALFA_DC;
    float y = muestra - dc_;
    for (int s = 0; s < 3; s++) {
      const float* c = COEF_A[s];
      float salida = c[0] * y + z1_[s];
      z1_[s] = c[1] * y - c[3] * salida + z2_[s];
      z2_[s] = c[2] * y - c[4] * salida;
      y = salida;
    }
    contadas_++;
    if (contadas_ > MUESTRAS_DESCARTE) {
      suma_ += y * y;
      utiles_++;
    }
  }

  uint32_t muestrasUtiles() const { return utiles_; }

  // Nivel bruto en dB (re 1 cuenta RMS ponderada A). Devuelve 0 si no hay datos.
  float nivelBrutoDb() const {
    if (utiles_ == 0) return 0.0f;
    float ms = suma_ / (float)utiles_;
    return 10.0f * log10f(ms + 1e-9f);
  }

 private:
  float z1_[3], z2_[3];
  float dc_;
  bool primera_;
  uint32_t contadas_, utiles_;
  float suma_;
};
