package com.example.sonometro;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private TextView tvNivelActual;
    private LineChart lineChart;

    // Datos para el gráfico
    private ArrayList<Entry> entries = new ArrayList<>();
    private LineDataSet dataSet;
    private LineData lineData;
    private int index = 0; // eje X (simple contador)

    // Firebase
    private DatabaseReference databaseRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Referencias a la interfaz
        tvNivelActual = findViewById(R.id.tvNivelActual);
        lineChart = findViewById(R.id.lineChart);

        // Configuración inicial del gráfico
        setupChart();

        // Inicializar referencia a Firebase
        databaseRef = FirebaseDatabase.getInstance().getReference("ruido");

        // Escuchar cambios en tiempo real
        databaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    Integer nivel = child.child("nivel_ruido").getValue(Integer.class);
                    Long timestamp = child.child("fecha_hora").getValue(Long.class);

                    if (nivel != null && timestamp != null) {
                        // Mostrar en Logcat
                        Log.d("FIREBASE", "Nivel: " + nivel + " dB | Timestamp: " + timestamp);

                        // Actualizar TextView
                        tvNivelActual.setText("Nivel actual: " + nivel + " dB");

                        // Agregar al gráfico
                        addEntryToChart(nivel, timestamp);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("FIREBASE", "Error: " + error.getMessage());
            }
        });
    }
    // Configuración inicial del gráfico
    // Configuración inicial del gráfico
    private void setupChart() {
        dataSet = new LineDataSet(entries, "Nivel de Ruido (dB)");
        dataSet.setColor(getColor(R.color.purple_500));
        dataSet.setValueTextColor(getColor(R.color.black));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircles(true);
        dataSet.setDrawValues(false);

        lineData = new LineData(dataSet);
        lineChart.setData(lineData);

        // Personalización del gráfico
        Description description = new Description();
        description.setText("Mediciones en tiempo real");
        lineChart.setDescription(description);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(5000f); // 5 segundos
        xAxis.setValueFormatter((value, axis) -> {
            long millis = (long) value;
            long seconds = (millis - startTime) / 1000;
            return seconds + "s";
        });

        // Eje Y a la izquierda con decibelios
        lineChart.getAxisRight().setEnabled(false); // ocultamos eje derecho
        lineChart.getAxisLeft().setAxisMinimum(0f);  // mínimo en 0 dB
        lineChart.getAxisLeft().setAxisMaximum(120f); // máximo estimado (ajústalo según tu sensor)
        lineChart.getAxisLeft().setGranularity(10f); // saltos de 10 dB
        lineChart.getAxisLeft().setDrawGridLines(true);
        lineChart.getAxisLeft().setDrawLabels(true);

        //Marcador personalizado(cuando seleccionas un punto especifico de la grafica)
        CustomMarkerView marker = new CustomMarkerView(this, R.layout.marker_view);
        lineChart.setMarker(marker);


        lineChart.invalidate();
    }

    // Agregar nueva entrada al gráfico
    private void addEntryToChart(int nivel, Long timestamp) {
        // X = índice creciente, Y = nivel en dB
        if (startTime == 0) startTime = timestamp; // guardo inicio
        entries.add(new Entry(timestamp, nivel)); // ahora X = timestamp
        dataSet.notifyDataSetChanged();
        lineData.notifyDataChanged();
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
    }
}