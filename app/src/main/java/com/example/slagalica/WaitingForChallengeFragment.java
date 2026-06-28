package com.example.slagalica;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.slagalica.data.ChallengeManager;
import com.example.slagalica.data.model.Challenge;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class WaitingForChallengeFragment extends Fragment {

    private TextView tvStatus, tvParticipantsList, tvBet;
    private Button btnStart;
    private String challengeId;
    private String myUid;
    private boolean isCreator;
    private boolean started = false;
    private ChallengeManager challengeManager = new ChallengeManager();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_waiting_for_challenge, container, false);

        challengeId = getArguments().getString("challengeId");
        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        isCreator = getArguments().getBoolean("isCreator", false);

        tvStatus = view.findViewById(R.id.tvWaitingStatus);
        tvParticipantsList = view.findViewById(R.id.tvParticipantsList);
        tvBet = view.findViewById(R.id.tvWaitingBet);
        btnStart = view.findViewById(R.id.btnStartChallenge);

        btnStart.setVisibility(isCreator ? View.VISIBLE : View.GONE);
        btnStart.setEnabled(false);

        btnStart.setOnClickListener(v -> startChallenge());

        listenToChallenge();

        listenForStart();

        return view;
    }

    private void listenToChallenge() {
        challengeManager.listenToMyChallenge(challengeId, challenge -> {
            if (challenge == null || getActivity() == null) return;

            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;

                // Prikazi ulog
                tvBet.setText("Ulog: " + challenge.starsBet + "⭐ + "
                        + challenge.tokensBet + "🪙");

                int count = challenge.participants != null ? challenge.participants.size() : 0;
                tvStatus.setText("Igrači: " + count + "/4");

                if (challenge.names != null && !challenge.names.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    int i = 1;
                    for (String uid : challenge.participants.keySet()) {
                        String name = challenge.names.containsKey(uid)
                                ? challenge.names.get(uid) : "Igrač";
                        sb.append(i).append(". ").append(name);
                        if (uid.equals(challenge.creatorUid)) sb.append(" 👑");
                        sb.append("\n");
                        i++;
                    }
                    tvParticipantsList.setText(sb.toString());
                }

                if (isCreator) {
                    boolean canStart = count >= 2;
                    btnStart.setEnabled(canStart);
                    btnStart.setText(canStart
                            ? "Pokreni izazov (" + count + " igrača)"
                            : "Čekaj još igrača (" + count + "/2 minimum)");
                } else {
                    tvStatus.setText("Igrači: " + count + "/4 — Čeka se kreator da pokrene...");
                }
            });
        });
    }

    private void listenForStart() {
        challengeManager.listenForStart(challengeId, myUid, () -> {
            if (!isAdded() || started) return;
            started = true;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    Bundle args = new Bundle();
                    args.putString("challengeId", challengeId);
                    ChallengeGameFragment gameFragment = new ChallengeGameFragment();
                    gameFragment.setArguments(args);
                    ((MainActivity) requireActivity()).navigate(gameFragment, true);
                });
            }
        });
    }

    private void startChallenge() {
        if (!isCreator) return;
        btnStart.setEnabled(false);
        btnStart.setText("Pokretanje...");

        challengeManager.startChallenge(challengeId, success -> {
            if (!success && isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Greška pri pokretanju",
                                Toast.LENGTH_SHORT).show());
                btnStart.setEnabled(true);
            }
        });
    }
}