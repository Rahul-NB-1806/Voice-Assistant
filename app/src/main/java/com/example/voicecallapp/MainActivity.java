package com.example.voicecallapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 🔑 Request required permissions
        requestRequiredPermissions();

        Button startButton = findViewById(R.id.btnMic);

        startButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, VoiceService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

            Toast.makeText(
                    MainActivity.this,
                    "Voice service started",
                    Toast.LENGTH_SHORT
            ).show();
        });
    }

    private void requestRequiredPermissions() {

        String[] permissions = new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS
        };

        boolean needPermission = false;

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                needPermission = true;
                break;
            }
        }

        if (needPermission) {
            ActivityCompat.requestPermissions(
                    this,
                    permissions,
                    PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(
                            this,
                            "Permissions are required for voice calling",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }
            }

            Toast.makeText(
                    this,
                    "All permissions granted",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }
}
