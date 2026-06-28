package com.example.slagalica;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.slagalica.data.ChallengeManager;
import com.example.slagalica.data.model.Challenge;
import com.example.slagalica.data.model.ChallengeParticipant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChallengeResultFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_challenge_result, container, false);
        TextView tvRanking = view.findViewById(R.id.tvRanking);
        Button btnBack = view.findViewById(R.id.btnBackToMenu);

        String challengeId = getArguments().getString("challengeId");

        new ChallengeManager().getChallenge(challengeId, challenge -> {
            if (challenge == null || getActivity() == null) return;

            List<ChallengeParticipant> list = new ArrayList<>();
            for (String uid : challenge.scores.keySet()) {
                int score = challenge.scores.get(uid);
                String name = challenge.names != null && challenge.names.containsKey(uid)
                        ? challenge.names.get(uid) : "Igrač";
                list.add(new ChallengeParticipant(uid, name, score));
            }
            Collections.sort(list, (a, b) -> Integer.compare(b.score, a.score));

            int totalStars = challenge.starsBet * challenge.participants.size();
            int totalTokens = challenge.tokensBet * challenge.participants.size();

            StringBuilder sb = new StringBuilder("🏆 REZULTATI IZAZOVA\n");
            sb.append("Okrug: ").append(challenge.region).append("\n");
            sb.append("Ukupno uloženo: ").append(totalStars)
                    .append("⭐ + ").append(totalTokens).append("🪙\n\n");

            for (int i = 0; i < list.size(); i++) {
                ChallengeParticipant p = list.get(i);
                sb.append(i + 1).append(". ").append(p.name)
                        .append(" — ").append(p.score).append(" poena");
                if (i == 0) {
                    sb.append("  🥇 +").append((int)(totalStars * 0.75))
                            .append("⭐ +").append((int)(totalTokens * 0.75)).append("🪙");
                } else if (i == 1) {
                    sb.append("  🥈 (ulog nazad: +").append(challenge.starsBet)
                            .append("⭐ +").append(challenge.tokensBet).append("🪙)");
                } else {
                    sb.append("  💸 (ulog izgubljen)");
                }
                sb.append("\n");
            }

            getActivity().runOnUiThread(() -> tvRanking.setText(sb.toString()));
        });

        if (btnBack != null) {
            btnBack.setOnClickListener(v ->
                    ((MainActivity) requireActivity()).navigate(new GameMenuFragment(), false));
        }

        return view;
    }
}