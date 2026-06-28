package com.example.slagalica;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.data.model.Challenge;

import java.util.List;

public class ChallengeAdapter extends RecyclerView.Adapter<ChallengeAdapter.VH> {

    private final List<Challenge> data;
    private final OnJoinListener listener;

    interface OnJoinListener {
        void onJoin(Challenge c);
    }

    public ChallengeAdapter(List<Challenge> data, OnJoinListener listener) {
        this.data = data;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_challenge, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Challenge c = data.get(position);
        holder.tvCreator.setText(c.creatorName);
        holder.tvBet.setText(c.starsBet + " ⭐ + " + c.tokensBet + " 🪙");
        holder.tvParticipants.setText(c.participants.size() + "/4 igrača");

        holder.btnJoin.setEnabled(c.participants.size() < 4);
        holder.btnJoin.setOnClickListener(v -> listener.onJoin(c));
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCreator, tvBet, tvParticipants;
        Button btnJoin;

        VH(View v) {
            super(v);
            tvCreator = v.findViewById(R.id.tvCreator);
            tvBet = v.findViewById(R.id.tvBet);
            tvParticipants = v.findViewById(R.id.tvParticipants);
            btnJoin = v.findViewById(R.id.btnJoin);
        }
    }
}