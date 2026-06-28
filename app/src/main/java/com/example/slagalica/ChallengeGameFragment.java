package com.example.slagalica;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.slagalica.data.ChallengeManager;
import com.example.slagalica.data.model.Challenge;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class ChallengeGameFragment extends Fragment {

    private static final int TOTAL_GAMES = 6;

    private String challengeId;
    private String myUid;
    private String soloSessionId;
    private int localGameIndex = -1;
    private int myTotalScore = 0;
    private boolean resultSent = false;
    private TextView tvInfo;

    private final DatabaseReference db = FirebaseDatabase.getInstance(
            "https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app"
    ).getReference();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_challenge_game, container, false);
        tvInfo = view.findViewById(R.id.tvChallengeGameInfo);

        challengeId = getArguments().getString("challengeId");
        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Kreira solo sesiju za ovog igrača, koja izgleda kao normalna 2-player sesija
        // Ghost player2 odmah šalje ready i score=0 nakon svake igre
        soloSessionId = "challenge_" + challengeId + "_" + myUid;

        setupGhostSessionAndStart();
        return view;
    }

    private void setupGhostSessionAndStart() {
        // Kreiraj ghost sesiju koja izgleda kao normalna 2-player sesija
        // player1 = pravi igrač, player2 = ghost (odmah ready, score=0)
        Map<String, Object> scores = new HashMap<>();
        scores.put("player1", 0);
        scores.put("player2", 0);

        Map<String, Object> session = new HashMap<>();
        session.put("player1Id", myUid);
        session.put("player1Name", "Ti");
        session.put("player2Id", "ghost");
        session.put("player2Name", "—");
        session.put("currentGameIndex", 0);
        session.put("phase", "playing");
        session.put("player1Ready", false);
        session.put("player2Ready", true);  // Ghost je uvek ready
        session.put("player1Disconnected", false);
        session.put("player2Disconnected", false);
        session.put("isFriendly", true); // da ne bi applyMatchResults radio
        session.put("scores", scores);

        db.child("sessions").child(soloSessionId).setValue(session)
                .addOnSuccessListener(v -> launchGameFragment());
    }

    private void launchGameFragment() {
        updateInfo(0);

        new ChallengeManager().getChallenge(challengeId, challenge -> {
            String seed = challenge != null ? challenge.roundSeed : challengeId;

            Bundle args = new Bundle();
            args.putString("sessionId", soloSessionId);
            args.putString("myRole", "player1");
            args.putString("challengeId", challengeId);
            args.putString("roundSeed", seed);

            listenAndAutoAdvanceGhost();

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    GameFragment gameFragment = new GameFragment();
                    gameFragment.setArguments(args);
                    getChildFragmentManager().beginTransaction()
                            .replace(R.id.challengeGameContainer, gameFragment)
                            .commit();
                });
            }
        });
    }

    private void listenAndAutoAdvanceGhost() {
        // Ghost prati sesiju i kad god se player1Ready promeni na true,
        // ghost odmah postavi player2Ready = true
        // Ovo omogućava normalnu tranziciju između igara bez pravog protivnika
        db.child("sessions").child(soloSessionId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String phase = snapshot.child("phase").getValue(String.class);

                        if ("finished".equals(phase)) {
                            // Sesija je završena — ukloni listener
                            snapshot.getRef().removeEventListener(this);

                            // Obriši ghost sesiju iz RTDB (čišćenje)
                            db.child("sessions").child(soloSessionId).removeValue();
                            return;
                        }

                        Boolean p1Ready = snapshot.child("player1Ready").getValue(Boolean.class);
                        Boolean p2Ready = snapshot.child("player2Ready").getValue(Boolean.class);

                        // Kad god player1 postavi ready, ghost odmah odgovori
                        if (Boolean.TRUE.equals(p1Ready)
                                && !Boolean.TRUE.equals(p2Ready)) {
                            db.child("sessions").child(soloSessionId)
                                    .child("player2Ready").setValue(true);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void updateInfo(int gameIndex) {
        if (tvInfo != null) {
            tvInfo.setText("Izazov — igra " + (gameIndex + 1) + "/" + TOTAL_GAMES
                    + "  |  Poeni: " + myTotalScore);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Počisti ghost sesiju ako igrač izlazi
        db.child("sessions").child(soloSessionId).removeValue();
    }
}