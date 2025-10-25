package com.example.sonometro;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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

/**
 * Clase principal del proyecto Sonómetro.
 * <p>
 * Esta actividad se encarga de:
 * <ul>
 *     <li>Sincronizar la app con el dispositivo ESP32 a través de Firebase.</li>
 *     <li>Visualizar los niveles de ruido capturados por el sensor MAX4466.</li>
 *     <li>Mostrar los valores mínimo, máximo y actual del ruido en dB.</li>
 *     <li>Permitir iniciar, detener o borrar el monitoreo.</li>
 *     <li>Mostrar un gráfico en tiempo real con los datos obtenidos.</li>
 * </ul>
 *
 * @author Lucas
 * @version 1.0
 */
public class MainActivity extends AppCompatActivity {

    // ============================
    // ATRIBUTOS DE INTERFAZ
    // ============================

    /** Muestra el nivel actual de ruido en decibelios (dB). */
    private TextView tvNivelActual;

    /** Indica el estado de conexión con el ESP32. */
    private TextView tvEstadoWifi;

    /** Muestra el nivel mínimo registrado durante la sesión. */
    private TextView tvMin;

    /** Muestra el nivel máximo registrado durante la sesión. */
    private TextView tvMax;

    /** Gráfico en tiempo real de los niveles de ruido. */
    private LineChart lineChart;

    /** Botón para iniciar el monitoreo. */
    private Button btnIniciar;

    /** Botón para detener el monitoreo. */
    private Button btnDetener;

    /** Botón para borrar los datos del monitoreo. */
    private Button btnBorrar;

    /** Botón de acceso a la pantalla de ayuda. */
    private ImageButton btnAyuda;

    /** Indicador visual de sincronización con Firebase/ESP32. */
    private ProgressBar progressSync;


    // ============================
    // DATOS Y CONFIGURACIÓN DEL GRÁFICO
    // ============================

    /** Lista de puntos de datos (dB) para el gráfico. */
    private ArrayList<Entry> entries = new ArrayList<>();

    /** Conjunto de datos del gráfico (línea principal). */
    private LineDataSet dataSet;

    /** Objeto principal que contiene los datos del gráfico. */
    private LineData lineData;

    /** Índice para el eje X (tiempo). */
    private int index = 0;

    /** Nivel mínimo detectado en la sesión actual. */
    private int minNivel = Integer.MAX_VALUE;

    /** Nivel máximo detectado en la sesión actual. */
    private int maxNivel = Integer.MIN_VALUE;


    // ============================
    // REFERENCIAS A FIREBASE
    // ============================

    /** Referencia al nodo "ruido" en Firebase donde se almacenan los niveles capturados. */
    private DatabaseReference ruidoRef;

    /** Referencia al nodo "control" en Firebase para manejar el estado del monitoreo. */
    private DatabaseReference controlRef;


    // ============================
    // MÉTODOS DE CICLO DE VIDA
    // ============================

    /**
     * Método llamado al crear la actividad principal.
     * <p>
     * Configura la interfaz, inicializa Firebase, prepara el gráfico y establece los listeners
     * para monitorear la conexión con el ESP32 y los niveles de ruido en tiempo real.
     *
     * @param savedInstanceState estado previo de la aplicación (si existe)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // --- Inicialización de vistas ---
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

        // Estado inicial
        tvEstadoWifi.setText("Sincronizando con ESP32...");
        tvEstadoWifi.setTextColor(ContextCompat.getColor(this, R.color.neutro));
        progressSync.setVisibility(android.view.View.VISIBLE);
        btnIniciar.setEnabled(false);
        btnDetener.setEnabled(false);
        btnBorrar.setEnabled(false);

        // Configuración del gráfico
        setupChart();

        // Inicialización de Firebase
        ruidoRef = FirebaseDatabase.getInstance().getReference("ruido");
        controlRef = FirebaseDatabase.getInstance().getReference("control");

        // Listener del nodo "control" (conexión y monitoreo)
        controlRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                progressSync.setVisibility(android.view.View.GONE);

                if (!snapshot.exists()) {
                    actualizarEstadoDesconectado();
                    return;
                }

                Boolean monitoreo = snapshot.child("monitoreo").getValue(Boolean.class);
                Object lastSeenObj = snapshot.child("lastSeen").getValue();

                long lastSeen = 0;
                if (lastSeenObj instanceof Long) lastSeen = (Long) lastSeenObj;
                else if (lastSeenObj instanceof Double) lastSeen = ((Double) lastSeenObj).longValue();

                boolean espOnline = (System.currentTimeMillis() - lastSeen) < 10_000;

                if (espOnline) actualizarEstadoConectado();
                else actualizarEstadoDesconectado();

                if (monitoreo != null && monitoreo) {
                    Log.d("CONTROL", "Monitoreo activo");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("FIREBASE", "Error en controlRef: " + error.getMessage());
            }
        });

        // Listener del nodo "ruido" (datos en tiempo real)
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

        // Configuración de botones
        configurarBotones();
    }


    // ============================
    // MÉTODOS AUXILIARES
    // ============================

    /**
     * Configura la apariencia y los parámetros iniciales del gráfico de ruido.
     */
    private void setupChart() {
        dataSet = new LineDataSet(entries, "Nivel de Ruido (dB)");
        dataSet.setColor(getColor(R.color.primary_green));
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(true);
        dataSet.setCircleRadius(3f);
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

    /**
     * Agrega un nuevo punto (nivel de ruido) al gráfico y actualiza los valores
     * mínimos y máximos registrados.
     *
     * @param nivel     nivel de ruido medido en dB
     * @param timestamp marca temporal asociada al dato
     */
    private void addEntryToChart(int nivel, Long timestamp) {
        entries.add(new Entry(index++, nivel, timestamp));

        dataSet.notifyDataSetChanged();
        lineData.notifyDataChanged();
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();

        if (nivel < minNivel) minNivel = nivel;
        if (nivel > maxNivel) maxNivel = nivel;

        tvMin.setText("Min: " + minNivel + " dB");
        tvMax.setText("Max: " + maxNivel + " dB");
    }

    /**
     * Configura la lógica de los botones de control de monitoreo.
     */
    private void configurarBotones() {
        btnIniciar.setOnClickListener(v -> {
            controlRef.child("monitoreo").setValue(true);
            ruidoRef.removeValue();
            Log.d("CONTROL", "Monitoreo iniciado desde la app");
        });

        btnDetener.setOnClickListener(v -> {
            controlRef.child("monitoreo").setValue(false);
            Log.d("CONTROL", "Monitoreo detenido desde la app");
        });

        btnBorrar.setOnClickListener(v -> mostrarDialogoBorrado());
        btnAyuda.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AyudaActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Muestra un diálogo de confirmación antes de borrar los datos almacenados en Firebase.
     */
    private void mostrarDialogoBorrado() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Confirmar borrado")
                .setMessage("¿Deseas eliminar todos los datos del monitoreo actual? Esta acción no se puede deshacer.")
                .setPositiveButton("Sí, borrar", (dialog, which) -> borrarDatos())
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Limpia los datos del gráfico, restablece los valores mínimos y máximos,
     * y elimina los datos del nodo "ruido" en Firebase.
     */
    private void borrarDatos() {
        tvNivelActual.setText("Nivel de ruido actual: -- dB");
        entries.clear();
        index = 0;
        lineChart.clear();
        setupChart();

        minNivel = Integer.MAX_VALUE;
        maxNivel = Integer.MIN_VALUE;
        tvMin.setText("Min: -- dB");
        tvMax.setText("Max: -- dB");

        ruidoRef.removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("FIREBASE", "Datos de 'ruido' eliminados correctamente.");
                Toast.makeText(MainActivity.this, "Datos eliminados.", Toast.LENGTH_SHORT).show();
            } else {
                Log.e("FIREBASE", "Error al eliminar datos: " + task.getException());
                Toast.makeText(MainActivity.this, "Error al borrar datos.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Actualiza la interfaz cuando el ESP32 está conectado. */
    private void actualizarEstadoConectado() {
        tvEstadoWifi.setText("ESP32 conectado");
        tvEstadoWifi.setTextColor(ContextCompat.getColor(this, R.color.Conectado));
        btnIniciar.setEnabled(true);
        btnDetener.setEnabled(true);
        btnBorrar.setEnabled(true);
    }

    /** Actualiza la interfaz cuando el ESP32 está desconectado. */
    private void actualizarEstadoDesconectado() {
        tvEstadoWifi.setText("ESP32 desconectado");
        tvEstadoWifi.setTextColor(ContextCompat.getColor(this, R.color.Desconectado));
        btnIniciar.setEnabled(false);
        btnDetener.setEnabled(false);
        btnBorrar.setEnabled(false);
    }
}
