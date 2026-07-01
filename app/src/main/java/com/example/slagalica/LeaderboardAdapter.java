package com.example.slagalica;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.data.CycleUtils;
import com.example.slagalica.data.model.LeaderboardEntry;

import java.util.List;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.VH> {

    private final List<LeaderboardEntry> data;
    private final String myUid;

    private static final int[] LEAGUE_ICONS = {
            R.drawable.ic_league_0, R.drawable.ic_league_1, R.drawable.ic_league_2,
            R.drawable.ic_league_3, R.drawable.ic_league_4, R.drawable.ic_league_5
    };

    public LeaderboardAdapter(List<LeaderboardEntry> data, String myUid) {
        this.data = data;
        this.myUid = myUid;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        LeaderboardEntry e = data.get(position);

        String rankText;
        switch (e.rank) {
            case 1: rankText = "🥇"; break;
            case 2: rankText = "🥈"; break;
            case 3: rankText = "🥉"; break;
            default: rankText = String.valueOf(e.rank);
        }
        h.tvRank.setText(rankText);

        int league = Math.max(0, Math.min(e.league, LEAGUE_ICONS.length - 1));
        h.ivLeague.setImageResource(LEAGUE_ICONS[league]);
        h.tvLeagueName.setText(CycleUtils.leagueName(league));
        h.tvUsername.setText(e.username);
        h.tvStars.setText(e.stars + " ⭐");

        boolean isMe = myUid != null && myUid.equals(e.uid);
        h.card.setCardBackgroundColor(ContextCompat.getColor(
                h.card.getContext(),
                isMe ? R.color.rank_me_highlight : R.color.card_bg));
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        androidx.cardview.widget.CardView card;
        TextView tvRank, tvUsername, tvLeagueName, tvStars;
        ImageView ivLeague;

        VH(View v) {
            super(v);
            card = v.findViewById(R.id.cardRow);
            tvRank = v.findViewById(R.id.tvRank);
            tvUsername = v.findViewById(R.id.tvUsername);
            tvLeagueName = v.findViewById(R.id.tvLeagueName);
            tvStars = v.findViewById(R.id.tvStars);
            ivLeague = v.findViewById(R.id.ivLeague);
        }
    }
}
