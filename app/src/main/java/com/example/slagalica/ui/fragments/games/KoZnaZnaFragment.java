package com.example.slagalica.ui.fragments.games;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class KoZnaZnaFragment extends Fragment {

    private static final int TOTAL_QUESTIONS = 5;
    private static final long QUESTION_TIME_MS = 5000;
    private static final int CORRECT_POINTS = 10;
    private static final int WRONG_POINTS = -5;

    private TextView tvRound, tvTimer, tvPhase, tvScores, tvQuestionCounter, tvQuestion, tvSelectedAnswer;
    private Button[] answerButtons = new Button[4];
    private Button btnSubmit;

    private boolean isMe1;
    private String myRole;
    private DatabaseReference gameStateRef;
    private ValueEventListener gameStateListener;

    private int currentQuestion = 0;
    private int player1Score = 0;
    private int player2Score = 0;
    private boolean gameEnded = false;
    private boolean answerSubmitted = false;

    private CountDownTimer questionTimer;

    private List<Map<String, Object>> questions = new ArrayList<>();
    private int selectedAnswer = -1;
    private DatabaseReference sessionScoresRef;
    private ValueEventListener scoresListener;
    private boolean questionProcessed = false;
    private boolean isChallengeMode = false;
    private String roundSeed = null;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ko_zna_zna, container, false);

        isChallengeMode = getArguments() != null
                && getArguments().getBoolean("isChallengeMode", false);
        roundSeed = getArguments() != null ? getArguments().getString("roundSeed") : null;

        myRole = getArguments() != null ? getArguments().getString("myRole") : "player1";
        String sessionId = getArguments() != null ? getArguments().getString("sessionId") : null;
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

                        // Ako čekamo protivnikov odgovor, upiši -1 za njega odmah
                        if (isMe1) {
                            // Ja sam controller — upiši protivnikov odgovor kao prazan
                            gameStateRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snap) {
                                    Integer p2a = snap.child("player2Answer").getValue(Integer.class);
                                    if (p2a == null) {
                                        Map<String, Object> update = new HashMap<>();
                                        update.put("player2Answer", -1);
                                        update.put("player2AnswerTime", Long.MAX_VALUE);
                                        gameStateRef.updateChildren(update);
                                    }
                                }
                                @Override public void onCancelled(@NonNull DatabaseError e) {}
                            });
                        } else {
                            // Ja nisam controller — upiši moj odgovor ako ga nisam dao
                            if (!answerSubmitted) {
                                answerSubmitted = true;
                                setButtonsEnabled(false);
                                btnSubmit.setEnabled(false);
                                Map<String, Object> update = new HashMap<>();
                                update.put("player1Answer", -1);
                                update.put("player1AnswerTime", Long.MAX_VALUE);
                                update.put("timeUp", true);
                                gameStateRef.updateChildren(update);
                            }
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });

        tvRound = view.findViewById(R.id.tvRound);
        tvTimer = view.findViewById(R.id.tvTimer);
        tvPhase = view.findViewById(R.id.tvPhase);
        tvScores = view.findViewById(R.id.tvScores);
        tvQuestionCounter = view.findViewById(R.id.tvQuestionCounter);
        tvQuestion = view.findViewById(R.id.tvQuestion);
        tvSelectedAnswer = view.findViewById(R.id.tvSelectedAnswer);
        btnSubmit = view.findViewById(R.id.btnSubmitKoZnaZna);

        answerButtons[0] = view.findViewById(R.id.btnAnswer1);
        answerButtons[1] = view.findViewById(R.id.btnAnswer2);
        answerButtons[2] = view.findViewById(R.id.btnAnswer3);
        answerButtons[3] = view.findViewById(R.id.btnAnswer4);

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            answerButtons[i].setOnClickListener(v -> selectAnswer(idx));
        }

        btnSubmit.setText("Potvrdi odgovor");
        btnSubmit.setOnClickListener(v -> submitAnswer());

        setButtonsEnabled(false);
        tvPhase.setText("Učitavanje pitanja...");

        if (isMe1) {
            loadQuestionsAndPublish();
        } else {
            waitForQuestions();
        }

        return view;
    }

    @SuppressWarnings("unchecked")
    private void loadQuestionsAndPublish() {
        FirebaseFirestore.getInstance()
                .collection("games")
                .document("ko_zna_zna")
                .collection("questions")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        int idx;
                        if (roundSeed != null) {
                            idx = Math.abs(roundSeed.hashCode()) % querySnapshot.size();
                        } else {
                            idx = new Random().nextInt(querySnapshot.size());
                        }
                        Map<String, Object> data = querySnapshot.getDocuments().get(idx).getData();
                        if (data != null) {
                            List<Map<String, Object>> qs = (List<Map<String, Object>>) data.get("questions");
                            if (qs != null) questions = qs;
                        }
                    }

                    if (questions.isEmpty()) {
                        setFallbackQuestions();
                    }

                    Map<String, Object> state = new HashMap<>();
                    state.put("questions", questions);
                    state.put("currentQuestion", 0);
                    state.put("player1Score", 0);
                    state.put("player2Score", 0);
                    state.put("phase", "playing");
                    state.put("player1Answer", null);
                    state.put("player2Answer", null);
                    state.put("player1AnswerTime", null);
                    state.put("player2AnswerTime", null);
                    state.put("questionStartTime", System.currentTimeMillis());

                    gameStateRef.setValue(state).addOnSuccessListener(v -> {
                        listenToGameState();
                        showQuestion(0);
                    });
                })
                .addOnFailureListener(e -> {
                    setFallbackQuestions();

                    Map<String, Object> state = new HashMap<>();
                    state.put("questions", questions);
                    state.put("currentQuestion", 0);
                    state.put("player1Score", 0);
                    state.put("player2Score", 0);
                    state.put("phase", "playing");
                    state.put("questionStartTime", System.currentTimeMillis());

                    gameStateRef.setValue(state).addOnSuccessListener(v -> {
                        listenToGameState();
                        showQuestion(0);
                    });
                });
    }

    private void waitForQuestions() {
        gameStateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.hasChild("questions")) {
                    readQuestionsFromSnapshot(snapshot);
                    listenToGameState();
                    showQuestion(0);
                } else {
                    tvPhase.setText("Čekam drugog igrača...");
                    if (tvQuestion != null) {
                        tvQuestion.postDelayed(() -> waitForQuestions(), 500);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void readQuestionsFromSnapshot(DataSnapshot snapshot) {
        questions.clear();
        DataSnapshot qs = snapshot.child("questions");
        for (DataSnapshot q : qs.getChildren()) {
            Map<String, Object> qMap = new HashMap<>();
            qMap.put("question", q.child("question").getValue(String.class));

            List<String> answers = new ArrayList<>();
            for (DataSnapshot a : q.child("answers").getChildren()) {
                answers.add(a.getValue(String.class));
            }
            qMap.put("answers", answers);

            Long correct = q.child("correct").getValue(Long.class);
            qMap.put("correct", correct != null ? correct.intValue() : 0);
            questions.add(qMap);
        }

        Integer p1 = snapshot.child("player1Score").getValue(Integer.class);
        Integer p2 = snapshot.child("player2Score").getValue(Integer.class);
        if (p1 != null) player1Score = p1;
        if (p2 != null) player2Score = p2;
    }

    private void listenToGameState() {
        if (gameStateListener != null) return;

        gameStateListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists() || gameEnded) return;

                Integer p1 = snapshot.child("player1Score").getValue(Integer.class);
                Integer p2 = snapshot.child("player2Score").getValue(Integer.class);
                if (p1 != null) player1Score = p1;
                if (p2 != null) player2Score = p2;
                updateScores();

                Integer cq = snapshot.child("currentQuestion").getValue(Integer.class);
                String phase = snapshot.child("phase").getValue(String.class);
                questionProcessed = false;

                if ("finished".equals(phase) && !gameEnded) {
                    gameEnded = true;
                    endGame();
                    return;
                }

                if (cq != null && cq != currentQuestion) {
                    currentQuestion = cq;
                    selectedAnswer = -1;
                    answerSubmitted = false;
                    showQuestion(currentQuestion);
                }

                if (!questionProcessed) {

                    if (isChallengeMode) {
                        Integer myAnswer = isMe1
                                ? snapshot.child("player1Answer").getValue(Integer.class)
                                : snapshot.child("player2Answer").getValue(Integer.class);

                        Long myTime = isMe1
                                ? snapshot.child("player1AnswerTime").getValue(Long.class)
                                : snapshot.child("player2AnswerTime").getValue(Long.class);

                        Boolean timeUp = snapshot.child("timeUp").getValue(Boolean.class);

                        if (myAnswer != null || Boolean.TRUE.equals(timeUp)) {
                            questionProcessed = true;

                            processChallengeAnswer(myAnswer, myTime);
                        }
                    } else {
                        Integer p1a = snapshot.child("player1Answer").getValue(Integer.class);
                        Integer p2a = snapshot.child("player2Answer").getValue(Integer.class);
                        Long p1t = snapshot.child("player1AnswerTime").getValue(Long.class);
                        Long p2t = snapshot.child("player2AnswerTime").getValue(Long.class);
                        Boolean timeUp = snapshot.child("timeUp").getValue(Boolean.class);

                        if ((p1a != null && p2a != null) || Boolean.TRUE.equals(timeUp)) {
                            questionProcessed = true;
                            processAnswers(p1a, p2a, p1t, p2t);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        gameStateRef.addValueEventListener(gameStateListener);
    }

    private void processChallengeAnswer(Integer answer, Long time) {

        Map<String, Object> q = questions.get(currentQuestion);
        int correct = ((Long) q.get("correct")).intValue();

        int myAnswer = answer != null ? answer : -1;

        if (myAnswer == correct) {
            if (isMe1) player1Score += CORRECT_POINTS;
            else player2Score += CORRECT_POINTS;
        } else if (myAnswer != -1) {
            if (isMe1) player1Score += WRONG_POINTS;
            else player2Score += WRONG_POINTS;
        }

        updateScores();

        int nextQ = currentQuestion + 1;

        Map<String, Object> update = new HashMap<>();
        update.put("player1Score", player1Score);
        update.put("player2Score", player2Score);

        update.put("player1Answer", null);
        update.put("player2Answer", null);
        update.put("player1AnswerTime", null);
        update.put("player2AnswerTime", null);
        update.put("timeUp", null);

        if (nextQ >= TOTAL_QUESTIONS || nextQ >= questions.size()) {
            update.put("phase", "finished");
        } else {
            update.put("currentQuestion", nextQ);
            update.put("questionStartTime", System.currentTimeMillis());
        }

        gameStateRef.updateChildren(update);
    }

    @SuppressWarnings("unchecked")
    private void showQuestion(int index) {
        if (index >= questions.size() || index >= TOTAL_QUESTIONS) return;

        if (questionTimer != null) questionTimer.cancel();

        Map<String, Object> q = questions.get(index);
        String questionText = (String) q.get("question");
        List<String> answers = (List<String>) q.get("answers");

        tvQuestionCounter.setText("Pitanje " + (index + 1) + "/" + TOTAL_QUESTIONS);
        tvQuestion.setText(questionText != null ? questionText : "");
        tvRound.setText("Runda 1/1");
        tvSelectedAnswer.setText("Odabran odgovor: nijedan");
        tvPhase.setText("Odgovori!");

        for (int i = 0; i < 4; i++) {
            if (answers != null && i < answers.size()) {
                answerButtons[i].setText(answers.get(i));
            }
            answerButtons[i].setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.slagalica_color)));
            answerButtons[i].setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        }

        selectedAnswer = -1;
        answerSubmitted = false;
        questionProcessed = false;
        setButtonsEnabled(true);
        btnSubmit.setEnabled(true);

        questionTimer = new CountDownTimer(QUESTION_TIME_MS, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (tvTimer != null) {
                    tvTimer.setText(String.format(Locale.US, "%.1f", millisUntilFinished / 1000.0));
                }
            }

            @Override
            public void onFinish() {
                if (tvTimer != null) tvTimer.setText("0.0");
                if (isChallengeMode && !answerSubmitted) {
                    submitAnswer();
                } else if (!answerSubmitted && !isChallengeMode) {
                    answerSubmitted = true;
                    setButtonsEnabled(false);
                    btnSubmit.setEnabled(false);
                    tvPhase.setText("Vreme isteklo!");

                    String myAnswerKey = isMe1 ? "player1Answer" : "player2Answer";
                    String myTimeKey = isMe1 ? "player1AnswerTime" : "player2AnswerTime";

                    Map<String, Object> update = new HashMap<>();
                    update.put(myAnswerKey, -1);
                    update.put(myTimeKey, System.currentTimeMillis());
                    if (isMe1) {
                        update.put("timeUp", true);
                    }
                    gameStateRef.updateChildren(update);
                }
            }
        }.start();
    }

    private void selectAnswer(int index) {
        if (answerSubmitted) return;

        selectedAnswer = index;

        for (int i = 0; i < 4; i++) {
            if (i == index) {
                answerButtons[i].setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.black)));
            } else {
                answerButtons[i].setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.slagalica_color)));
            }
            answerButtons[i].setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        }

        tvSelectedAnswer.setText("Odabran odgovor: " + answerButtons[index].getText().toString());
    }

    private void submitAnswer() {
        if (answerSubmitted) return;
        answerSubmitted = true;

        setButtonsEnabled(false);
        btnSubmit.setEnabled(false);

        String myAnswerKey = isMe1 ? "player1Answer" : "player2Answer";
        String myTimeKey = isMe1 ? "player1AnswerTime" : "player2AnswerTime";

        int answer = selectedAnswer >= 0 ? selectedAnswer : -1;

        gameStateRef.child("questionStartTime").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long startTime = snapshot.getValue(Long.class);
                long responseTime = startTime != null
                        ? (System.currentTimeMillis() - startTime)
                        : 0;

                Map<String, Object> update = new HashMap<>();
                update.put(myAnswerKey, answer);
                update.put(myTimeKey, responseTime);
                gameStateRef.updateChildren(update);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        if (isChallengeMode) {
            tvPhase.setText("Obrađujem odgovor...");
        } else {
            tvPhase.setText("Čekam protivnika...");
        }
    }

    private void processAnswers(Integer p1Answer, Integer p2Answer,
                                Long p1Time, Long p2Time) {
        if (currentQuestion >= questions.size()) return;

        Map<String, Object> q = questions.get(currentQuestion);
        Object correctObj = q.get("correct");
        int correct;
        if (correctObj instanceof Long) {
            correct = ((Long) correctObj).intValue();
        } else if (correctObj instanceof Integer) {
            correct = (Integer) correctObj;
        } else {
            correct = 0;
        }

        int p1a = p1Answer != null ? p1Answer : -1;
        int p2a = p2Answer != null ? p2Answer : -1;

        boolean p1Correct = p1a == correct;
        boolean p2Correct = p2a == correct;
        boolean p1Wrong = p1a != -1 && p1a != correct;
        boolean p2Wrong = p2a != -1 && p2a != correct;

        String resultMessage = "";

        if (p1Correct && p2Correct) {
            long t1 = p1Time != null ? p1Time : Long.MAX_VALUE;
            long t2 = p2Time != null ? p2Time : Long.MAX_VALUE;
            if (t1 <= t2) {
                player1Score += CORRECT_POINTS;
                resultMessage = "Oba tačno! Brži je Igrač 1 (+10)";
            } else {
                player2Score += CORRECT_POINTS;
                resultMessage = "Oba tačno! Brži je Igrač 2 (+10)";
            }
        } else {
            if (p1Correct) {
                player1Score += CORRECT_POINTS;
                resultMessage = "Igrač 1 tačno (+10)";
            }
            if (p2Correct) {
                player2Score += CORRECT_POINTS;
                resultMessage += (resultMessage.isEmpty() ? "" : " | ") + "Igrač 2 tačno (+10)";
            }
            if (p1Wrong) {
                player1Score += WRONG_POINTS;
                resultMessage += (resultMessage.isEmpty() ? "" : " | ") + "Igrač 1 netačno (-5)";
            }
            if (p2Wrong) {
                player2Score += WRONG_POINTS;
                resultMessage += (resultMessage.isEmpty() ? "" : " | ") + "Igrač 2 netačno (-5)";
            }
            if (resultMessage.isEmpty()) {
                resultMessage = "Niko nije odgovorio - bodovi ostaju isti";
            }
        }

        if (tvPhase != null) {
            tvPhase.setText(resultMessage);
        }

        int nextQ = currentQuestion + 1;

        Map<String, Object> update = new HashMap<>();
        update.put("player1Score", player1Score);
        update.put("player2Score", player2Score);
        update.put("player1Answer", null);
        update.put("player2Answer", null);
        update.put("player1AnswerTime", null);
        update.put("player2AnswerTime", null);
        update.put("timeUp", null);

        if (nextQ >= TOTAL_QUESTIONS || nextQ >= questions.size()) {
            update.put("phase", "finished");
        } else {
            update.put("currentQuestion", nextQ);
            update.put("questionStartTime", System.currentTimeMillis());
        }

        gameStateRef.updateChildren(update);
    }

    private void endGame() {
        if (questionTimer != null) questionTimer.cancel();
        if (gameStateListener != null) {
            gameStateRef.removeEventListener(gameStateListener);
            gameStateListener = null;
        }

        setButtonsEnabled(false);
        btnSubmit.setEnabled(false);
        tvPhase.setText("Kraj igre!");

        int myScore = isMe1 ? player1Score : player2Score;

        Bundle result = new Bundle();
        result.putInt("myScore", myScore);
        getParentFragmentManager().setFragmentResult("game_finished", result);
    }

    private void updateScores() {
        if (tvScores != null) {
            tvScores.setText("Igrač 1: " +  player1Score + "  |  Igrač 2: " + player2Score);
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        for (Button b : answerButtons) {
            if (b != null) b.setEnabled(enabled);
        }
    }

    private void setFallbackQuestions() {
        questions.clear();
        String[][] data = {
                {"Koji je glavni grad Srbije?", "Novi Sad", "Niš", "Beograd", "Kragujevac", "2"},
                {"Koja reka protiče kroz Beograd?", "Morava", "Dunav", "Tisa", "Drina", "1"},
                {"Ko je napisao Na Drini ćuprija?", "Meša Selimović", "Ivo Andrić", "Danilo Kiš", "Borislav Pekić", "1"},
                {"Koliko strana ima šestougao?", "5", "6", "7", "8", "1"},
                {"Koji planet je najbliži Suncu?", "Venera", "Mars", "Merkur", "Jupiter", "2"}
        };
        for (String[] d : data) {
            Map<String, Object> q = new HashMap<>();
            q.put("question", d[0]);
            List<String> answers = new ArrayList<>();
            answers.add(d[1]);
            answers.add(d[2]);
            answers.add(d[3]);
            answers.add(d[4]);
            q.put("answers", answers);
            q.put("correct", Integer.parseInt(d[5]));
            questions.add(q);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (questionTimer != null) questionTimer.cancel();
        if (gameStateListener != null) {
            gameStateRef.removeEventListener(gameStateListener);
        }
        if (scoresListener != null && sessionScoresRef != null)
            sessionScoresRef.removeEventListener(scoresListener);
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
                if (tvScores != null)
                    tvScores.setText("Igrač 1: " + (s1 != null ? s1 : 0)
                            + "  |  Igrač 2: " + (s2 != null ? s2 : 0));
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        sessionScoresRef.addValueEventListener(scoresListener);
    }
}