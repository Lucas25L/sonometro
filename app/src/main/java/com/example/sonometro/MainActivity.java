package com.example.sonometro;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Clase principal del proyecto Sonómetro.
 * <p>
 * Esta actividad se encarga de:
 * <ul>
 *     <li>Autenticarse (sesión anónima) y sincronizar la app con el ESP32 a través de Firebase.</li>
 *     <li>Visualizar los niveles de ruido capturados por el sensor MAX4466.</li>
 *     <li>Mostrar los valores mínimo, máximo y actual del ruido en dB.</li>
 *     <li>Permitir iniciar, detener o borrar el monitoreo.</li>
 *     <li>Mostrar un gráfico en tiempo real con la hora real de cada medición.</li>
 * </ul>
 *
 * @author Lucas
 * @version 2.0 (Fase 1)
 */
public class MainActivity extends AppCompatActivity {

    // ============================
    // CONSTANTES
    // ============================

    /** Cantidad máxima de lecturas que se descargan y se mantienen en el gráfico. */
    private static final int MAX_LECTURAS = 300;

    /** Si el último "lastSeen" del ESP32 es más viejo que esto, se lo considera desconectado. */
    private static final long TIMEOUT_ESP_MS = 10_000;

    /** Cada cuánto se reevalúa el estado de conexión del ESP32. */
    private static final long CHEQUEO_MS = 2_000;

    /** Ventana visible del gráfico, en segundos. */
    private static final float VENTANA_VISIBLE_SEG = 60f;

    // ============================
    // ATRIBUTOS DE INTERFAZ
    // ============================

    private TextView tvNivelActual;
    private TextView tvEstadoWifi;
    private TextView tvMin;
    private TextView tvMax;
    private LineChart lineChart;
    private Button btnIniciar;
    private Button btnDetener;
    private Button btnBorrar;
    private ImageButton btnAyuda;
    private ProgressBar progressSync;

    // ============================
    // DATOS Y CONFIGURACIÓN DEL GRÁFICO
    // ============================

    /** Puntos del gráfico: X = segundos desde la primera lectura, Y = dB, data = timestamp (ms). */
    private final ArrayList<Entry> entries = new ArrayList<>();
    private LineDataSet dataSet;
    private LineData lineData;

    /** Timestamp (ms) de la primera lectura mostrada; -1 si todavía no hay lecturas. */
    private long baseTimestamp = -1;

    private int minNivel = Integer.MAX_VALUE;
    private int maxNivel = Integer.MIN_VALUE;

    // ============================
    // FIREBASE Y CONEXIÓN CON EL ESP32
    // ============================

    private DatabaseReference ruidoRef;
    private DatabaseReference controlRef;
    private DatabaseReference offsetRef;
    private Query ruidoQuery;

    private ValueEventListener controlListener;
    private ValueEventListener offsetListener;
    private ChildEventListener ruidoListener;

    /** Último "lastSeen" informado por el ESP32 (ms, hora del servidor/NTP). */
    private long lastSeenMs = 0;

    /** Diferencia entre el reloj del servidor de Firebase y el del celular (ms). */
    private long serverOffsetMs = 0;

    /** Indica si ya llegó la primera respuesta del nodo "control". */
    private boolean controlRecibido = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * Reevalúa periódicamente si el ESP32 sigue conectado. Es necesario porque el listener
     * de Firebase solo se dispara cuando hay cambios: si el ESP32 se apaga, no llega ninguno.
     */
    private final Runnable chequeoConexion = new Runnable() {
        @Override
        public void run() {
            if (controlRecibido) {
                long ahora = System.currentTimeMillis() + serverOffsetMs;
                boolean online = lastSeenMs > 0 && (ahora - lastSeenMs) < TIMEOUT_ESP_MS;
                if (online) actualizarEstadoConectado();
                else actualizarEstadoDesconectado();
            }
            handler.postDelayed(this, CHEQUEO_MS);
        }
    };

    // ============================
    // CICLO DE VIDA
    // ============================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

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
        progressSync.setVisibility(View.VISIBLE);
        btnIniciar.setEnabled(false);
        btnDetener.setEnabled(false);
        btnBorrar.setEnabled(false);

        setupChart();

        FirebaseDatabase db = FirebaseDatabase.getInstance();
        ruidoRef = db.getReference("ruido");
        controlRef = db.getReference("control");
        offsetRef = db.getReference(".info/serverTimeOffset");

        configurarBotones();

        // Las reglas de Firebase exigen sesión iniciada: autenticar y luego escuchar
        autenticarYEscuchar();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(chequeoConexion);
        if (controlListener != null) controlRef.removeEventListener(controlListener);
        if (offsetListener != null) offsetRef.removeEventListener(offsetListener);
        if (ruidoListener != null && ruidoQuery != null) ruidoQuery.removeEventListener(ruidoListener);
    }

    // ============================
    // AUTENTICACIÓN Y LISTENERS
    // ============================

    /**
     * Inicia sesión anónima en Firebase Auth (si no hay una activa) y, una vez
     * autenticada la app, comienza a escuchar los datos.
     */
    private void autenticarYEscuchar() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            iniciarListeners();
            return;
        }
        auth.signInAnonymously()
                .addOnSuccessListener(this, result -> iniciarListeners())
                .addOnFailureListener(this, e -> {
                    Log.e("AUTH", "Error de autenticación: " + e.getMessage());
                    progressSync.setVisibility(View.GONE);
                    tvEstadoWifi.setText("Error de autenticación");
                    tvEstadoWifi.setTextColor(ContextCompat.getColor(this, R.color.Desconectado));
                });
    }

    /** Registra los listeners de Firebase (requiere sesión iniciada). */
    private void iniciarListeners() {
        // Diferencia de reloj entre el celular y el servidor (para juzgar bien el "lastSeen")
        offsetListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Object o = snapshot.getValue();
                if (o instanceof Number) serverOffsetMs = ((Number) o).longValue();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("FIREBASE", "Error leyendo serverTimeOffset: " + error.getMessage());
            }
        };
        offsetRef.addValueEventListener(offsetListener);

        // Nodo "control": guarda el último "lastSeen" del ESP32
        controlListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                controlRecibido = true;
                progressSync.setVisibility(View.GONE);

                Object o = snapshot.child("lastSeen").getValue();
                lastSeenMs = (o instanceof Number) ? ((Number) o).longValue() : 0;
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("FIREBASE", "Error en controlRef: " + error.getMessage());
            }
        };
        controlRef.addValueEventListener(controlListener);

        // Nodo "ruido": solo las últimas MAX_LECTURAS, ordenadas por hora
        ruidoQuery = ruidoRef.orderByChild("fecha_hora").limitToLast(MAX_LECTURAS);
        ruidoListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                Object n = snapshot.child("nivel_ruido").getValue();
                Object t = snapshot.child("fecha_hora").getValue();

                if (n instanceof Number && t instanceof Number) {
                    int nivel = ((Number) n).intValue();
                    long timestamp = ((Number) t).longValue();
                    tvNivelActual.setText("Nivel de ruido actual: " + nivel + " dB");
                    addEntryToChart(nivel, timestamp);
                }
            }

            @Override public void onChildChanged(DataSnapshot snapshot, String prev) {}
            @Override public void onChildRemoved(DataSnapshot snapshot) {}
            @Override public void onChildMoved(DataSnapshot snapshot, String prev) {}

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("FIREBASE", "Error leyendo ruido: " + error.getMessage());
            }
        };
        ruidoQuery.addChildEventListener(ruidoListener);

        handler.post(chequeoConexion);
    }

    // ============================
    // GRÁFICO
    // ============================

    /** Configura la apariencia y los parámetros iniciales del gráfico de ruido. */
    private void setupChart() {
        dataSet = new LineDataSet(entries, "Nivel de Ruido (dB)");
        dataSet.setColor(getColor(R.color.primary_green));
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(true);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawValues(false);

        lineChart.setMarker(new CustomMarkerView(this, R.layout.custom_marker));

        lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        lineChart.getDescription().setEnabled(false);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        // El eje X son segundos desde la primera lectura; se muestra como hora real (HH:mm:ss)
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat formato = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

            @Override
            public String getFormattedValue(float value) {
                if (baseTimestamp < 0) return "";
                return formato.format(new Date(baseTimestamp + (long) (value * 1000)));
            }
        });

        lineChart.invalidate();
    }

    /**
     * Agrega un punto al gráfico (manteniendo como máximo {@link #MAX_LECTURAS})
     * y actualiza los valores mínimo y máximo de la sesión.
     *
     * @param nivel     nivel de ruido medido en dB
     * @param timestamp hora de la medición (ms desde 1970)
     */
    private void addEntryToChart(int nivel, long timestamp) {
        if (baseTimestamp < 0) baseTimestamp = timestamp;
        float x = (timestamp - baseTimestamp) / 1000f;

        entries.add(new Entry(x, nivel, timestamp));
        if (entries.size() > MAX_LECTURAS) entries.remove(0);

        dataSet.notifyDataSetChanged();
        lineData.notifyDataChanged();
        lineChart.notifyDataSetChanged();
        lineChart.setVisibleXRangeMaximum(VENTANA_VISIBLE_SEG);
        lineChart.moveViewToX(x);

        if (nivel < minNivel) minNivel = nivel;
        if (nivel > maxNivel) maxNivel = nivel;

        tvMin.setText("Min: " + minNivel + " dB");
        tvMax.setText("Max: " + maxNivel + " dB");
    }

    /** Vacía el gráfico y los indicadores locales (no toca Firebase). */
    private void limpiarGrafico() {
        tvNivelActual.setText("Nivel de ruido actual: -- dB");
        entries.clear();
        baseTimestamp = -1;
        lineChart.clear();
        setupChart();

        minNivel = Integer.MAX_VALUE;
        maxNivel = Integer.MIN_VALUE;
        tvMin.setText("Min: -- dB");
        tvMax.setText("Max: -- dB");
    }

    // ============================
    // BOTONES
    // ============================

    /** Configura la lógica de los botones de control de monitoreo. */
    private void configurarBotones() {
        btnIniciar.setOnClickListener(v -> {
            // Primero se limpia lo anterior y recién después se activa el monitoreo
            limpiarGrafico();
            ruidoRef.removeValue().addOnCompleteListener(task ->
                    controlRef.child("monitoreo").setValue(true));
            Log.d("CONTROL", "Monitoreo iniciado desde la app");
        });

        btnDetener.setOnClickListener(v -> {
            controlRef.child("monitoreo").setValue(false);
            Log.d("CONTROL", "Monitoreo detenido desde la app");
        });

        btnBorrar.setOnClickListener(v -> mostrarDialogoBorrado());
        btnAyuda.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, AyudaActivity.class)));
    }

    /** Muestra un diálogo de confirmación antes de borrar los datos almacenados en Firebase. */
    private void mostrarDialogoBorrado() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Confirmar borrado")
                .setMessage("¿Deseas eliminar todos los datos del monitoreo actual? Esta acción no se puede deshacer.")
                .setPositiveButton("Sí, borrar", (dialog, which) -> borrarDatos())
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /** Limpia el gráfico y elimina los datos del nodo "ruido" en Firebase. */
    private void borrarDatos() {
        limpiarGrafico();
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

    // ============================
    // ESTADO DE CONEXIÓN
    // ============================

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
