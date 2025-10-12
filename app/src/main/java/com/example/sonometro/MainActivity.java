package com.example.sonometro;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    // --- UI ---
    private TextView tvNivelActual, tvEstadoWifi, tvMin, tvMax;
    private LineChart lineChart;
    private Button btnIniciar, btnDetener, btnBorrar;
    private ImageButton btnAyuda;

    // --- Datos para el gráfico ---
    private ArrayList<Entry> entries = new ArrayList<>();
    private LineDataSet dataSet;
    private LineData lineData;
    private int index = 0; // eje X
    private int minNivel = Integer.MAX_VALUE;
    private int maxNivel = Integer.MIN_VALUE;

    // --- Firebase ---
    private DatabaseReference ruidoRef;
    private DatabaseReference controlRef;
    ProgressBar progressSync; // nueva referencia

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // --- Referencias UI ---
        tvEstadoWifi = findViewById(R.id.tvEstadoWifi);
        tvNivelActual = findViewById(R.id.tvNivelActual);
        lineChart = findViewById(R.id.lineChart);
        btnIniciar = findViewById(R.id.btnIniciar);
        btnDetener = findViewById(R.id.btnDetener);
        btnBorrar = findViewById(R.id.btnBorrar);
        btnAyuda = findViewById(R.id.btnAyuda);
        progressSync = findViewById(R.id.progressSync);
        tvMin = findViewById(R.id.tvMin);
        tvMax = findViewById(R.id.tvMax);

        // Estado inicial de la etiqueta que muestra la conexion del ESP32
        tvEstadoWifi.setText("Sincronizando con ESP32...");
        tvEstadoWifi.setTextColor(ContextCompat.getColor(this, R.color.neutro));
        progressSync.setVisibility(View.VISIBLE);
        btnIniciar.setEnabled(false);
        btnDetener.setEnabled(false);
        btnBorrar.setEnabled(false);

        // --- Configuración del gráfico ---
        setupChart();

        // --- Inicializar Firebase ---
        ruidoRef = FirebaseDatabase.getInstance().getReference("ruido");
        controlRef = FirebaseDatabase.getInstance().getReference("control");

        // --- Listener del nodo "control" ---
        controlRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                //ocultar indicador de carga cuando llega la primera respuesta
                progressSync.setVisibility(View.GONE);

                if (!snapshot.exists()) {
                    tvEstadoWifi.setText("ESP32 desconectado");
                    tvEstadoWifi.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.Desconectado));
                    btnIniciar.setEnabled(false);
                    btnDetener.setEnabled(false);
                    btnBorrar.setEnabled(false);
                    return;
                }

                // Leer monitoreo y lastSeen
                Boolean monitoreo = snapshot.child("monitoreo").getValue(Boolean.class);
                Object lastSeenObj = snapshot.child("lastSeen").getValue();

                long lastSeen = 0;
                if (lastSeenObj instanceof Long) {
                    lastSeen = (Long) lastSeenObj;
                } else if (lastSeenObj instanceof Double) {
                    lastSeen = ((Double) lastSeenObj).longValue();
                }

                long now = System.currentTimeMillis();
                boolean espOnline = (System.currentTimeMillis() - lastSeen) < 10_000; // umbral 10 s

                if (espOnline) {
                    tvEstadoWifi.setText("ESP32 conectado");
                    tvEstadoWifi.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.Conectado));
                    btnIniciar.setEnabled(true);
                    btnDetener.setEnabled(true);
                    btnBorrar.setEnabled(true);
                } else {
                    tvEstadoWifi.setText("ESP32 desconectado");
                    tvEstadoWifi.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.Desconectado));
                    btnIniciar.setEnabled(false);
                    btnDetener.setEnabled(false);
                    btnBorrar.setEnabled(false);
                }

                // Estado del monitoreo (opcional)
                if (monitoreo != null && monitoreo) {
                    Log.d("CONTROL", "Monitoreo activo");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("FIREBASE", "Error en controlRef: " + error.getMessage());
            }
        });

        // --- Listener del nodo "ruido" ---
        ruidoRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                Integer nivel = snapshot.child("nivel_ruido").getValue(Integer.class);
                Long timestamp = snapshot.child("fecha_hora").getValue(Long.class);

                if (nivel != null && timestamp != null) {
                    tvNivelActual.setText("Nivel de ruido actual: " + nivel + " dB");
                    addEntryToChart(nivel, timestamp);
                }
            }

            @Override public void onChildChanged(DataSnapshot snapshot, String prev) {}
            @Override public void onChildRemoved(DataSnapshot snapshot) {}
            @Override public void onChildMoved(DataSnapshot snapshot, String prev) {}
            @Override public void onCancelled(DatabaseError error) {
                Log.e("FIREBASE", "Error leyendo ruido: " + error.getMessage());
            }
        });

        // --- Botón Iniciar ---
        btnIniciar.setOnClickListener(v -> {
            controlRef.child("monitoreo").setValue(true);
            Log.d("CONTROL", "Monitoreo iniciado desde la app");
            ruidoRef.removeValue(); // Limpia los datos previos automáticamente
        });

        // --- Botón Detener ---
        btnDetener.setOnClickListener(v -> {
            controlRef.child("monitoreo").setValue(false);
            Log.d("CONTROL", "Monitoreo detenido desde la app");
        });

        // --- Botón Borrar ---
        btnBorrar.setOnClickListener(v -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Confirmar borrado")
                    .setMessage("¿Deseas eliminar todos los datos del monitoreo actual? Esta acción no se puede deshacer.")
                    .setPositiveButton("Sí, borrar", (dialog, which) -> {

                        // Limpiar tv Nivel actual
                        tvNivelActual.setText("Nivel de ruido actual: -- dB");
                        // Limpiar el gráfico
                        entries.clear();
                        index = 0;
                        lineChart.clear();
                        setupChart();

                        // Limpiar min y max
                        minNivel = Integer.MAX_VALUE;
                        maxNivel = Integer.MIN_VALUE;
                        tvMin.setText("Min: -- dB");
                        tvMax.setText("Max: -- dB");

                        // Eliminar datos del nodo "ruido" en Firebase
                        ruidoRef.removeValue().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d("FIREBASE", "Datos de 'ruido' eliminados correctamente.");
                                Toast.makeText(MainActivity.this, "Datos eliminados.", Toast.LENGTH_SHORT).show();
                            } else {
                                Log.e("FIREBASE", "Error al eliminar datos: " + task.getException());
                                Toast.makeText(MainActivity.this, "Error al borrar datos.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                    .show();
        });

        // --- Botón Ayuda ---
        btnAyuda.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AyudaActivity.class);
            startActivity(intent);
        });
    }

    // --- Configuración inicial del gráfico ---
    private void setupChart() {
        dataSet = new LineDataSet(entries, "Nivel de Ruido (dB)");
        dataSet.setColor(getColor(R.color.primary_green));
        dataSet.setValueTextColor(getColor(R.color.black));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircles(true);
        dataSet.setDrawValues(false);

        CustomMarkerView marker = new CustomMarkerView(this, R.layout.custom_marker);
        lineChart.setMarker(marker);

        lineData = new LineData(dataSet);
        lineChart.setData(lineData);

        Description description = new Description();
        lineChart.setDescription(description);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        lineChart.invalidate();
    }

    // --- Agregar nueva entrada al gráfico ---
    private void addEntryToChart(int nivel, Long timestamp) {
        entries.add(new Entry(index++, nivel, timestamp));

        dataSet.notifyDataSetChanged();
        lineData.notifyDataChanged();
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();

        // actualizar valores min y max
        if (nivel < minNivel) minNivel = nivel;
        if (nivel > maxNivel) maxNivel = nivel;

        tvMin.setText("Min: " + minNivel + " dB");
        tvMax.setText("Max: " + maxNivel + " dB");
    }
}