package com.example.slagalica.ui.fragments.games;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.slagalica.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class SpojniceFragment extends Fragment {

    private static final long ROUND_DURATION_MS = 30_000;
    private static final int POINTS_PER_PAIR = 2;

    private TextView tvRound, tvTimer, tvPhase, tvScores, tvSelectedPair, tvConnectedPairs;
    private Button[] leftButtons = new Button[5];
    private Button[] rightButtons = new Button[5];
    private Button btnConnect, btnClear, btnFinish;

    private boolean isChallengeMode = false;
    private boolean isMe1;
    private String myRole;

    private DatabaseReference gameStateRef;
    private ValueEventListener gameStateListener;

    private int currentRound = 1;
    private int player1Score = 0;
    private int player2Score = 0;
    private boolean gameEnded = false;
    private boolean resultSent = false;
    private CountDownTimer roundTimer;

    private String category = "";
    private List<String> leftItems = new ArrayList<>();
    private List<String> rightItems = new ArrayList<>();
    private List<String> originalRight = new ArrayList<>();
    private int[] correctMapping = new int[5];

    private int selectedLeft = -1;
    private int selectedRight = -1;

    private String activePlayer = "player1";
    private String roundPhase = "main"; // main, opponent_chance

    // paired[i] = true ako je levi pojam i uspešno spojen (od strane bilo kog igrača)
    private boolean[] paired = new boolean[5];
    // attemptedThisTurn[i] = true ako je trenutni igrač već probao levi pojam i u main fazi
    private boolean[] attemptedThisTurn = new boolean[5];

    private int loadedRound = -1;
    private String lastTurnKey = "";

    private DatabaseReference sessionScoresRef;
    private ValueEventListener scoresListener;

    private String roundSeed = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_spojnice, container, false);

        myRole = getArguments() != null ? getArguments().getString("myRole") : "player1";
        String sessionId = getArguments() != null ? getArguments().getString("sessionId") : null;
        roundSeed = getArguments() != null ? getArguments().getString("roundSeed") : null;
        isChallengeMode = getArguments() != null && getArguments().getBoolean("isChallengeMode", false);
        isMe1 = "player1".equals(myRole);

        gameStateRef = FirebaseDatabase.getInstance(
                "https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app"
        ).getReference("sessions").child(sessionId).child("gameState");

        if (sessionId != null) listenToSessionScores(sessionId);

        String oppRole = isMe1 ? "player2" : "player1";
        FirebaseDatabase.getInstance(
                        "https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app"
                ).getReference("sessions").child(sessionId)
                .child(oppRole + "Disconnected")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) return;
                        if (gameEnded) return;
                        if (!isMyTurn()) {
                            // Protivnik napustio dok je na njemu red — odmah završi rundu
                            if (roundTimer != null) roundTimer.cancel();
                            // Preskoči na opponent_chance AKO smo u main fazi i ima nepovezanih
                            // ili odmah na sledeću rundu
                            gameStateRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snap) {
                                    String rPhase = snap.child("roundPhase").getValue(String.class);
                                    if ("main".equals(rPhase)) {
                                        // Daj šansu nama (koji smo ostali)
                                        startOpponentChance();
                                    } else {
                                        // Bili smo u opponent_chance — završi rundu
                                        advanceRound();
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError e) {
                                }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                    }
                });

        bindViews(view);
        setupClickListeners();
        setInputEnabled(false);
        tvPhase.setText("Učitavanje...");

        if (isMe1) {
            loadRoundsAndPublish();
        } else {
            waitForState();
        }

        return view;
    }

    private void bindViews(View view) {
        tvRound = view.findViewById(R.id.tvRound);
        tvTimer = view.findViewById(R.id.tvTimer);
        tvPhase = view.findViewById(R.id.tvPhase);
        tvScores = view.findViewById(R.id.tvScores);
        tvSelectedPair = view.findViewById(R.id.tvSelectedPair);
        tvConnectedPairs = view.findViewById(R.id.tvConnectedPairs);

        leftButtons[0] = view.findViewById(R.id.btnLeft1);
        leftButtons[1] = view.findViewById(R.id.btnLeft2);
        leftButtons[2] = view.findViewById(R.id.btnLeft3);
        leftButtons[3] = view.findViewById(R.id.btnLeft4);
        leftButtons[4] = view.findViewById(R.id.btnLeft5);

        rightButtons[0] = view.findViewById(R.id.btnRight1);
        rightButtons[1] = view.findViewById(R.id.btnRight2);
        rightButtons[2] = view.findViewById(R.id.btnRight3);
        rightButtons[3] = view.findViewById(R.id.btnRight4);
        rightButtons[4] = view.findViewById(R.id.btnRight5);

        btnConnect = view.findViewById(R.id.btnConnectPair);
        btnClear = view.findViewById(R.id.btnClearSelection);
        btnFinish = view.findViewById(R.id.btnFinishSpojnice);
    }

    private void setupClickListeners() {
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            leftButtons[i].setOnClickListener(v -> selectLeft(idx));
            rightButtons[i].setOnClickListener(v -> selectRight(idx));
        }
        btnConnect.setOnClickListener(v -> connectPair());
        btnClear.setOnClickListener(v -> clearSelection());
        btnFinish.setOnClickListener(v -> finishMyTurn());
    }

    @SuppressWarnings("unchecked")
    private void loadRoundsAndPublish() {
        FirebaseFirestore.getInstance()
                .collection("games")
                .document("spojnice")
                .collection("rounds")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Map<String, Object>> allRounds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        allRounds.add(doc.getData());
                    }
                    if (allRounds.size() < 2) {
                        allRounds.clear();
                        allRounds.add(getFallbackRound1());
                        allRounds.add(getFallbackRound2());
                    }
                    if (roundSeed != null) {
                        Collections.sort(allRounds, (a, b) -> String.valueOf(a).hashCode() - String.valueOf(b).hashCode());
                    } else {
                        Collections.shuffle(allRounds);
                    }

                    Map<String, Object> r1 = allRounds.get(0);
                    Map<String, Object> r2 = allRounds.size() > 1 ? allRounds.get(1) : allRounds.get(0);

                    List<Integer> shuffle1 = getShuffledIndices(roundSeed != null ? roundSeed + "s1" : null);
                    List<Integer> shuffle2 = getShuffledIndices(roundSeed != null ? roundSeed + "s2" : null);

                    Map<String, Object> state = new HashMap<>();
                    state.put("round", 1);
                    state.put("activePlayer", "player1");
                    state.put("roundPhase", "main");
                    state.put("player1Score", 0);
                    state.put("player2Score", 0);
                    state.put("phase", "playing");
                    state.put("roundStart", System.currentTimeMillis());
                    state.put("round1", r1);
                    state.put("round1Shuffle", shuffle1);
                    state.put("round2", r2);
                    state.put("round2Shuffle", shuffle2);
                    state.put("isChallengeMode", isChallengeMode);

                    Map<String, Object> pairedMap = new HashMap<>();
                    for (int i = 0; i < 5; i++) pairedMap.put(String.valueOf(i), false);
                    state.put("paired", pairedMap);

                    Map<String, Object> attemptedMap = new HashMap<>();
                    for (int i = 0; i < 5; i++) attemptedMap.put(String.valueOf(i), false);
                    state.put("attempted", attemptedMap);

                    gameStateRef.setValue(state).addOnSuccessListener(v -> listenToGameState());
                })
                .addOnFailureListener(e -> publishFallbackState());
    }

    private void publishFallbackState() {
        Map<String, Object> state = new HashMap<>();
        state.put("round", 1);
        state.put("activePlayer", "player1");
        state.put("roundPhase", "main");
        state.put("player1Score", 0);
        state.put("player2Score", 0);
        state.put("phase", "playing");
        state.put("roundStart", System.currentTimeMillis());
        state.put("round1", getFallbackRound1());
        state.put("round1Shuffle", getShuffledIndices(roundSeed != null ? roundSeed + "s1" : null));
        state.put("round2", getFallbackRound2());
        state.put("round2Shuffle", getShuffledIndices(roundSeed != null ? roundSeed + "s2 " : null));
        state.put("isChallengeMode", isChallengeMode);

        Map<String, Object> pairedMap = new HashMap<>();
        for (int i = 0; i < 5; i++) pairedMap.put(String.valueOf(i), false);
        state.put("paired", pairedMap);

        Map<String, Object> attemptedMap = new HashMap<>();
        for (int i = 0; i < 5; i++) attemptedMap.put(String.valueOf(i), false);
        state.put("attempted", attemptedMap);

        gameStateRef.setValue(state).addOnSuccessListener(v -> listenToGameState());
    }

    private void waitForState() {
        gameStateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.hasChild("round1")) {
                    listenToGameState();
                } else {
                    tvPhase.setText("Čekam drugog igrača...");
                    if (tvPhase != null) {
                        tvPhase.postDelayed(() -> waitForState(), 500);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void listenToGameState() {
        if (gameStateListener != null) return;
        gameStateListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists() || gameEnded) return;

                Integer round = snapshot.child("round").getValue(Integer.class);
                String active = snapshot.child("activePlayer").getValue(String.class);
                String rPhase = snapshot.child("roundPhase").getValue(String.class);
                Integer p1 = snapshot.child("player1Score").getValue(Integer.class);
                Integer p2 = snapshot.child("player2Score").getValue(Integer.class);
                String phase = snapshot.child("phase").getValue(String.class);
                Boolean ch = snapshot.child("isChallengeMode").getValue(Boolean.class);

                if (ch != null) isChallengeMode = ch;
                if (round != null) currentRound = round;
                if (active != null) activePlayer = active;
                if (rPhase != null) roundPhase = rPhase;
                if (p1 != null) player1Score = p1;
                if (p2 != null) player2Score = p2;

                for (int i = 0; i < 5; i++) {
                    Boolean p = snapshot.child("paired").child(String.valueOf(i)).getValue(Boolean.class);
                    paired[i] = Boolean.TRUE.equals(p);
                    Boolean a = snapshot.child("attempted").child(String.valueOf(i)).getValue(Boolean.class);
                    attemptedThisTurn[i] = Boolean.TRUE.equals(a);
                }

                if ("finished".equals(phase) && !gameEnded) {
                    gameEnded = true;
                    endGame();
                    return;
                }

                if (currentRound != loadedRound) {
                    loadedRound = currentRound;
                    loadRoundFromSnapshot(snapshot);
                    selectedLeft = -1;
                    selectedRight = -1;
                }

                // Reset selection ako se promenio aktivni igrač ili faza
                String turnKey = currentRound + "_" + activePlayer + "_" + roundPhase;
                if (!turnKey.equals(lastTurnKey)) {
                    lastTurnKey = turnKey;
                    selectedLeft = -1;
                    selectedRight = -1;
                }

                Long roundStart = snapshot.child("roundStart").getValue(Long.class);
                if (roundStart != null) {
                    startTimer(roundStart);
                }

                renderUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        gameStateRef.addValueEventListener(gameStateListener);
    }

    private void loadRoundFromSnapshot(DataSnapshot snapshot) {
        String roundKey = currentRound == 1 ? "round1" : "round2";
        String shuffleKey = currentRound == 1 ? "round1Shuffle" : "round2Shuffle";

        DataSnapshot roundData = snapshot.child(roundKey);
        category = roundData.child("category").getValue(String.class);
        if (category == null) category = "";

        leftItems.clear();
        for (DataSnapshot item : roundData.child("left").getChildren()) {
            leftItems.add(item.getValue(String.class));
        }

        originalRight.clear();
        for (DataSnapshot item : roundData.child("right").getChildren()) {
            originalRight.add(item.getValue(String.class));
        }

        correctMapping = new int[5];
        int idx = 0;
        for (DataSnapshot item : roundData.child("correct").getChildren()) {
            Long val = item.getValue(Long.class);
            if (val != null && idx < 5) {
                correctMapping[idx] = val.intValue();
            }
            idx++;
        }

        List<Integer> shuffleOrder = new ArrayList<>();
        for (DataSnapshot s : snapshot.child(shuffleKey).getChildren()) {
            Long val = s.getValue(Long.class);
            if (val != null) shuffleOrder.add(val.intValue());
        }

        rightItems.clear();
        if (shuffleOrder.size() == 5) {
            for (int si : shuffleOrder) {
                if (si < originalRight.size()) {
                    rightItems.add(originalRight.get(si));
                }
            }
        } else {
            rightItems.addAll(originalRight);
        }
    }

    private boolean isMyTurn() {
        return myRole != null && myRole.equals(activePlayer);
    }

    private boolean allAttempted() {
        for (int i = 0; i < 5; i++) {
            if (!paired[i] && !attemptedThisTurn[i]) return false;
        }
        return true;
    }

    private void renderUI() {
        if (isChallengeMode) {
            tvRound.setText("Runda 1/1");
        } else {
            tvRound.setText("Runda " + currentRound + "/2");
        }
        updateScores();

        if (gameEnded) {
            tvPhase.setText("Kraj igre!");
        } else if (isMyTurn()) {
            if ("opponent_chance".equals(roundPhase)) {
                tvPhase.setText("Tvoja šansa za preostale parove!");
            } else {
                tvPhase.setText("Tvoj red - poveži pojmove");
            }
        } else {
            if ("opponent_chance".equals(roundPhase)) {
                tvPhase.setText("Protivnik pokušava preostale...");
            } else {
                tvPhase.setText("Protivnik povezuje pojmove...");
            }
        }

        // Set button texts and states
        for (int i = 0; i < 5; i++) {
            if (i < leftItems.size()) leftButtons[i].setText(leftItems.get(i));

            if (paired[i]) {
                // Već uspešno spojen - zelen, disabled
                leftButtons[i].setEnabled(false);
                leftButtons[i].setAlpha(0.5f);
                leftButtons[i].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            } else if (attemptedThisTurn[i]) {
                // Već probano u ovoj fazi - ne može opet
                leftButtons[i].setEnabled(false);
                leftButtons[i].setAlpha(0.4f);
                leftButtons[i].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#9E9E9E")));
            } else {
                leftButtons[i].setEnabled(isMyTurn() && !gameEnded);
                leftButtons[i].setAlpha(1f);
                if (i == selectedLeft) {
                    leftButtons[i].setBackgroundTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), R.color.black)));
                } else {
                    leftButtons[i].setBackgroundTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), R.color.slagalica_color)));
                }
            }
        }

        // Right buttons - find which are paired
        boolean[] rightPaired = new boolean[5];
        for (int i = 0; i < 5; i++) {
            if (paired[i]) {
                String correctRightText = originalRight.get(correctMapping[i]);
                for (int j = 0; j < rightItems.size(); j++) {
                    if (rightItems.get(j).equals(correctRightText)) {
                        rightPaired[j] = true;
                    }
                }
            }
        }

        for (int i = 0; i < 5; i++) {
            if (i < rightItems.size()) rightButtons[i].setText(rightItems.get(i));

            if (rightPaired[i]) {
                rightButtons[i].setEnabled(false);
                rightButtons[i].setAlpha(0.5f);
                rightButtons[i].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            } else {
                rightButtons[i].setEnabled(isMyTurn() && !gameEnded);
                rightButtons[i].setAlpha(1f);
                if (i == selectedRight) {
                    rightButtons[i].setBackgroundTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), R.color.black)));
                } else {
                    rightButtons[i].setBackgroundTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), R.color.slagalica_color)));
                }
            }
        }

        int pairedCount = 0;
        for (boolean p : paired) if (p) pairedCount++;
        tvConnectedPairs.setText("Povezano parova: " + pairedCount + "/5");

        boolean canInteract = isMyTurn() && !gameEnded;
        btnConnect.setEnabled(canInteract);
        btnClear.setEnabled(canInteract);
        btnFinish.setEnabled(canInteract);
    }

    private void selectLeft(int index) {
        if (!isMyTurn() || paired[index] || gameEnded) return;
        if (attemptedThisTurn[index]) return; // ne može u obe faze
        selectedLeft = index;
        renderUI();
        updateSelectionText();
    }

    private void selectRight(int index) {
        if (!isMyTurn() || gameEnded) return;
        selectedRight = index;
        renderUI();
        updateSelectionText();
    }

    private void updateSelectionText() {
        String left = selectedLeft >= 0 ? leftButtons[selectedLeft].getText().toString() : "-";
        String right = selectedRight >= 0 ? rightButtons[selectedRight].getText().toString() : "-";
        tvSelectedPair.setText("Izabrano: " + left + " — " + right);
    }

    private void clearSelection() {
        selectedLeft = -1;
        selectedRight = -1;
        renderUI();
        tvSelectedPair.setText("Izabrano: -");
    }

    private void connectPair() {
        if (selectedLeft < 0 || selectedRight < 0 || !isMyTurn()) {
            Toast.makeText(getContext(), "Izaberi pojam iz obe kolone.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (paired[selectedLeft]) {
            Toast.makeText(getContext(), "Već povezano.", Toast.LENGTH_SHORT).show();
            return;
        }

        String selectedRightText = rightButtons[selectedRight].getText().toString();
        String correctRightText = originalRight.get(correctMapping[selectedLeft]);
        boolean isCorrect = selectedRightText.equals(correctRightText);

        Map<String, Object> update = new HashMap<>();
        // U OBE faze: probano = true (može jednom)
        update.put("attempted/" + selectedLeft, true);

        if (isCorrect) {
            update.put("paired/" + selectedLeft, true);
            String scorer = isMe1 ? "player1Score" : "player2Score";
            int currentScore = isMe1 ? player1Score : player2Score;
            update.put(scorer, currentScore + POINTS_PER_PAIR);
            Toast.makeText(getContext(), "Tačno! +2 boda", Toast.LENGTH_SHORT).show();
        } else {
            if ("main".equals(roundPhase)) {
                Toast.makeText(getContext(), "Netačno! Pojam ostaje za protivnika.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Netačno!", Toast.LENGTH_SHORT).show();
            }
        }

        selectedLeft = -1;
        selectedRight = -1;
        tvSelectedPair.setText("Izabrano: -");

        gameStateRef.updateChildren(update).addOnSuccessListener(v -> {
            checkAllAttemptedAfterUpdate();
        });
    }

    private void checkAllAttemptedAfterUpdate() {
        gameStateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean allDone = true;
                int pairedCnt = 0;
                for (int i = 0; i < 5; i++) {
                    Boolean p = snapshot.child("paired").child(String.valueOf(i)).getValue(Boolean.class);
                    Boolean a = snapshot.child("attempted").child(String.valueOf(i)).getValue(Boolean.class);
                    boolean isPaired = Boolean.TRUE.equals(p);
                    boolean isAttempted = Boolean.TRUE.equals(a);
                    if (isPaired) pairedCnt++;
                    if (!isPaired && !isAttempted) allDone = false;
                }

                String phase = snapshot.child("roundPhase").getValue(String.class);

                // Ako su svi parovi povezani - kraj runde
                if (pairedCnt >= 5) {
                    if (roundTimer != null) roundTimer.cancel();
                    advanceRound();
                    return;
                }

                // Ako su svi levi probani u main fazi:
                // - u challenge/solo modu nema protivnika koji bi imao "šansu" za preostale
                //   parove, pa se ide ODMAH na sledeću rundu (FIX: ranije se ovde uvek
                //   zvalo startOpponentChance(), što je u challenge modu zaglavljivalo igru
                //   jer "opponent_chance" prebacuje red na ghost "player2" koji ne postoji)
                // - u običnom 1v1 modu i dalje se daje šansa protivniku
                if ("main".equals(phase) && allDone) {
                    if (roundTimer != null) roundTimer.cancel();
                    if (isChallengeMode) {
                        advanceRound();
                    } else {
                        startOpponentChance();
                    }
                }
                // Ako su svi probani u opponent_chance fazi - kraj runde
                else if ("opponent_chance".equals(phase) && allDone) {
                    if (roundTimer != null) roundTimer.cancel();
                    advanceRound();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void finishMyTurn() {
        if (!isMyTurn()) return;
        if (roundTimer != null) roundTimer.cancel();

        if ("main".equals(roundPhase)) {
            int unpairedCount = 0;
            for (boolean p : paired) if (!p) unpairedCount++;

            if (unpairedCount > 0) {
                if (isChallengeMode) {
                    advanceRound();
                } else {
                    startOpponentChance();
                }
            } else {
                advanceRound();
            }
        } else {
            advanceRound();
        }
    }

    private void startOpponentChance() {
        String opponent = isMe1 ? "player2" : "player1";
        Map<String, Object> u = new HashMap<>();
        u.put("activePlayer", opponent);
        u.put("roundPhase", "opponent_chance");
        u.put("roundStart", System.currentTimeMillis());

        Map<String, Object> attemptedMap = new HashMap<>();
        for (int i = 0; i < 5; i++) attemptedMap.put(String.valueOf(i), false);
        u.put("attempted", attemptedMap);

        gameStateRef.updateChildren(u);
    }

    private void advanceRound() {
        if (isChallengeMode) {
            gameStateRef.child("phase").setValue("finished");
            return;
        }
        if (currentRound == 1) {
            Map<String, Object> u = new HashMap<>();
            u.put("round", 2);
            u.put("activePlayer", isChallengeMode ? myRole : "player2");
            u.put("roundPhase", "main");
            u.put("roundStart", System.currentTimeMillis());

            Map<String, Object> pairedMap = new HashMap<>();
            for (int i = 0; i < 5; i++) pairedMap.put(String.valueOf(i), false);
            u.put("paired", pairedMap);

            Map<String, Object> attemptedMap = new HashMap<>();
            for (int i = 0; i < 5; i++) attemptedMap.put(String.valueOf(i), false);
            u.put("attempted", attemptedMap);

            gameStateRef.updateChildren(u);
        } else {
            gameStateRef.child("phase").setValue("finished");
        }
    }

    private void startTimer(long roundStart) {
        if (roundTimer != null) roundTimer.cancel();
        long elapsed = System.currentTimeMillis() - roundStart;
        long remaining = ROUND_DURATION_MS - elapsed;

        if (remaining <= 0) {
            if (isMyTurn()) finishMyTurn();
            return;
        }

        roundTimer = new CountDownTimer(remaining, 100) {
            @Override
            public void onTick(long ms) {
                if (tvTimer != null) {
                    tvTimer.setText(String.format(Locale.US, "%.1f", ms / 1000.0));
                }
            }

            @Override
            public void onFinish() {
                if (tvTimer != null) tvTimer.setText("0.0");
                if (isMyTurn()) finishMyTurn();
            }
        }.start();
    }

    private void endGame() {
        if (resultSent) return;
        resultSent = true;
        if (roundTimer != null) roundTimer.cancel();
        if (gameStateListener != null) {
            gameStateRef.removeEventListener(gameStateListener);
            gameStateListener = null;
        }

        int myScore = isMe1 ? player1Score : player2Score;
        Bundle result = new Bundle();
        result.putInt("myScore", myScore);
        getParentFragmentManager().setFragmentResult("game_finished", result);
    }

    private void updateScores() {
        if (tvScores != null) {
            tvScores.setText("Igrač 1: " + player1Score + " | Igrač 2: " + player2Score);
        }
    }

    private void setInputEnabled(boolean enabled) {
        for (Button b : leftButtons) if (b != null) b.setEnabled(enabled);
        for (Button b : rightButtons) if (b != null) b.setEnabled(enabled);
        if (btnConnect != null) btnConnect.setEnabled(enabled);
        if (btnClear != null) btnClear.setEnabled(enabled);
        if (btnFinish != null) btnFinish.setEnabled(enabled);
    }

    private List<Integer> getShuffledIndices(String seed) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < 5; i++) indices.add(i);
        if (seed != null) {
            Collections.shuffle(indices, new Random(seed.hashCode()));
        } else {
            Collections.shuffle(indices);
        }
        return indices;
    }

    private Map<String, Object> getFallbackRound1() {
        Map<String, Object> r = new HashMap<>();
        r.put("category", "Poveži izvođače sa pesmama");
        List<String> left = new ArrayList<>();
        left.add("Bajaga");
        left.add("Zdravko Čolić");
        left.add("Riblja Čorba");
        left.add("Marija Šerifović");
        left.add("Đorđe Balašević");
        r.put("left", left);
        List<String> right = new ArrayList<>();
        right.add("Moji drugovi");
        right.add("Ti se ljubiš");
        right.add("Pogledaj dom svoj anđele");
        right.add("Molitva");
        right.add("Priča o Vasi Ladačkom");
        r.put("right", right);
        List<Integer> correct = new ArrayList<>();
        correct.add(0);
        correct.add(1);
        correct.add(2);
        correct.add(3);
        correct.add(4);
        r.put("correct", correct);
        return r;
    }

    private Map<String, Object> getFallbackRound2() {
        Map<String, Object> r = new HashMap<>();
        r.put("category", "Poveži države sa gradovima");
        List<String> left = new ArrayList<>();
        left.add("Francuska");
        left.add("Nemačka");
        left.add("Italija");
        left.add("Španija");
        left.add("Portugal");
        r.put("left", left);
        List<String> right = new ArrayList<>();
        right.add("Berlin");
        right.add("Rim");
        right.add("Pariz");
        right.add("Lisabon");
        right.add("Madrid");
        r.put("right", right);
        List<Integer> correct = new ArrayList<>();
        correct.add(2);
        correct.add(0);
        correct.add(1);
        correct.add(4);
        correct.add(3);
        r.put("correct", correct);
        return r;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (roundTimer != null) roundTimer.cancel();
        if (gameStateListener != null) {
            gameStateRef.removeEventListener(gameStateListener);
        }
        if (scoresListener != null && sessionScoresRef != null) sessionScoresRef.removeEventListener(scoresListener);
    }

    private void listenToSessionScores(String sessionId) {
        sessionScoresRef = FirebaseDatabase.getInstance(
                "https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app"
        ).getReference("sessions").child(sessionId).child("scores");

        scoresListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                Integer s1 = snap.child("player1").getValue(Integer.class);
                Integer s2 = snap.child("player2").getValue(Integer.class);
                if (tvScores != null) tvScores.setText("Igrač 1: " + (s1 != null ? s1 : 0) + " | Igrač 2: " + (s2 != null ? s2 : 0));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
            }
        };
        sessionScoresRef.addValueEventListener(scoresListener);
    }
}