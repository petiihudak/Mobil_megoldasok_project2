package com.freefalldetector.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.freefalldetector.app.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private ActivityMainBinding binding;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private EventAdapter eventAdapter;
    private SharedPreferences sharedPreferences;

    // Beállítások mentéséhez használt konstansok
    private static final String PREFS_NAME = "FreefallPrefs";
    private static final String KEY_SENSITIVITY = "sensitivity_level";

    // Érzékelési küszöbértékek (alapértelmezett)
    private float freefallThreshold = 3.0f;   // esés határa (m/s²)
    private float impactThreshold   = 20.0f;  // becsapódás határa (m/s²)

    // Érzékenységi szintek mátrixa -- {esési küszöb, becsapódási küszöb}
    private final float[][] sensitivityLevels = {
            {4.5f, 15.0f},   // 0 = ALACSONY
            {3.0f, 20.0f},   // 1 = NORMÁL
            {2.0f, 25.0f}    // 2 = MAGAS
    };
    private final String[] sensitivityNames = {"ALACSONY", "NORMÁL", "MAGAS"};

    // Állapotváltozók az esés figyeléséhez
    private boolean isInFreefall = false;
    private long freefallStartTime = 0L;
    private float freefallPeakAccel = 9.81f;
    private long lastImpactTime = 0L;
    private static final long IMPACT_COOLDOWN_MS = 1500L;

    // Simítás (EMA szűrő) konstansai
    private float smoothedMagnitude = 9.81f;
    private static final float SMOOTHING = 0.2f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // View Binding inicializálása -- UI elemek elérése
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Beállítások kezelőjének létrehozása -- Adattárolás előkészítése
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        setupSensor(); // Szenzorok inicializálása
        setupUI();     // Felületi elemek és listák beállítása
        loadSettings(); // Mentett érzékenység betöltése
        setupNavigation(); // Alsó menüsor beállítása
    }

    private void setupSensor() {
        // Gyorsulásmérő lekérése a rendszertől
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    private void setupUI() {
        // RecyclerView (eseménylista) beállítása
        eventAdapter = new EventAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setReverseLayout(true); // Új elemek alulra, de a lista megfordítva
        layoutManager.setStackFromEnd(false);
        binding.eventsList.setLayoutManager(layoutManager);
        binding.eventsList.setAdapter(eventAdapter);

        // Lista törlése gomb
        binding.clearButton.setOnClickListener(v -> eventAdapter.clearEvents());

        // Érzékenység állító csúszka kezelése
        binding.sensitivityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSensitivity(progress); // Küszöbértékek módosítása
                if (fromUser) {
                    saveSettings(progress); // Felhasználói állítás mentése
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupNavigation() {
        // Navigáció vezérlése -- Átváltás az érmedobás játékra
        binding.bottomNavigation.setSelectedItemId(R.id.nav_detector);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_coinflip) {
                startActivity(new Intent(this, CoinFlipActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return id == R.id.nav_detector;
        });
    }

    private void updateSensitivity(int level) {
        // Fizikai határértékek frissítése a kiválasztott szint alapján
        freefallThreshold = sensitivityLevels[level][0];
        impactThreshold   = sensitivityLevels[level][1];
        binding.sensitivityLabel.setText(sensitivityNames[level]); // Felirat frissítése
        binding.sensitivityBar.setProgress(level); // Csúszka pozíciója
    }

    private void saveSettings(int level) {
        // Érzékenységi szint mentése a háttértárba
        sharedPreferences.edit().putInt(KEY_SENSITIVITY, level).apply();
    }

    private void loadSettings() {
        // Utoljára használt szint betöltése
        int savedLevel = sharedPreferences.getInt(KEY_SENSITIVITY, 1);
        updateSensitivity(savedLevel);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_coin_flip) {
            Intent intent = new Intent(this, CoinFlipActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Szenzor regisztrálása -- Mérés indítása, ha az app látható
        binding.bottomNavigation.setSelectedItemId(R.id.nav_detector);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        }
        binding.statusText.setText("AKTÍV"); // Státusz visszajelzés
        binding.statusText.setTextColor(getColor(R.color.accent_green));
        binding.statusDot.setBackgroundResource(R.drawable.dot_active);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Szenzor leállítása -- Erőforrások kímélése a háttérben
        sensorManager.unregisterListener(this);
        binding.statusText.setText("SZÜNET"); // Státusz szüneteltetése
        binding.statusText.setTextColor(getColor(R.color.status_paused));
        binding.statusDot.setBackgroundResource(R.drawable.dot_paused);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        // Gyorsulás adatok kiolvasása a tengelyekről
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        // Eredő gyorsulás számítása (vektorhossz)
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);

        // Adat simítása (EMA) -- Rezgések és zaj kiszűrése
        smoothedMagnitude = SMOOTHING * magnitude + (1 - SMOOTHING) * smoothedMagnitude;

        updateSpeedDisplay(smoothedMagnitude, x, y, z); // Kijelző frissítése

        long now = System.currentTimeMillis();

        // --- SZABADESÉS ÉRZÉKELÉSE ---
        if (smoothedMagnitude < freefallThreshold) {
            if (!isInFreefall) {
                isInFreefall = true; // Esés kezdete
                freefallStartTime = now;
                freefallPeakAccel = smoothedMagnitude;
            } else {
                if (smoothedMagnitude < freefallPeakAccel) {
                    freefallPeakAccel = smoothedMagnitude; // Legalacsonyabb érték mentése
                }
            }
        } else {
            if (isInFreefall) {
                isInFreefall = false; // Esés vége
                long duration = now - freefallStartTime;
                if (duration > 100) { // Csak a valódi esést naplózzuk (min 0.1 mp)
                    final DetectedEvent freefallEvent = new DetectedEvent(
                            DetectedEvent.EventType.FREEFALL,
                            freefallPeakAccel,
                            freefallStartTime,
                            duration
                    );
                    runOnUiThread(() -> {
                        eventAdapter.addEvent(freefallEvent); // Hozzáadás a listához
                        binding.eventsList.scrollToPosition(0);
                    });
                    vibrateShort(); // Rezgő visszajelzés
                }
            }
        }

        // --- BECSAPÓDÁS ÉRZÉKELÉSE ---
        if (smoothedMagnitude > impactThreshold && !isInFreefall) {
            if (now - lastImpactTime > IMPACT_COOLDOWN_MS) {
                lastImpactTime = now;
                final DetectedEvent impactEvent = new DetectedEvent(
                        DetectedEvent.EventType.IMPACT,
                        smoothedMagnitude,
                        now,
                        0
                );
                runOnUiThread(() -> {
                    eventAdapter.addEvent(impactEvent); // Becsapódás naplózása
                    binding.eventsList.scrollToPosition(0);
                    flashImpact(); // Vizuális villanás effekt
                });
                vibrateImpact(); // Erősebb rezgés
            }
        }
    }

    private void updateSpeedDisplay(float mag, float x, float y, float z) {
        // UI frissítése a szenzoradatokkal -- Digitális kijelzők
        runOnUiThread(() -> {
            binding.speedValue.setText(String.format("%.2f", mag));
            binding.axisX.setText(String.format("%+.2f", x));
            binding.axisY.setText(String.format("%+.2f", y));
            binding.axisZ.setText(String.format("%+.2f", z));

            // Grafikus G-erő sáv frissítése
            int progress = (int) Math.min(200, Math.max(0, mag * 10));
            binding.gforceBar.setProgress(progress);

            float g = mag / 9.81f; // Átszámítás G egységbe
            binding.gforceLabel.setText(String.format("%.2f G", g));

            // Szöveg színének változtatása az állapot alapján
            if (mag < freefallThreshold) {
                binding.speedValue.setTextColor(getColor(R.color.freefall_color));
            } else if (mag > impactThreshold) {
                binding.speedValue.setTextColor(getColor(R.color.impact_color));
            } else {
                binding.speedValue.setTextColor(getColor(R.color.text_primary));
            }
        });
    }

    private void flashImpact() {
        // Becsapódás vizuális jelzése -- Szám felnagyítása és visszazsugorítása
        binding.speedValue.animate()
                .scaleX(1.2f).scaleY(1.2f)
                .setDuration(80)
                .withEndAction(() ->
                        binding.speedValue.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(150)
                                .start())
                .start();
    }

    @SuppressWarnings("deprecation")
    private void vibrateShort() {
        // Rövid rezgés esésnél -- Kompatibilitás Android 12+ és régebbi rendszerekkel
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(100, 80));
                }
            } else {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) v.vibrate(100);
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private void vibrateImpact() {
        // Dupla rezgés becsapódásnál -- Ritmusos vibráció
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vm.getDefaultVibrator().vibrate(
                            VibrationEffect.createWaveform(new long[]{0, 80, 60, 120}, -1));
                }
            } else {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) v.vibrate(new long[]{0, 80, 60, 120}, -1);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Biztonságos leállás -- Szenzor lecsatlakoztatása
        sensorManager.unregisterListener(this);
    }
}
