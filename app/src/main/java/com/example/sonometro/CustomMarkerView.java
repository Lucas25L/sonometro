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

public class CustomMarkerView extends MarkerView {

    private final TextView tvNivelMarker;
    private final TextView tvHoraMarker;
    private final SimpleDateFormat timeFormat;

    public CustomMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        tvNivelMarker = findViewById(R.id.tvNivelMarker);
        tvHoraMarker = findViewById(R.id.tvHoraMarker);
        timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        int nivel = (int) e.getY();
        long timestamp = (long) e.getData(); // lo guardamos más adelante
        String hora = timeFormat.format(new Date(timestamp));

        tvNivelMarker.setText(nivel + " dB");
        tvHoraMarker.setText(hora);

        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        // centra el marcador sobre el punto
        return new MPPointF(-(getWidth() / 2f), -getHeight() - 10);
    }
}

