package com.example.voicecallapp;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.Toast;
import com.google.android.material.card.MaterialCardView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity {
    public static TextView assistantState;
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        assistantState = findViewById(R.id.txtAssistantState);

        requestRequiredPermissions();

        askAssistantName();

        ImageButton btnMic = findViewById(R.id.btnMic);

        MaterialCardView micCard = findViewById(R.id.micCard);

        // Start mic animation
        startMicAnimation(micCard);

        btnMic.setOnClickListener(v -> {

            Intent intent =
                    new Intent(MainActivity.this, VoiceService.class);

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

    private void startMicAnimation(MaterialCardView micCard) {

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(
                micCard,
                "scaleX",
                1f,
                1.08f,
                1f
        );

        scaleX.setDuration(1000);
        scaleX.setRepeatCount(ObjectAnimator.INFINITE);

        ObjectAnimator scaleY = ObjectAnimator.ofFloat(
                micCard,
                "scaleY",
                1f,
                1.08f,
                1f
        );

        scaleY.setDuration(1000);
        scaleY.setRepeatCount(ObjectAnimator.INFINITE);

        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());

        scaleX.start();
        scaleY.start();
    }
    private void askAssistantName() {

        SharedPreferences prefs =
                getSharedPreferences("VoiceAppPrefs", MODE_PRIVATE);

        String savedName =
                prefs.getString("assistant_name", null);

        if (savedName != null) {
            return;
        }

        EditText input = new EditText(this);

        input.setTextColor(android.graphics.Color.BLACK);
        input.setHintTextColor(android.graphics.Color.LTGRAY);

        input.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.WHITE
                )
        );

        input.setBackgroundResource(android.R.drawable.edit_text);

        input.setPadding(40, 30, 40, 30);

        input.setHint("Assistant Name");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Choose Wake Name")
                .setView(input)
                .setCancelable(false)

                .setPositiveButton("Save", (d, which) -> {

                    String name =
                            input.getText().toString().trim();

                    if (name.isEmpty()) {
                        name = "Nova";
                    }

                    prefs.edit()
                            .putString(
                                    "assistant_name",
                                    name.toLowerCase()
                            )
                            .apply();

                    Toast.makeText(
                            this,
                            name + " is ready",
                            Toast.LENGTH_LONG
                    ).show();
                })

                .create();

        dialog.show();

        dialog.getWindow().setBackgroundDrawableResource(
                android.R.color.background_dark
        );

        input.requestFocus();

        input.postDelayed(() -> {

            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE);

            if (imm != null) {
                imm.showSoftInput(input,
                        android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }

        }, 200);
    }
    private void requestRequiredPermissions() {

        String[] permissions = new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.POST_NOTIFICATIONS
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
                            "Permissions are required",
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