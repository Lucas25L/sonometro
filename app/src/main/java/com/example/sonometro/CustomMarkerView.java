package com.example.sonometro;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Vista personalizada para mostrar información detallada de cada punto del gráfico.
 * <p>
 * Este marcador se utiliza dentro del gráfico de niveles de ruido (MPAndroidChart)
 * para mostrar el valor del nivel en decibelios (dB) y la hora correspondiente a cada medición.
 * </p>
 *
 * <p>Diseño asociado: {@code custom_marker.xml}</p>
 *
 * @author Lucas Lopez, Juan Arellano y Tania Bacik
 * @version 1.0
 * @see com.github.mikephil.charting.charts.LineChart
 * @see com.github.mikephil.charting.components.MarkerView
 */
public class CustomMarkerView extends MarkerView {

    /** Texto que muestra el nivel de ruido (en dB) cuando se selecciona un punto del gráfico. */
    private final TextView tvNivelMarker;

    /** Texto que muestra la hora correspondiente al dato del gráfico seleccionado. */
    private final TextView tvHoraMarker;

    /** Formateador de tiempo para mostrar la hora en formato HH:mm:ss. */
    private final SimpleDateFormat timeFormat;

    /**
     * Constructor del marcador personalizado.
     *
     * @param context         contexto actual de la aplicación
     * @param layoutResource  recurso de layout que define la estructura visual del marcador
     */
    public CustomMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        tvNivelMarker = findViewById(R.id.tvNivelMarker);
        tvHoraMarker = findViewById(R.id.tvHoraMarker);
        timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    }

    /**
     * Actualiza el contenido del marcador cada vez que el usuario selecciona un punto del gráfico.
     * <p>
     * Extrae los datos del {@link Entry} seleccionado (nivel y timestamp) y los muestra
     * en las etiquetas de texto del marcador.
     * </p>
     *
     * @param e          entrada seleccionada del gráfico, que contiene el nivel de ruido y la hora
     * @param highlight  información de resaltado proporcionada por MPAndroidChart
     */
    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        int nivel = (int) e.getY();
        long timestamp = (long) e.getData(); // recupera el valor temporal asociado al punto
        String hora = timeFormat.format(new Date(timestamp));

        tvNivelMarker.setText(nivel + " dB");
        tvHoraMarker.setText(hora);

        super.refreshContent(e, highlight);
    }

    /**
     * Define la posición del marcador respecto al punto del gráfico seleccionado.
     * <p>
     * En este caso, se centra horizontalmente y se coloca ligeramente por encima del punto.
     * </p>
     *
     * @return coordenadas de desplazamiento del marcador
     */
    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2f), -getHeight() - 10);
    }
}