package com.example.sonometro;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Actividad de ayuda del proyecto Sonómetro.
 * <p>
 * Esta pantalla proporciona información o instrucciones de uso para el usuario.
 * Incluye una flecha en la barra de título que permite regresar a la actividad principal.
 *
 * <p>Diseño asociado: {@code activity_ayuda.xml}</p>
 *
 * @author Lucas Lopez, Juan Arellano y Tania bacik
 * @version 1.0
 */
public class AyudaActivity extends AppCompatActivity {

    /**
     * Método llamado cuando se crea la actividad de ayuda.
     * <p>
     * Establece el contenido visual desde el layout {@code activity_ayuda.xml}
     * y configura la barra de acción para mostrar una flecha de navegación hacia atrás.
     *
     * @param savedInstanceState estado previo de la actividad, si existe
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ayuda);

        // Configura la barra superior con el botón de retroceso
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Ayuda");
        }
    }

    /**
     * Maneja la acción al presionar la flecha de retroceso en la barra de título.
     * <p>
     * Finaliza la actividad actual y regresa a la pantalla anterior.
     *
     * @return {@code true} siempre que se gestione correctamente la navegación hacia atrás
     */
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}