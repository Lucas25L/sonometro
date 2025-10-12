package com.example.sonometro;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class AyudaActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ayuda);

        // Opcional: Configurar una flecha para volver atrás en la barra de título
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Ayuda");
        }
    }

    // Opcional: Para que la flecha de atrás funcione
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}