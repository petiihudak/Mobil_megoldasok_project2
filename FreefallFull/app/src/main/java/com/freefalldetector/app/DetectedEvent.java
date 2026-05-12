package com.freefalldetector.app;

/**
 * Esemény adatmodell -- Egy érzékelt esemény (esés vagy becsapódás) adatait tároló osztály.
 */
public class DetectedEvent {

    // Esemény típusok meghatározása
    public enum EventType {
        FREEFALL, // Szabadesés
        IMPACT    // Becsapódás
    }

    private final EventType type;       // Az esemény típusa
    private final float acceleration;   // A mért legnagyobb/legkisebb gyorsulás értéke
    private final long timestamp;       // Az esemény bekövetkezésének időpontja (ms)
    private final long durationMs;      // Az esemény időtartama (ms) - főleg esésnél releváns

    public DetectedEvent(EventType type, float acceleration, long timestamp, long durationMs) {
        this.type = type;
        this.acceleration = acceleration;
        this.timestamp = timestamp;
        this.durationMs = durationMs;
    }

    // Getter metódusok az adatok eléréséhez
    public EventType getType() { return type; }
    public float getAcceleration() { return acceleration; }
    public long getTimestamp() { return timestamp; }
    public long getDurationMs() { return durationMs; }
}
