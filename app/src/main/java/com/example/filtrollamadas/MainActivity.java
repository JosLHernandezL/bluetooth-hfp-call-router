package com.example.filtrollamadas;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String[]> permisosLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), resultados -> {
                Boolean contactosOk = resultados.getOrDefault(Manifest.permission.READ_CONTACTS, false);
                Boolean telefonoOk = resultados.getOrDefault(Manifest.permission.READ_PHONE_STATE, false);

                if (Boolean.TRUE.equals(contactosOk) && Boolean.TRUE.equals(telefonoOk)) {
                    abrirAjustesNotificaciones();
                } else {
                    Toast.makeText(this, "Debes aceptar ambos permisos (Contactos y Teléfono) para que funcione", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);

        Button btnActivar = new Button(this);
        btnActivar.setText("1. Conceder Permisos y Activar Lector");
        btnActivar.setOnClickListener(v -> verificarPermisos());

        layout.addView(btnActivar);
        setContentView(layout);
    }

    private void verificarPermisos() {
        boolean tieneContactos = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
        boolean tieneTelefono = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;

        if (!tieneContactos || !tieneTelefono) {
            permisosLauncher.launch(new String[]{
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_PHONE_STATE
            });
        } else {
            abrirAjustesNotificaciones();
        }
    }

    private void abrirAjustesNotificaciones() {
        Toast.makeText(this, "Verifica que 'Lector de Llamadas y CallApp' esté encendido", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        startActivity(intent);
    }
}
