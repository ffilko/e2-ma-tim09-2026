package com.example.slagalica.ui.fragments.games;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class KorakPoKorakFragment extends Fragment {

    private static final int MAX_POINTS = 20;
    private static final int POINTS_PER_STEP_LOSS = 2;
    private static final int OPPONENT_BONUS = 5;
    private static final int STEP_DURATION_MS = 10_000;

    private TextView tvRound, tvTimer, tvPhase, tvScores;
    private LinearLayout llClues;
    private EditText etGuess;
    private Button btnGuess;

    private boolean isMe1;
    private DatabaseReference gameStateRef;
    private ValueEventListener gameStateListener;

    private int currentRound = 1;
    private int currentStep = 0;
    private boolean isActivePlayer = false;

    private int player1Score = 0;
    private int player2Score = 0;

    private String[] steps;
    private String answer;
    private CountDownTimer stepTimer;
    private TextView[] stepViews;
    private boolean round2Prepared = false;
    private boolean gameEnded = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_korak_po_korak, container, false);

        String myRole = getArguments() != null ? getArguments().getString("myRole") : "player1";
        String sessionId = getArguments() != null ? getArguments().getString("sessionId") : null;
        isMe1 = "player1".equals(myRole);

        gameStateRef = FirebaseDatabase.getInstance(
                "https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app"
        ).getReference("sessions").child(sessionId).child("gameState");

        tvRound  = view.findViewById(R.id.tvRound);
        tvTimer  = view.findViewById(R.id.tvTimer);
        tvPhase  = view.findViewById(R.id.tvPhase);
        tvScores = view.findViewById(R.id.tvScores);
        llClues  = view.findViewById(R.id.llClues);
        etGuess  = view.findViewById(R.id.etGuess);
        btnGuess = view.findViewById(R.id.btnGuess);

        etGuess.setEnabled(false);
        btnGuess.setEnabled(false);

        btnGuess.setOnClickListener(v -> {
            if (!isActivePlayer) return;
            String guess = etGuess.getText().toString().trim();
            if (guess.isEmpty()) return;
            etGuess.setText("");
            etGuess.setEnabled(false);
            btnGuess.setEnabled(false);
            gameStateRef.child("lastGuess").setValue(guess);
            gameStateRef.child("lastGuessBy").setValue(isMe1 ? "player1" : "player2");
        });

        if (isMe1) {
            loadAndPublishQuestion(1);
        } else {
            waitForQuestion();
        }

        return view;
    }

    private void loadAndPublishQuestion(int round) {
        FirebaseFirestore.getInstance()
                .collection("games")
                .document("korak_po_korak")
                .collection("questions")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        int idx = new Random().nextInt(querySnapshot.size());
                        Map<String, Object> data = querySnapshot.getDocuments().get(idx).getData();
                        List<String> stepList = (List<String>) data.get("steps");
                        answer = (String) data.get("answer");
                        steps = stepList.toArray(new String[0]);
                    } else {
                        setFallbackData();
                    }

                    Map<String, Object> questionData = new HashMap<>();
                    questionData.put("answer", answer);
                    questionData.put("steps", Arrays.asList(steps));
                    questionData.put("round", round);
                    questionData.put("currentStep", 0);
                    questionData.put("phase", "playing");

                    if (round == 1) {
                        questionData.put("player1Score", 0);
                        questionData.put("player2Score", 0);
                    } else {
                        questionData.put("player1Score", player1Score);
                        questionData.put("player2Score", player2Score);
                    }
                    questionData.put("lastGuess", "");
                    questionData.put("lastGuessBy", "");
                    questionData.put("opponentChance", false);
                    questionData.put("guessProcessed", false);

                    gameStateRef.setValue(questionData).addOnSuccessListener(v -> {
                        if (round == 1 && gameStateListener == null) {
                            listenToGameState();
                        }

                        isActivePlayer = (round == 1 && isMe1) || (round == 2 && !isMe1);

                        if (isActivePlayer) {
                            etGuess.setEnabled(true);
                            btnGuess.setEnabled(true);
                            tvPhase.setText("Tvoj red — pogađaj!");
                        } else {
                            tvPhase.setText(round == 1 ? "Igrač 1 pogađa..." : "Igrač 2 pogađa...");
                        }

                        tvRound.setText("Runda " + round + "/2");
                        currentStep = 0;
                        initClueViews();


                        boolean iAmTimerController = (round == 1 && isMe1) || (round == 2 && !isMe1);
                        if (iAmTimerController) {
                            startRevealLoop();
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    setFallbackData();
                    loadAndPublishQuestion(round);
                });
    }

    private void waitForQuestion() {
        gameStateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.child("answer").exists()) {
                    readQuestionFromSnapshot(snapshot);
                    if (gameStateListener == null) {
                        listenToGameState();
                    }
                } else {
                    llClues.postDelayed(KorakPoKorakFragment.this::waitForQuestion, 500);
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    private void readQuestionFromSnapshot(DataSnapshot snapshot) {
        answer = snapshot.child("answer").getValue(String.class);
        List<String> stepList = new ArrayList<>();
        for (DataSnapshot s : snapshot.child("steps").getChildren()) {
            stepList.add(s.getValue(String.class));
        }
        steps = stepList.toArray(new String[0]);

        Integer p1s = snapshot.child("player1Score").getValue(Integer.class);
        Integer p2s = snapshot.child("player2Score").getValue(Integer.class);
        if (p1s != null) player1Score = p1s;
        if (p2s != null) player2Score = p2s;

        Integer round = snapshot.child("round").getValue(Integer.class);
        if (round != null) currentRound = round;

        initClueViews();
        updateScores();
        tvRound.setText("Runda " + currentRound + "/2");

        isActivePlayer = (currentRound == 1 && isMe1) || (currentRound == 2 && !isMe1);
        tvPhase.setText(isActivePlayer ? "Tvoj red — pogađaj!" :
                (currentRound == 1 ? "Igrač 1 pogađa..." : "Igrač 2 pogađa..."));

        if (isActivePlayer) {
            etGuess.setEnabled(true);
            btnGuess.setEnabled(true);
        }
    }

    private void initClueViews() {
        if (steps == null) return;
        llClues.removeAllViews();
        stepViews = new TextView[steps.length];
        for (int i = 0; i < steps.length; i++) {
            TextView tv = createStepView("??? Korak " + (i + 1));
            stepViews[i] = tv;
            llClues.addView(tv);
        }
    }

    private void listenToGameState() {
        gameStateListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists() || gameEnded) return;

                Integer step = snapshot.child("currentStep").getValue(Integer.class);
                Integer round = snapshot.child("round").getValue(Integer.class);
                Integer p1s = snapshot.child("player1Score").getValue(Integer.class);
                Integer p2s = snapshot.child("player2Score").getValue(Integer.class);
                Boolean oppChance = snapshot.child("opponentChance").getValue(Boolean.class);
                String phase = snapshot.child("phase").getValue(String.class);
                String lastGuess = snapshot.child("lastGuess").getValue(String.class);
                Boolean guessProcessed = snapshot.child("guessProcessed").getValue(Boolean.class);


                if (p1s != null) player1Score = p1s;
                if (p2s != null) player2Score = p2s;
                updateScores();


                if (step != null && stepViews != null && steps != null) {
                    for (int i = 0; i < step && i < stepViews.length; i++) {
                        if (i < steps.length) {
                            stepViews[i].setText(steps[i]);
                            stepViews[i].setBackgroundTintList(
                                    android.content.res.ColorStateList.valueOf(
                                            getResources().getColor(R.color.slagalica_color)));
                        }
                    }
                }


                Long timerMs = snapshot.child("timerMs").getValue(Long.class);
                if (timerMs != null) {
                    tvTimer.setText(String.format("%.1f", timerMs / 1000.0));
                }


                if (Boolean.TRUE.equals(oppChance)) {
                    int r = round != null ? round : 1;

                    boolean myOppChance = (r == 1 && !isMe1) || (r == 2 && isMe1);
                    if (myOppChance && !isActivePlayer) {
                        tvPhase.setText("Tvoja šansa! Pogodi za 5 bodova (10s)");
                        isActivePlayer = true;
                        etGuess.setEnabled(true);
                        btnGuess.setEnabled(true);
                    } else if (!myOppChance) {
                        tvPhase.setText("Protivnik ima šansu...");
                        isActivePlayer = false;
                        etGuess.setEnabled(false);
                        btnGuess.setEnabled(false);
                    }
                }


                if (lastGuess != null && !lastGuess.isEmpty()
                        && !Boolean.TRUE.equals(guessProcessed)) {
                    int r = round != null ? round : 1;
                    boolean iAmController = (r == 1 && isMe1) || (r == 2 && !isMe1);
                    if (iAmController) {
                        gameStateRef.child("guessProcessed").setValue(true);
                        handleGuessFromFirebase(lastGuess,
                                Boolean.TRUE.equals(oppChance), r);
                    }
                }

                if ("startRound2".equals(phase) && !round2Prepared) {
                    round2Prepared = true;
                    prepareRound2();
                }

                if ("finished".equals(phase) && !gameEnded) {
                    gameEnded = true;
                    endGame();
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        gameStateRef.addValueEventListener(gameStateListener);
    }

    private void handleGuessFromFirebase(String guess, boolean oppChance, int round) {
        boolean correct = guess.equalsIgnoreCase(answer);

        if (oppChance) {
            if (correct) {
                if (round == 1) player2Score += OPPONENT_BONUS;
                else player1Score += OPPONENT_BONUS;
            }
            if (stepTimer != null) stepTimer.cancel();

            gameStateRef.child("lastGuess").setValue("");
            gameStateRef.child("guessProcessed").setValue(false);
            finishRound(round);
        } else {
            if (correct) {
                int points = MAX_POINTS - (currentStep - 1) * POINTS_PER_STEP_LOSS;
                if (points < 0) points = 0;
                if (round == 1) player1Score += points;
                else player2Score += points;

                if (stepTimer != null) stepTimer.cancel();
                gameStateRef.child("lastGuess").setValue("");
                gameStateRef.child("guessProcessed").setValue(false);
                finishRound(round);
            } else {
                gameStateRef.child("lastGuess").setValue("");
                gameStateRef.child("guessProcessed").setValue(false);
                if (isActivePlayer) {
                    etGuess.setEnabled(true);
                    btnGuess.setEnabled(true);
                }
            }
        }

        gameStateRef.child("player1Score").setValue(player1Score);
        gameStateRef.child("player2Score").setValue(player2Score);
    }

    private void startRevealLoop() {
        if (stepViews == null) initClueViews();
        revealStep();
    }

    private void revealStep() {
        if (steps == null || currentStep >= steps.length) {
            gameStateRef.child("opponentChance").setValue(true);
            gameStateRef.child("phase").setValue("opponentChance");

            if (stepTimer != null) stepTimer.cancel();
            stepTimer = new CountDownTimer(10_000, 100) {
                @Override public void onTick(long ms) {
                    tvTimer.setText(String.format("%.1f", ms / 1000.0));
                    gameStateRef.child("timerMs").setValue(ms);
                }
                @Override public void onFinish() {
                    tvTimer.setText("0.0");
                    finishRound(currentRound);
                }
            }.start();
            return;
        }

        gameStateRef.child("currentStep").setValue(currentStep + 1);
        currentStep++;

        if (stepTimer != null) stepTimer.cancel();
        stepTimer = new CountDownTimer(STEP_DURATION_MS, 100) {
            @Override public void onTick(long ms) {
                tvTimer.setText(String.format("%.1f", ms / 1000.0));
                gameStateRef.child("timerMs").setValue(ms);
            }
            @Override public void onFinish() {
                tvTimer.setText("0.0");
                revealStep();
            }
        }.start();
    }

    private void finishRound(int round) {
        if (stepTimer != null) stepTimer.cancel();
        isActivePlayer = false;
        etGuess.setEnabled(false);
        btnGuess.setEnabled(false);

        if (round == 1) {
            gameStateRef.child("opponentChance").setValue(false);
            gameStateRef.child("currentStep").setValue(0);
            gameStateRef.child("lastGuess").setValue("");
            gameStateRef.child("guessProcessed").setValue(false);
            gameStateRef.child("phase").setValue("startRound2");
        } else {
            gameStateRef.child("phase").setValue("finished");
        }
    }

    private void prepareRound2() {
        currentRound = 2;
        currentStep = 0;
        tvRound.setText("Runda 2/2");

        isActivePlayer = !isMe1;

        if (isActivePlayer) {
            tvPhase.setText("Tvoj red — pogađaj!");
            etGuess.setEnabled(true);
            btnGuess.setEnabled(true);
        } else {
            tvPhase.setText("Igrač 2 pogađa...");
            etGuess.setEnabled(false);
            btnGuess.setEnabled(false);
        }

        if (isMe1) {
            FirebaseFirestore.getInstance()
                    .collection("games")
                    .document("korak_po_korak")
                    .collection("questions")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!querySnapshot.isEmpty()) {
                            int idx = new Random().nextInt(querySnapshot.size());
                            Map<String, Object> data = querySnapshot.getDocuments()
                                    .get(idx).getData();
                            List<String> stepList = (List<String>) data.get("steps");
                            answer = (String) data.get("answer");
                            steps = stepList.toArray(new String[0]);
                        } else {
                            setFallbackData();
                        }

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("answer", answer);
                        updates.put("steps", Arrays.asList(steps));
                        updates.put("round", 2);
                        updates.put("phase", "playing");
                        updates.put("currentStep", 0);
                        updates.put("opponentChance", false);
                        updates.put("lastGuess", "");
                        updates.put("guessProcessed", false);
                        updates.put("player1Score", player1Score);
                        updates.put("player2Score", player2Score);
                        gameStateRef.updateChildren(updates).addOnSuccessListener(v -> {
                            initClueViews();
                        });
                    });
        } else {
            waitForRound2QuestionThenReveal();
        }
    }

    private void waitForRound2QuestionThenReveal() {
        gameStateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String phase = snapshot.child("phase").getValue(String.class);
                Integer round = snapshot.child("round").getValue(Integer.class);

                if ("playing".equals(phase) && Integer.valueOf(2).equals(round)) {
                    answer = snapshot.child("answer").getValue(String.class);
                    List<String> sl = new ArrayList<>();
                    for (DataSnapshot s : snapshot.child("steps").getChildren())
                        sl.add(s.getValue(String.class));
                    steps = sl.toArray(new String[0]);
                    initClueViews();
                    startRevealLoop();
                } else {
                    llClues.postDelayed(
                            () -> waitForRound2QuestionThenReveal(), 300);
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    private void endGame() {
        if (stepTimer != null) stepTimer.cancel();
        if (gameStateListener != null) {
            gameStateRef.removeEventListener(gameStateListener);
            gameStateListener = null;
        }

        int myFinalScore = isMe1 ? player1Score : player2Score;
        Bundle result = new Bundle();
        result.putBoolean("finished", true);
        result.putInt("myScore", myFinalScore);
        getParentFragmentManager().setFragmentResult("game_finished", result);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (stepTimer != null) stepTimer.cancel();
        if (gameStateListener != null) {
            gameStateRef.removeEventListener(gameStateListener);
        }
    }

    private void updateScores() {
        tvScores.setText("Igrač 1: " + player1Score + "  |  Igrač 2: " + player2Score);
    }

    private void setFallbackData() {
        steps = new String[]{
                "Korak 1 — Rođen je 1856. godine.",
                "Korak 2 — Studirao je elektrotehniku u Gracu i Pragu.",
                "Korak 3 — Radio je u Njujorku.",
                "Korak 4 — Poznat po naizmeničnoj struji (AC).",
                "Korak 5 — Izgradio je Vordenklifovu kulu.",
                "Korak 6 — Jedinica za magnetnu indukciju nosi njegovo ime.",
                "Korak 7 — Nalazi se na srpskoj novčanici od 100 dinara."
        };
        answer = "Nikola Tesla";
    }

    private TextView createStepView(String text) {
        int color = getResources().getColor(android.R.color.darker_gray);
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setPadding(32, 24, 32, 24);
        tv.setTextColor(android.graphics.Color.WHITE);
        tv.setTextSize(16f);
        android.graphics.drawable.GradientDrawable shape =
                new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setCornerRadius(15f);
        shape.setColor(color);
        tv.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        tv.setLayoutParams(params);
        return tv;
    }
}