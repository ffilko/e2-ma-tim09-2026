package com.example.slagalica;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.slagalica.data.NotificationHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {
    private static class Item {
        String id, category, title, text;
        long timestamp;
        boolean read;
    }

    private final List<Item> all = new ArrayList<>();
    private LinearLayout llList;
    private Spinner spCategory, spStatus;
    private String uid;

    private static final String[] CATEGORY_BY_INDEX = {
            null,
            NotificationHelper.CAT_CHAT,
            NotificationHelper.CAT_RANKING,
            NotificationHelper.CAT_REWARDS,
            NotificationHelper.CAT_OTHER
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifikacije, container, false);

        llList = view.findViewById(R.id.llNotifications);
        spCategory = view.findViewById(R.id.spinnerCategory);
        spStatus = view.findViewById(R.id.spinnerStatus);

        AdapterView.OnItemSelectedListener refilter = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { render(); }
            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        };
        spCategory.setOnItemSelectedListener(refilter);
        spStatus.setOnItemSelectedListener(refilter);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            uid = user.getUid();
            loadNotifications();
        } else {
            showEmptyMessage("Notifikacije su dostupne samo prijavljenim igračima.");
        }

        return view;
    }

    private void loadNotifications() {
        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    all.clear();
                    for (QueryDocumentSnapshot doc : snap) {
                        Item it = new Item();
                        it.id = doc.getId();
                        it.category = doc.getString("category");
                        it.title = doc.getString("title");
                        it.text = doc.getString("text");
                        Long ts = doc.getLong("timestamp");
                        it.timestamp = ts != null ? ts : 0;
                        it.read = Boolean.TRUE.equals(doc.getBoolean("read"));
                        all.add(it);
                    }
                    render();
                })
                .addOnFailureListener(e ->
                        showEmptyMessage("Greška pri učitavanju notifikacija."));
    }

    private List<Item> filtered() {
        String cat = CATEGORY_BY_INDEX[
                Math.min(spCategory.getSelectedItemPosition(), CATEGORY_BY_INDEX.length - 1)];
        int status = spStatus.getSelectedItemPosition();

        List<Item> out = new ArrayList<>();
        for (Item it : all) {
            if (cat != null && !cat.equals(it.category)) continue;
            if (status == 1 && it.read) continue;
            if (status == 2 && !it.read) continue;
            out.add(it);
        }
        return out;
    }

    private void render() {
        if (llList == null) return;
        llList.removeAllViews();

        List<Item> items = filtered();
        if (items.isEmpty()) {
            showEmptyMessage("Nema notifikacija za izabrani filter.");
            return;
        }
        for (Item it : items) {
            llList.addView(buildCard(it));
        }
    }

    private void showEmptyMessage(String msg) {
        llList.removeAllViews();
        TextView tv = new TextView(requireContext());
        tv.setText(msg);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(32), 0, 0);
        llList.addView(tv);
    }

    private View buildCard(Item it) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackgroundColor(it.read
                ? Color.parseColor("#E0E0E0")
                : ContextCompat.getColor(requireContext(), R.color.white));
        card.setElevation(it.read ? 0 : dp(4));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(8);
        card.setLayoutParams(cp);

        LinearLayout headerRow = new LinearLayout(requireContext());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(it.title != null ? it.title : "");
        tvTitle.setTextSize(16);
        if (!it.read) {
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
        }
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(tvTitle);

        TextView tvTime = new TextView(requireContext());
        tvTime.setText(relativeTime(it.timestamp));
        tvTime.setTextSize(12);
        headerRow.addView(tvTime);
        card.addView(headerRow);

        TextView tvText = new TextView(requireContext());
        tvText.setText(it.text != null ? it.text : "");
        tvText.setPadding(0, dp(4), 0, 0);
        if (!it.read) {
            tvText.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
        }
        card.addView(tvText);

        if (!it.read) {
            Button btn = new Button(requireContext());
            btn.setText(R.string.oznaci_procitano);
            btn.setTextSize(10);
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.slagalica_color)));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            bp.topMargin = dp(8);
            btn.setLayoutParams(bp);
            btn.setOnClickListener(v -> markAsRead(it));
            card.addView(btn);
        }

        card.setOnClickListener(v -> {
            if (!it.read) markAsRead(it);
            Toast.makeText(getContext(), "Notifikacija: " + it.title, Toast.LENGTH_SHORT).show();
        });

        return card;
    }

    private void markAsRead(Item it) {
        it.read = true;
        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("notifications").document(it.id)
                .update("read", true);
        render();
    }

    private String relativeTime(long ts) {
        long diff = System.currentTimeMillis() - ts;
        long min = diff / 60000;
        if (min < 1) return "Upravo sada";
        if (min < 60) return "Pre " + min + " min";
        long h = min / 60;
        if (h < 24) return "Pre " + h + " h";
        long d = h / 24;
        return d == 1 ? "Juče" : "Pre " + d + " dana";
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}