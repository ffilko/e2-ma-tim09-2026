package com.example.slagalica;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.data.CycleUtils;
import com.example.slagalica.data.LeaderboardManager;
import com.example.slagalica.data.model.LeaderboardEntry;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LeaderboardFragment extends Fragment {

    private static final long REFRESH_MS = 2 * 60 * 1000;

    private final LeaderboardManager manager = new LeaderboardManager();
    private final List<LeaderboardEntry> entries = new ArrayList<>();
    private LeaderboardAdapter adapter;

    private RecyclerView rv;
    private TextView tvDateRange, tvUpdated, tvEmpty;
    private ProgressBar progress;

    private boolean weekly = true;
    private String myUid;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            load();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_leaderboard, container, false);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        myUid = user != null ? user.getUid() : null;

        rv = v.findViewById(R.id.rvLeaderboard);
        tvDateRange = v.findViewById(R.id.tvDateRange);
        tvUpdated = v.findViewById(R.id.tvUpdated);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        progress = v.findViewById(R.id.progress);

        adapter = new LeaderboardAdapter(entries, myUid);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);

        v.findViewById(R.id.btnBack).setOnClickListener(x ->
                requireActivity().getSupportFragmentManager().popBackStack());

        MaterialButtonToggleGroup toggle = v.findViewById(R.id.toggleCycle);
        toggle.check(R.id.btnWeekly);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            weekly = (checkedId == R.id.btnWeekly);
            load();
        });

        load();

        if (myUid != null) {
            manager.processCycleRewards(requireContext().getApplicationContext(), myUid,
                    this::showPendingRewards);
        }

        return v;
    }

    private void load() {
        if (!isAdded()) return;
        String range = weekly
                ? CycleUtils.weekRangeLabel(CycleUtils.currentWeekKey())
                : CycleUtils.monthRangeLabel(CycleUtils.currentMonthKey());
        tvDateRange.setText((weekly ? "Nedeljni ciklus: " : "Mesečni ciklus: ") + range);
        progress.setVisibility(View.VISIBLE);

        LeaderboardManager.ListCallback cb = list -> {
            if (!isAdded()) return;
            entries.clear();
            entries.addAll(list);
            adapter.notifyDataSetChanged();
            progress.setVisibility(View.GONE);
            tvEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
            String now = new SimpleDateFormat("HH:mm:ss", new Locale("sr", "RS"))
                    .format(new Date());
            tvUpdated.setText("Ažurirano: " + now + " · osvežava se na 2 min");
        };

        if (weekly) manager.getWeekly(cb);
        else manager.getMonthly(cb);
    }

    private void showPendingRewards() {
        if (!isAdded() || myUid == null) return;
        manager.fetchNextRewardPopup(myUid, popup -> {
            if (!isAdded() || popup == null) return;
            RewardDialog.show(requireContext(), popup, () ->
                    manager.consumeRewardPopup(myUid, popup.docId, this::showPendingRewards));
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.postDelayed(refreshTask, REFRESH_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshTask);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(refreshTask);
    }
}
