package com.example.slagalica;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalica.data.MissionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MissionsFragment extends Fragment {

    private final MissionManager manager = new MissionManager();
    private String myUid;

    private final String[] fields = {
            MissionManager.MISSION_WIN,
            MissionManager.MISSION_CHAT,
            MissionManager.MISSION_FRIENDLY,
            MissionManager.MISSION_TOURNAMENT
    };
    private final String[] titles = {
            "Pobedi partiju",
            "Pošalji poruku u čet",
            "Odigraj prijateljsku partiju",
            "Pobedi partiju u turniru"
    };

    private final TextView[] icons = new TextView[4];
    private final TextView[] statuses = new TextView[4];

    private TextView tvProgress, tvBonusStatus, tvBonusIcon;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_missions, container, false);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        myUid = user != null ? user.getUid() : null;

        tvProgress = v.findViewById(R.id.tvProgress);
        tvBonusStatus = v.findViewById(R.id.tvBonusStatus);
        tvBonusIcon = v.findViewById(R.id.tvBonusIcon);

        v.findViewById(R.id.btnBack).setOnClickListener(x ->
                requireActivity().getSupportFragmentManager().popBackStack());

        LinearLayout containerView = v.findViewById(R.id.missionsContainer);
        for (int i = 0; i < fields.length; i++) {
            View row = inflater.inflate(R.layout.item_mission, containerView, false);
            ((TextView) row.findViewById(R.id.tvMissionTitle)).setText(titles[i]);
            ((TextView) row.findViewById(R.id.tvMissionReward)).setText("+3 ⭐");
            icons[i] = row.findViewById(R.id.tvStatusIcon);
            statuses[i] = row.findViewById(R.id.tvMissionStatus);
            containerView.addView(row);
        }

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadStatus();
    }

    private void loadStatus() {
        if (myUid == null) return;
        manager.getStatus(myUid, status -> {
            if (!isAdded()) return;
            boolean[] done = {status.win, status.chat, status.friendly, status.tournament};
            for (int i = 0; i < 4; i++) {
                setRow(i, done[i]);
            }
            tvProgress.setText("Urađeno " + status.completedCount() + "/4");

            if (status.bonusGranted) {
                tvBonusIcon.setText("✅");
                tvBonusStatus.setText("Bonus osvojen!");
                tvBonusStatus.setTextColor(Color.parseColor("#2E7D32"));
            } else {
                tvBonusIcon.setText("🎁");
                tvBonusStatus.setText("Bonus još nije osvojen");
                tvBonusStatus.setTextColor(Color.parseColor("#777777"));
            }
        });
    }

    private void setRow(int i, boolean done) {
        if (done) {
            icons[i].setText("✔");
            icons[i].setTextColor(Color.parseColor("#2E7D32"));
            statuses[i].setText("Urađeno");
            statuses[i].setTextColor(Color.parseColor("#2E7D32"));
        } else {
            icons[i].setText("○");
            icons[i].setTextColor(Color.parseColor("#BBBBBB"));
            statuses[i].setText("Nije urađeno");
            statuses[i].setTextColor(Color.parseColor("#777777"));
        }
    }
}
