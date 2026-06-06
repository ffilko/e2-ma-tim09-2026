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
import com.google.firebase.firestore.FirebaseFirestore;

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

    // Stanje igre
    private int currentRound = 1;
    private int currentStep = 0;
    private boolean isPlayer1Turn = true;
    private boolean roundActive = false;
    private boolean opponentChance = false;

    private int player1Score = 0;
    private int player2Score = 0;

    private String[] steps;
    private String answer;

    private CountDownTimer stepTimer;

    private TextView[] stepViews;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_korak_po_korak, container, false);

        tvRound  = view.findViewById(R.id.tvRound);
        tvTimer  = view.findViewById(R.id.tvTimer);
        tvPhase  = view.findViewById(R.id.tvPhase);
        tvScores = view.findViewById(R.id.tvScores);
        llClues  = view.findViewById(R.id.llClues);
        etGuess  = view.findViewById(R.id.etGuess);
        btnGuess = view.findViewById(R.id.btnGuess);

        loadGameDataAndStart();

        btnGuess.setOnClickListener(v -> {
            String guess = etGuess.getText().toString().trim();
            etGuess.setText("");
            handleGuess(guess);
        });

        return view;
    }


    private void loadGameDataAndStart() {
        FirebaseFirestore.getInstance()
                .collection("games")
                .document("korak_po_korak")
                .collection("questions")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("KPK", "Broj pitanja: " + querySnapshot.size());

                    if (!querySnapshot.isEmpty()) {
                        int randomIndex = new Random().nextInt(querySnapshot.size());
                        android.util.Log.d("KPK", "Odabran index: " + randomIndex);

                        Map<String, Object> data = querySnapshot.getDocuments()
                                .get(randomIndex).getData();
                        android.util.Log.d("KPK", "Podaci: " + data);

                        List<String> stepList = (List<String>) data.get("steps");
                        answer = (String) data.get("answer");
                        android.util.Log.d("KPK", "Answer: " + answer);

                        steps = stepList.toArray(new String[0]);
                    } else {
                        android.util.Log.d("KPK", "Baza prazna, koristim fallback");
                        setFallbackData();
                    }
                    startRound();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("KPK", "Firestore greška: " + e.getMessage());
                    setFallbackData();
                    startRound();
                });
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


    private void startRound() {
        currentStep = 0;
        roundActive = true;
        opponentChance = false;
        llClues.removeAllViews();
        stepViews = new TextView[steps.length];

        tvRound.setText("Runda " + currentRound + "/2");
        tvPhase.setText(isPlayer1Turn ? "Igrač 1 pogađa" : "Igrač 2 pogađa");
        updateScores();

        for (int i = 0; i < steps.length; i++) {
            TextView tv = createStepView("??? Korak " + (i + 1));
            stepViews[i] = tv;
            llClues.addView(tv);
        }

        revealNextStep();
    }

    private void revealNextStep() {
        if (!roundActive) return;
        if (currentStep >= steps.length) {
            endRoundWithNoGuess();
            return;
        }

        stepViews[currentStep].setText(steps[currentStep]);
        stepViews[currentStep].setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        getResources().getColor(R.color.slagalica_color)));

        currentStep++;

        if (stepTimer != null) stepTimer.cancel();
        stepTimer = new CountDownTimer(STEP_DURATION_MS, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(String.format("%.1f", millisUntilFinished / 1000.0));
            }
            @Override
            public void onFinish() {
                tvTimer.setText("0.0");
                revealNextStep();
            }
        }.start();
    }

    private void handleGuess(String guess) {
        if (!roundActive) return;

        boolean correct = guess.equalsIgnoreCase(answer);

        if (opponentChance) {
            if (correct) {
                if (isPlayer1Turn) {
                    player2Score += OPPONENT_BONUS;
                } else {
                    player1Score += OPPONENT_BONUS;
                }
            }
            finishRound();
        } else {
            if (correct) {
                int points = MAX_POINTS - (currentStep - 1) * POINTS_PER_STEP_LOSS;
                if (points < 0) points = 0;
                if (isPlayer1Turn) {
                    player1Score += points;
                } else {
                    player2Score += points;
                }
                finishRound();
            }
        }
        updateScores();
    }

    private void endRoundWithNoGuess() {
        roundActive = false;
        opponentChance = true;
        if (stepTimer != null) stepTimer.cancel();

        tvPhase.setText(isPlayer1Turn ? "Igrač 2 ima šansu (10s)!" : "Igrač 1 ima šansu (10s)!");

        stepTimer = new CountDownTimer(10_000, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(String.format("%.1f", millisUntilFinished / 1000.0));
            }
            @Override
            public void onFinish() {
                tvTimer.setText("0.0");
                finishRound();
            }
        }.start();
    }

    private void finishRound() {
        roundActive = false;
        opponentChance = false;
        if (stepTimer != null) stepTimer.cancel();
        updateScores();

        if (currentRound == 1) {
            currentRound = 2;
            isPlayer1Turn = !isPlayer1Turn;
            llClues.postDelayed(this::startRound, 1500);
        } else {
            llClues.postDelayed(this::endGame, 1500);
        }
    }

    private void endGame() {
        if (stepTimer != null) stepTimer.cancel();
        Bundle result = new Bundle();
        result.putBoolean("finished", true);
        result.putInt("player1Score", player1Score);
        result.putInt("player2Score", player2Score);
        getParentFragmentManager().setFragmentResult("game_finished", result);
    }

    private void updateScores() {
        tvScores.setText("Igrač 1: " + player1Score + "  |  Igrač 2: " + player2Score);
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (stepTimer != null) stepTimer.cancel();
    }
}