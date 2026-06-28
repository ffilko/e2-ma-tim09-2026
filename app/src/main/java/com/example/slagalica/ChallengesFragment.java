package com.example.slagalica;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.data.ChallengeManager;
import com.example.slagalica.data.model.Challenge;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ChallengesFragment extends Fragment {

    private RecyclerView rvChallenges;
    private TextView tvRegion, tvMyStats, tvEmpty;
    private ChallengeAdapter adapter;
    private final List<Challenge> challenges = new ArrayList<>();
    private final ChallengeManager manager = new ChallengeManager();
    private String myRegion;
    private String myUid;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_challenges, container, false);

        rvChallenges = view.findViewById(R.id.rvChallenges);
        Button btnCreate = view.findViewById(R.id.btnCreateChallenge);
        tvRegion = view.findViewById(R.id.tvRegion);
        tvMyStats = view.findViewById(R.id.tvMyStats);
        tvEmpty = view.findViewById(R.id.tvEmpty);

        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        adapter = new ChallengeAdapter(challenges, this::onJoinClicked);
        rvChallenges.setLayoutManager(new LinearLayoutManager(getContext()));
        rvChallenges.setAdapter(adapter);

        btnCreate.setOnClickListener(v -> {
            if (myRegion == null) {
                Toast.makeText(getContext(), "Učitavanje regiona...", Toast.LENGTH_SHORT).show();
            } else {
                showCreateDialog();
            }
        });

        loadMyProfile();
        return view;
    }

    private void loadMyProfile() {
        FirebaseFirestore.getInstance().collection("users").document(myUid).get()
                .addOnSuccessListener(doc -> {
                    myRegion = doc.getString("region");
                    Long stars = doc.getLong("stars");
                    Long tokens = doc.getLong("tokens");

                    if (tvRegion != null)
                        tvRegion.setText("Tvoj okrug: " + (myRegion != null ? myRegion : "—"));
                    if (tvMyStats != null)
                        tvMyStats.setText("Zvezde: " + (stars != null ? stars : 0)
                                + "  |  Tokeni: " + (tokens != null ? tokens : 0));

                    if (myRegion != null) loadChallenges();
                });
    }

    private void loadChallenges() {
        manager.listenToOpenChallenges(myRegion, list -> {
            // Filtriraj: ne prikazuj izazove u kojima si već učesnik
            challenges.clear();
            for (Challenge c : list) {
                if (!c.participants.containsKey(myUid)) {
                    challenges.add(c);
                }
            }
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    if (tvEmpty != null)
                        tvEmpty.setVisibility(challenges.isEmpty() ? View.VISIBLE : View.GONE);
                    rvChallenges.setVisibility(challenges.isEmpty() ? View.GONE : View.VISIBLE);
                });
            }
        });
    }

    private void showCreateDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_challenge, null);
        EditText etStars = dialogView.findViewById(R.id.etStars);
        EditText etTokens = dialogView.findViewById(R.id.etTokens);

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Napravi izazov")
                .setMessage("Region: " + myRegion)
                .setView(dialogView)
                .setPositiveButton("Kreiraj", (d, w) -> {
                    String sStr = etStars.getText().toString().trim();
                    String tStr = etTokens.getText().toString().trim();
                    if (sStr.isEmpty() || tStr.isEmpty()) {
                        Toast.makeText(getContext(), "Unesite vrednosti",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int stars = Integer.parseInt(sStr);
                    int tokens = Integer.parseInt(tStr);

                    if (stars < 0 || stars > 10 || tokens < 0 || tokens > 2) {
                        Toast.makeText(getContext(), "Max 10 zvezda i 2 tokena",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    manager.deductBet(myUid, stars, tokens, success -> {
                        if (!success) {
                            if (getActivity() != null)
                                getActivity().runOnUiThread(() ->
                                        Toast.makeText(getContext(),
                                                "Nemaš dovoljno zvezda/tokena!",
                                                Toast.LENGTH_SHORT).show());
                            return;
                        }
                        manager.createChallenge(myRegion, stars, tokens,
                                (ok, createdId) -> {
                                    if (getActivity() != null)
                                        getActivity().runOnUiThread(() -> {
                                            if (!isAdded()) return;
                                            // Idi na čekaonicu kao kreator
                                            Bundle args = new Bundle();
                                            args.putString("challengeId", createdId);
                                            args.putBoolean("isCreator", true);
                                            WaitingForChallengeFragment waitFragment =
                                                    new WaitingForChallengeFragment();
                                            waitFragment.setArguments(args);
                                            ((MainActivity) requireActivity()).navigate(waitFragment, true);
                                        });
                                },
                                err -> {
                                    if (getActivity() != null)
                                        getActivity().runOnUiThread(() ->
                                                Toast.makeText(getContext(), "Greška",
                                                        Toast.LENGTH_SHORT).show());
                                });
                    });
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void onJoinClicked(Challenge challenge) {
        manager.deductBet(myUid, challenge.starsBet, challenge.tokensBet, success -> {
            if (!success) {
                if (getActivity() != null)
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Nemaš dovoljno zvezda/tokena!",
                                    Toast.LENGTH_SHORT).show());
                return;
            }

            manager.joinChallenge(challenge.id, joined -> {
                if (!joined) {
                    // Vrati ulog
                    FirebaseFirestore.getInstance().collection("users").document(myUid)
                            .update("stars",
                                    com.google.firebase.firestore.FieldValue.increment(
                                            challenge.starsBet),
                                    "tokens",
                                    com.google.firebase.firestore.FieldValue.increment(
                                            challenge.tokensBet));
                    if (getActivity() != null)
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "Ne možeš se pridružiti",
                                        Toast.LENGTH_SHORT).show());
                    return;
                }

                // Idi na čekaonicu kao takmičar (isCreator = false)
                if (getActivity() != null)
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        Bundle args = new Bundle();
                        args.putString("challengeId", challenge.id);
                        args.putBoolean("isCreator", false);
                        WaitingForChallengeFragment waitFragment =
                                new WaitingForChallengeFragment();
                        waitFragment.setArguments(args);
                        ((MainActivity) requireActivity()).navigate(waitFragment, true);
                    });
            });
        });
    }

    private void startChallengeGame(String challengeId) {
        Bundle args = new Bundle();
        args.putString("challengeId", challengeId);
        ChallengeGameFragment gameFragment = new ChallengeGameFragment();
        gameFragment.setArguments(args);
        ((MainActivity) requireActivity()).navigate(gameFragment, true);
    }
}