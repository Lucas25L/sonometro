package com.example.sonometro;

import java.util.Calendar;

/**
 * Motor de evaluación contra la normativa de ruido ambiental de Resistencia
 * (Ordenanza 3593/98 del Concejo Municipal).
 * <p>
 * Es Java puro (sin dependencias de Android) para poder probarse fuera del teléfono.
 * </p>
 *
 * <p>Fuente de los valores: tabla del Art. 12 de la ordenanza (copia fiel del original, 23/04/1998),
 * columnas "ruidos ambientes", "picos 7-60 p/h (frecuentes)" y "picos 1-6 p/h (poco frecuentes)".
 * La tabla del original ordena las columnas NOCHE y DÍA; acá se guardan como {día, noche}.</p>
 *
 * <p>Qué dice la ordenanza y se usa acá: el día va de 06:00 a 22:00 y la noche de 22:00 a 06:00;
 * hay cuatro ámbitos (I hospitalario/educativo, II residencial, III comercial/entretenimiento,
 * IV industrial); las mediciones se expresan en dB(A); y los niveles máximos no pueden ser
 * excedidos dentro de cualquier predio vecino (Art. 12).</p>
 *
 * <p>La app evalúa contra el límite de <b>ruido ambiente</b>. Los límites de picos se guardan
 * como datos, pero no se evalúan solos porque dependen de contar cuántas veces por hora ocurre
 * el pico.</p>
 *
 * @version 2.0.0
 */
public final class Normativa {

    private Normativa() {
    }

    /** Ámbitos de la ordenanza (Art. 13). */
    public enum Ambito {
        I("Ámbito I – Hospitalario / educativo", "Ámbito I"),
        II("Ámbito II – Residencial", "Ámbito II"),
        III("Ámbito III – Comercial / entretenimiento", "Ámbito III"),
        IV("Ámbito IV – Industrial", "Ámbito IV");

        /** Texto largo, para el selector. */
        public final String etiqueta;
        /** Texto corto, para el veredicto. */
        public final String corto;

        Ambito(String etiqueta, String corto) {
            this.etiqueta = etiqueta;
            this.corto = corto;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    /** Franja horaria según la ordenanza. */
    public enum Franja {
        DIA("día"),
        NOCHE("noche");

        public final String etiqueta;

        Franja(String etiqueta) {
            this.etiqueta = etiqueta;
        }
    }

    /** Resultado de comparar un nivel con el límite. */
    public enum Estado {
        /** No hay límite para esa combinación (celda sin dato): no se puede opinar. */
        SIN_LIMITE,
        /** El nivel no supera el límite. */
        DENTRO,
        /** El nivel supera el límite. */
        SUPERADO
    }

    /** Resultado completo de una evaluación. */
    public static final class Evaluacion {
        public final Estado estado;
        /** Límite aplicado en dB(A); {@link Double#NaN} si no hay. */
        public final double limite;
        public final Ambito ambito;
        public final Franja franja;

        Evaluacion(Estado estado, double limite, Ambito ambito, Franja franja) {
            this.estado = estado;
            this.limite = limite;
            this.ambito = ambito;
            this.franja = franja;
        }
    }

    /** Hora (inclusive) en que empieza el día. */
    public static final int HORA_INICIO_DIA = 6;
    /** Hora (exclusive) en que termina el día y empieza la noche. */
    public static final int HORA_FIN_DIA = 22;

    /**
     * Límites de ruido ambiente en dB(A) (Art. 12, ordenanza 3593/98).
     * Fila = ámbito (I, II, III, IV); columna = franja {día, noche}.
     */
    private static final double[][] LIMITES_ORDENANZA_3593_98 = {
            /* Ámbito I   */ {45, 35},
            /* Ámbito II  */ {55, 45},
            /* Ámbito III */ {60, 50},
            /* Ámbito IV  */ {60, 50}
    };

    /** Picos frecuentes (7 a 60 por hora), dB(A) {día, noche}. Solo como dato de referencia. */
    private static final double[][] PICOS_FRECUENTES_3593_98 = {
            {50, 45}, {65, 55}, {70, 60}, {70, 60}
    };

    /** Picos poco frecuentes (1 a 6 por hora), dB(A) {día, noche}. Solo como dato de referencia. */
    private static final double[][] PICOS_POCO_FRECUENTES_3593_98 = {
            {55, 55}, {70, 65}, {75, 65}, {75, 65}
    };

    /** Devuelve la franja (día/noche) que corresponde a una hora del reloj (0 a 23). */
    public static Franja franjaDeHora(int hora) {
        return (hora >= HORA_INICIO_DIA && hora < HORA_FIN_DIA) ? Franja.DIA : Franja.NOCHE;
    }

    /** Devuelve la franja de un instante (milisegundos desde 1970), según la hora local del teléfono. */
    public static Franja franjaDeTimestamp(long timestampMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestampMs);
        return franjaDeHora(c.get(Calendar.HOUR_OF_DAY));
    }

    /** Límite vigente en dB(A), o {@link Double#NaN} si todavía no está cargado. */
    public static double limite(Ambito ambito, Franja franja) {
        return limite(LIMITES_ORDENANZA_3593_98, ambito, franja);
    }

    /** Límite de picos frecuentes (7 a 60 por hora) en dB(A). */
    public static double limitePicoFrecuente(Ambito ambito, Franja franja) {
        return limite(PICOS_FRECUENTES_3593_98, ambito, franja);
    }

    /** Límite de picos poco frecuentes (1 a 6 por hora) en dB(A). */
    public static double limitePicoPocoFrecuente(Ambito ambito, Franja franja) {
        return limite(PICOS_POCO_FRECUENTES_3593_98, ambito, franja);
    }

    /** Compara un nivel en dB(A) con el límite del ámbito y la franja indicados. */
    public static Evaluacion evaluar(double nivelDbA, Ambito ambito, Franja franja) {
        return evaluar(LIMITES_ORDENANZA_3593_98, nivelDbA, ambito, franja);
    }

    // --- Versiones con tabla explícita (permiten probar la lógica sin tocar la tabla real) ---

    static double limite(double[][] tabla, Ambito ambito, Franja franja) {
        return tabla[ambito.ordinal()][franja.ordinal()];
    }

    static Evaluacion evaluar(double[][] tabla, double nivelDbA, Ambito ambito, Franja franja) {
        double lim = limite(tabla, ambito, franja);
        Estado estado;
        if (Double.isNaN(lim)) {
            estado = Estado.SIN_LIMITE;
        } else if (nivelDbA > lim) {
            estado = Estado.SUPERADO;
        } else {
            estado = Estado.DENTRO;
        }
        return new Evaluacion(estado, lim, ambito, franja);
    }
}
