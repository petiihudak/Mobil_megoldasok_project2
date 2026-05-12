package com.freefalldetector.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.animation.RotateAnimation;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.freefalldetector.app.databinding.ActivityCoinFlipBinding;

import java.util.Random;

/**
 * CoinFlipActivity -- Érmedobás játék, amely gombnyomásra vagy mozdulatra indul.
 */
public class CoinFlipActivity extends AppCompatActivity implements SensorEventListener {

    private ActivityCoinFlipBinding binding;
    private SharedPreferences prefs;
    private SensorManager sensorManager;
    private Sensor accelerometer;

    // Adattároláshoz használt kulcsok
    private static final String PREFS_NAME = "CoinFlipPrefs";
    private static final String KEY_HEADS = "heads_count";
    private static final String KEY_TAILS = "tails_count";

    // ==========================================================
    // --- MOZDULAT ÉRZÉKELÉS KÜSZÖBÉRTÉKE ---
    // Ez az érték határozza meg, mekkora rántás kell a dobáshoz.
    // Alaphelyzetben (függőlegesen tartva) az Y érték ~9.8.
    // A 15.0f egy érzékeny rántást igényel. Emeld meg (pl. 20.0f), ha túl könnyen indul el.
    private static final float TOSS_THRESHOLD = 15.0f;
    // ==========================================================

    private long lastFlipTime = 0;
    private static final int FLIP_COOLDOWN_MS = 1000; // 1 mp várakozás dobások között

    private int headsCount = 0;
    private int tailsCount = 0;
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCoinFlipBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Érmedobás Játék");
        }

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadStats();

        // Szenzorok inicializálása -- Gyorsulásmérő lekérése a mozdulathoz
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        binding.flipButton.setOnClickListener(v -> flipCoin()); // Gombos dobás funkciója megmarad
        binding.resetStatsButton.setOnClickListener(v -> resetStats());

        setupNavigation();
    }

    private void setupNavigation() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_coinflip);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_detector) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            }
            return id == R.id.nav_coinflip;
        });
    }

    // Dobás végrehajtása (közös funkció a gombnak és a mozdulatnak)
    private void flipCoin() {
        if (!binding.flipButton.isEnabled()) return;

        binding.flipButton.setEnabled(false);
        lastFlipTime = System.currentTimeMillis();

        // Érmeforgatás szimulálása animációval
        RotateAnimation anim = new RotateAnimation(0, 360 * 3,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        anim.setDuration(500);

        anim.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            @Override
            public void onAnimationStart(android.view.animation.Animation animation) {}

            @Override
            public void onAnimationEnd(android.view.animation.Animation animation) {
                // Sorsolás
                boolean isHeads = random.nextBoolean();
                if (isHeads) {
                    headsCount++;
                    binding.coinResult.setText("FEJ");
                    binding.coinResult.setBackgroundResource(R.drawable.dot_active);
                } else {
                    tailsCount++;
                    binding.coinResult.setText("ÍRÁS");
                    binding.coinResult.setBackgroundResource(R.drawable.dot_paused);
                }

                saveStats(); // Perzisztens tárolás
                updateUI();  // Felület frissítése
                binding.flipButton.setEnabled(true);
            }

            @Override
            public void onAnimationRepeat(android.view.animation.Animation animation) {}
        });

        binding.coinResult.startAnimation(anim);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        // Felfelé irányú mozgás (Y tengely) figyelése
        float yAccel = event.values[2];
        long currentTime = System.currentTimeMillis();

        // --- MOZDULATRA TÖRTÉNŐ DOBÁS ---
        // Ha az Y gyorsulás meghaladja a küszöböt (15.0), és letelt a cooldown
        if (yAccel > TOSS_THRESHOLD) {
            if (currentTime - lastFlipTime > FLIP_COOLDOWN_MS) {
                flipCoin(); // Automatikus dobás indítása mozdulatra
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void updateUI() {
        binding.statsText.setText("Fej: " + headsCount + " | Írás: " + tailsCount);
        int total = headsCount + tailsCount;
        if (total > 0) {
            float ratio = (float) headsCount / total * 100;
            binding.ratioText.setText(String.format("Fej arány: %.1f%%", ratio));
        } else {
            binding.ratioText.setText("Arány: 0%");
        }
    }

    private void saveStats() {
        prefs.edit().putInt(KEY_HEADS, headsCount).putInt(KEY_TAILS, tailsCount).apply();
    }

    private void loadStats() {
        headsCount = prefs.getInt(KEY_HEADS, 0);
        tailsCount = prefs.getInt(KEY_TAILS, 0);
        updateUI();
    }

    private void resetStats() {
        headsCount = 0;
        tailsCount = 0;
        saveStats();
        updateUI();
        binding.coinResult.setText("?");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Szenzor bekapcsolása, amikor a képernyőre lépsz
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Szenzor kikapcsolása az akkumulátor kímélése érdekében
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}