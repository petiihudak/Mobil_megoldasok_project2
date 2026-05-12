package com.freefalldetector.app;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * EventAdapter -- A detektált események listájának megjelenítéséért felelős osztály (RecyclerView).
 */
public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    // Események listája és az időpont formázója
    private final List<DetectedEvent> events = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    // Színkonstansok a vizuális elválasztáshoz
    private static final int FREEFALL_COLOR = Color.parseColor("#FF8C00");
    private static final int IMPACT_COLOR   = Color.parseColor("#FF2222");

    /**
     * Új esemény hozzáadása a listához.
     * @param event A detektált esemény adatai.
     */
    public void addEvent(DetectedEvent event) {
        events.add(0, event); // Új esemény beszúrása a lista elejére (legfelül jelenik meg)
        notifyItemInserted(0); // Értesítés a változásról az animációhoz
        
        // Lista korlátozása 100 elemre a memória kímélése érdekében
        if (events.size() > 100) {
            events.remove(events.size() - 1);
            notifyItemRemoved(events.size());
        }
    }

    /**
     * Lista teljes kiürítése.
     */
    public void clearEvents() {
        int size = events.size();
        events.clear();
        notifyItemRangeRemoved(0, size); // Felület frissítése
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Listaelem sablon (Layout) betöltése
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        // Adatok összekötése a nézettel a lista adott pozícióján
        holder.bind(events.get(position));
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    /**
     * EventViewHolder -- Egyetlen listaelem grafikus elemeit tároló belső osztály.
     */
    class EventViewHolder extends RecyclerView.ViewHolder {
        private final View colorBar;
        private final TextView icon;
        private final TextView type;
        private final TextView time;
        private final TextView speed;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            // UI elemek megkeresése az item_event.xml-ben
            colorBar = itemView.findViewById(R.id.eventColorBar);
            icon     = itemView.findViewById(R.id.eventIcon);
            type     = itemView.findViewById(R.id.eventType);
            time     = itemView.findViewById(R.id.eventTime);
            speed    = itemView.findViewById(R.id.eventSpeed);
        }

        /**
         * Adatok kirakása a nézetre az esemény típusa alapján.
         */
        void bind(DetectedEvent event) {
            if (event.getType() == DetectedEvent.EventType.FREEFALL) {
                // SZABADESÉS megjelenítése (Narancs szín + nyíl ikon)
                colorBar.setBackgroundColor(FREEFALL_COLOR);
                icon.setText("↓");
                icon.setTextColor(FREEFALL_COLOR);
                type.setText("SZABADESÉS");
                type.setTextColor(FREEFALL_COLOR);

                // Időtartam kiírása (pl. 450 ms)
                String durationStr = event.getDurationMs() > 0
                        ? "  |  " + event.getDurationMs() + " ms"
                        : "";
                time.setText(timeFormat.format(new Date(event.getTimestamp())) + durationStr);
                
                // Gyorsulás mértéke
                speed.setText(String.format(Locale.getDefault(), "%.2f", event.getAcceleration()));
                speed.setTextColor(FREEFALL_COLOR);

            } else {
                // BECSAPÓDÁS megjelenítése (Piros szín + felkiáltójel ikon)
                colorBar.setBackgroundColor(IMPACT_COLOR);
                icon.setText("!");
                icon.setTextColor(IMPACT_COLOR);
                type.setText("BECSAPÓDÁS");
                type.setTextColor(IMPACT_COLOR);
                
                // Becsapódás időpontja
                time.setText(timeFormat.format(new Date(event.getTimestamp())));
                
                // Ütés ereje (G-erő/gyorsulás)
                speed.setText(String.format(Locale.getDefault(), "%.2f", event.getAcceleration()));
                speed.setTextColor(IMPACT_COLOR);
            }
        }
    }
}
