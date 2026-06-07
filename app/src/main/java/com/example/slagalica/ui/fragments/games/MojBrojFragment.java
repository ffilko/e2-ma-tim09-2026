package com.example.slagalica.ui.fragments.games;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import com.example.slagalica.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Random;

public class MojBrojFragment extends Fragment implements SensorEventListener {

    private static final int ROUND_DURATION_MS = 60_000;
    private static final int STOP_PHASE_DURATION_MS = 5_000;
    private static final int EXACT_POINTS = 10;
    private static final int CLOSEST_POINTS = 5;
    private static final float SHAKE_THRESHOLD = 12f;

    private TextView tvTarget, tvExpression, tvRound, tvTimer, tvPhase, tvScores;
    private GridLayout gridNumbers;
    private Button btnStop, btnSubmit;
    private View mainRootView;

    private boolean isMe1;
    private DatabaseReference gameStateRef;
    private ValueEventListener numbersListener;

    private int currentRound = 1;
    private int player1Score = 0;
    private int player2Score = 0;

    private enum Phase { WAITING_TARGET_STOP, WAITING_NUMBER_STOP, PLAYING, WAITING_OPPONENT }
    private Phase phase = Phase.WAITING_TARGET_STOP;

    private int targetNumber = 0;
    private int[] availableNumbers = new int[6];
    private Button[] numberButtons = new Button[6];
    private List<String> tokens = new ArrayList<>();

    private CountDownTimer roundTimer;
    private CountDownTimer stopTimer;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime = 0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        String myRole = getArguments() != null ? getArguments().getString("myRole") : "player1";
        String sessionId = getArguments() != null ? getArguments().getString("sessionId") : null;
        isMe1 = "player1".equals(myRole);

        gameStateRef = FirebaseDatabase.getInstance(
                "https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app"
        ).getReference("sessions").child(sessionId).child("gameState");

        mainRootView = inflater.inflate(R.layout.fragment_moj_broj, container, false);

        tvTarget     = mainRootView.findViewById(R.id.tvTarget);
        tvExpression = mainRootView.findViewById(R.id.tvExpression);
        tvRound      = mainRootView.findViewById(R.id.tvRound);
        tvTimer      = mainRootView.findViewById(R.id.tvTimer);
        tvPhase      = mainRootView.findViewById(R.id.tvPhase);
        tvScores     = mainRootView.findViewById(R.id.tvScores);
        gridNumbers  = mainRootView.findViewById(R.id.gridNumbers);
        btnStop      = mainRootView.findViewById(R.id.btnStop);
        btnSubmit    = mainRootView.findViewById(R.id.btnSubmit);

        setupSensor();
        setupOperatorButtons();
        startRound();

        btnStop.setOnClickListener(v -> handleStop());
        btnSubmit.setOnClickListener(v -> handleSubmit());

        return mainRootView;
    }

    private void setupSensor() {
        sensorManager = (SensorManager) requireContext()
                .getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (accelerometer != null)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimers();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float x = event.values[0], y = event.values[1], z = event.values[2];
        double magnitude = Math.sqrt(x*x + y*y + z*z) - SensorManager.GRAVITY_EARTH;
        if (magnitude > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > 1000) {
                lastShakeTime = now;
                handleStop();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void startRound() {
        tokens.clear();
        tvExpression.setText("");
        tvTarget.setText("...");
        gridNumbers.removeAllViews();

        tvRound.setText("Runda " + currentRound + "/2");
        updateScores();
        btnSubmit.setEnabled(false);

        boolean iAmController = (currentRound == 1 && isMe1) || (currentRound == 2 && !isMe1);

        if (iAmController) {
            phase = Phase.WAITING_TARGET_STOP;
            tvPhase.setText("Pritisni STOP za traženi broj");
            btnStop.setEnabled(true);

            stopTimer = new CountDownTimer(STOP_PHASE_DURATION_MS, 100) {
                @Override public void onTick(long ms) {
                    tvTimer.setText(String.format("%.1f", ms / 1000.0));
                }
                @Override public void onFinish() { handleStop(); }
            }.start();
        } else {
            phase = Phase.WAITING_OPPONENT;
            tvPhase.setText("Čekaj dok protivnik stopira brojeve...");
            btnStop.setEnabled(false);
            listenForNumbersFromFirebase();
        }
    }

    private void handleStop() {
        boolean iAmController = (currentRound == 1 && isMe1) || (currentRound == 2 && !isMe1);
        if (!iAmController) return;

        if (phase == Phase.WAITING_TARGET_STOP) {
            if (stopTimer != null) stopTimer.cancel();
            targetNumber = new Random().nextInt(900) + 100;
            tvTarget.setText(String.valueOf(targetNumber));

            gameStateRef.child("targetNumber").setValue(targetNumber);
            gameStateRef.child("round").setValue(currentRound);
            gameStateRef.child("numbersReady").setValue(false);

            phase = Phase.WAITING_NUMBER_STOP;
            tvPhase.setText("Pritisni STOP za brojeve");

            stopTimer = new CountDownTimer(STOP_PHASE_DURATION_MS, 100) {
                @Override public void onTick(long ms) {
                    tvTimer.setText(String.format("%.1f", ms / 1000.0));
                }
                @Override public void onFinish() { handleStop(); }
            }.start();

        } else if (phase == Phase.WAITING_NUMBER_STOP) {
            if (stopTimer != null) stopTimer.cancel();
            generateNumbers();

            List<Integer> numList = new ArrayList<>();
            for (int n : availableNumbers) numList.add(n);

            gameStateRef.child("availableNumbers").setValue(numList);
            gameStateRef.child("numbersReady").setValue(true);
            gameStateRef.child("result_player1").setValue(null);
            gameStateRef.child("result_player2").setValue(null);

            populateNumberButtons();
            startPlayPhase();
        }
    }

    private void listenForNumbersFromFirebase() {
        numbersListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean ready = snapshot.child("numbersReady").getValue(Boolean.class);
                Integer round = snapshot.child("round").getValue(Integer.class);

                if (Boolean.TRUE.equals(ready) && round != null && round == currentRound) {
                    gameStateRef.removeEventListener(this);

                    Integer target = snapshot.child("targetNumber").getValue(Integer.class);
                    if (target != null) {
                        targetNumber = target;
                        tvTarget.setText(String.valueOf(targetNumber));
                    }

                    List<Integer> nums = new ArrayList<>();
                    for (DataSnapshot n : snapshot.child("availableNumbers").getChildren()) {
                        Integer val = n.getValue(Integer.class);
                        if (val != null) nums.add(val);
                    }
                    for (int i = 0; i < Math.min(nums.size(), 6); i++) {
                        availableNumbers[i] = nums.get(i);
                    }

                    populateNumberButtons();
                    startPlayPhase();
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        gameStateRef.addValueEventListener(numbersListener);
    }

    private void generateNumbers() {
        Random rnd = new Random();
        int[] singles = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] mediums = {10, 15, 20};
        int[] larges  = {25, 50, 75, 100};

        int[] shuffled = singles.clone();
        for (int i = shuffled.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = shuffled[i]; shuffled[i] = shuffled[j]; shuffled[j] = tmp;
        }
        availableNumbers[0] = shuffled[0];
        availableNumbers[1] = shuffled[1];
        availableNumbers[2] = shuffled[2];
        availableNumbers[3] = shuffled[3];
        availableNumbers[4] = mediums[rnd.nextInt(mediums.length)];
        availableNumbers[5] = larges[rnd.nextInt(larges.length)];
    }

    private void populateNumberButtons() {
        gridNumbers.removeAllViews();
        numberButtons = new Button[6];

        for (int i = 0; i < availableNumbers.length; i++) {
            final int num = availableNumbers[i];

            Button btn = new Button(requireContext());
            btn.setText(String.valueOf(num));
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(R.color.slagalica_color)));
            btn.setTextColor(android.graphics.Color.WHITE);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> {
                if (!lastTokenIsOperator()) return;
                tokens.add(String.valueOf(num));
                btn.setEnabled(false);
                btn.setAlpha(0.4f);
                updateExpression();
            });

            numberButtons[i] = btn;
            gridNumbers.addView(btn);
        }
    }

    private void setupOperatorButtons() {
        mainRootView.findViewById(R.id.btnPlus).setOnClickListener(v    -> addOperator("+"));
        mainRootView.findViewById(R.id.btnMinus).setOnClickListener(v   -> addOperator("-"));
        mainRootView.findViewById(R.id.btnMul).setOnClickListener(v     -> addOperator("*"));
        mainRootView.findViewById(R.id.btnDiv).setOnClickListener(v     -> addOperator("/"));
        mainRootView.findViewById(R.id.btnLParen).setOnClickListener(v  -> addOperator("("));
        mainRootView.findViewById(R.id.btnRParen).setOnClickListener(v  -> addOperator(")"));

        mainRootView.findViewById(R.id.btnBackspace).setOnClickListener(v -> {
            if (!tokens.isEmpty()) {
                String removed = tokens.remove(tokens.size() - 1);

                for (int i = 0; i < availableNumbers.length; i++) {
                    if (String.valueOf(availableNumbers[i]).equals(removed)
                            && numberButtons[i] != null
                            && !numberButtons[i].isEnabled()) {
                        numberButtons[i].setEnabled(true);
                        numberButtons[i].setAlpha(1f);
                        break;
                    }
                }
                updateExpression();
            }
        });

        mainRootView.findViewById(R.id.btnClear).setOnClickListener(v -> {
            tokens.clear();
            for (Button btn : numberButtons) {
                if (btn != null) {
                    btn.setEnabled(true);
                    btn.setAlpha(1f);
                }
            }
            updateExpression();
        });
    }

    private void addOperator(String op) {
        if (op.equals("(")) {
            if (lastTokenIsNumber()) return;
        } else if (op.equals(")")) {
            if (!lastTokenIsNumber()) return;
        } else {
            if (!lastTokenIsNumber()) {
                String last = tokens.isEmpty() ? "" : tokens.get(tokens.size() - 1);
                if (!last.equals(")")) return;
            }
        }
        tokens.add(op);
        updateExpression();
    }

    private void updateExpression() {
        StringBuilder sb = new StringBuilder();
        for (String t : tokens) {
            sb.append(t);
        }
        tvExpression.setText(sb.toString());
    }

    private double evaluate(String expr) {
        try {
            return new ExpressionEvaluator(expr).parse();
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    private static class ExpressionEvaluator {
        private final String expr;
        private int pos = 0;

        ExpressionEvaluator(String expr) {
            this.expr = expr.replaceAll("\\s+", "");
        }

        double parse() {
            double v = parseExpr();
            if (pos != expr.length()) throw new RuntimeException("Unexpected char");
            return v;
        }

        private double parseExpr() {
            double v = parseTerm();
            while (pos < expr.length()) {
                char c = expr.charAt(pos);
                if      (c == '+') { pos++; v += parseTerm(); }
                else if (c == '-') { pos++; v -= parseTerm(); }
                else break;
            }
            return v;
        }

        private double parseTerm() {
            double v = parseFactor();
            while (pos < expr.length()) {
                char c = expr.charAt(pos);
                if (c == '*') { pos++; v *= parseFactor(); }
                else if (c == '/') {
                    pos++;
                    double d = parseFactor();
                    if (d == 0) throw new ArithmeticException("Division by zero");
                    v /= d;
                }
                else break;
            }
            return v;
        }

        private double parseFactor() {
            if (pos < expr.length() && expr.charAt(pos) == '(') {
                pos++;
                double v = parseExpr();
                if (pos < expr.length() && expr.charAt(pos) == ')') pos++;
                return v;
            }
            int start = pos;
            if (pos < expr.length() && (expr.charAt(pos) == '-' || expr.charAt(pos) == '+')) pos++;
            while (pos < expr.length() && (Character.isDigit(expr.charAt(pos)) || expr.charAt(pos) == '.')) pos++;
            if (start == pos) throw new RuntimeException("Expected number at pos " + pos);
            return Double.parseDouble(expr.substring(start, pos));
        }
    }

    private void listenForNumbers() {
        if (gameStateRef == null) return;

        gameStateRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean ready = snapshot.child("numbersReady").getValue(Boolean.class);
                Integer round = snapshot.child("round").getValue(Integer.class);

                if (Boolean.TRUE.equals(ready) && round != null && round == currentRound) {
                    Integer target = snapshot.child("targetNumber").getValue(Integer.class);
                    if (target != null) targetNumber = target;
                    tvTarget.setText(String.valueOf(targetNumber));

                    List<Integer> nums = new ArrayList<>();
                    for (DataSnapshot n : snapshot.child("availableNumbers").getChildren()) {
                        nums.add(n.getValue(Integer.class));
                    }
                    for (int i = 0; i < Math.min(nums.size(), availableNumbers.length); i++) {
                        availableNumbers[i] = nums.get(i);
                    }

                    gameStateRef.removeEventListener(this);
                    populateNumberButtons();
                    startPlayPhase();
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    private void startPlayPhase() {
        phase = Phase.PLAYING;
        btnStop.setEnabled(false);
        btnSubmit.setEnabled(true);
        tvPhase.setText("Nađi broj: " + targetNumber);

        roundTimer = new CountDownTimer(ROUND_DURATION_MS, 100) {
            @Override public void onTick(long ms) {
                tvTimer.setText(String.format("%.1f", ms / 1000.0));
            }
            @Override public void onFinish() {
                tvTimer.setText("0.0");
                handleSubmit();
            }
        }.start();
    }

    private void handleSubmit() {
        if (phase != Phase.PLAYING) return;
        cancelTimers();
        phase = Phase.WAITING_OPPONENT;
        btnSubmit.setEnabled(false);
        tvPhase.setText("Čekaj protivnika...");

        String expr = tvExpression.getText().toString();
        double result = evaluate(expr);
        int myResult = Double.isNaN(result) ? -1 : (int) result;

        String myKey = isMe1 ? "result_player1" : "result_player2";
        gameStateRef.child(myKey).setValue(myResult);

        waitForBothResults();
    }


    private void waitForBothResults() {
        gameStateRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                boolean r1exists = snapshot.hasChild("result_player1")
                        && snapshot.child("result_player1").getValue() != null;
                boolean r2exists = snapshot.hasChild("result_player2")
                        && snapshot.child("result_player2").getValue() != null;

                if (r1exists && r2exists) {
                    gameStateRef.removeEventListener(this);

                    int r1 = snapshot.child("result_player1").getValue(Integer.class);
                    int r2 = snapshot.child("result_player2").getValue(Integer.class);

                    awardPoints(r1, r2);

                    if (currentRound == 1) {
                        currentRound = 2;
                        gameStateRef.child("numbersReady").setValue(false);
                        mainRootView.postDelayed(() -> startRound(), 1500);
                    } else {
                        mainRootView.postDelayed(() -> endGame(), 1500);
                    }
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    private void awardPoints(int r1, int r2) {
        if (r1 == targetNumber && r2 != targetNumber) {
            player1Score += EXACT_POINTS;
        } else if (r2 == targetNumber && r1 != targetNumber) {
            player2Score += EXACT_POINTS;
        } else if (r1 == targetNumber && r2 == targetNumber) {
            player1Score += EXACT_POINTS;
            player2Score += EXACT_POINTS;
        } else {
            int d1 = r1 == -1 ? Integer.MAX_VALUE : Math.abs(r1 - targetNumber);
            int d2 = r2 == -1 ? Integer.MAX_VALUE : Math.abs(r2 - targetNumber);
            if (d1 < d2 && r1 != -1) player1Score += CLOSEST_POINTS;
            else if (d2 < d1 && r2 != -1) player2Score += CLOSEST_POINTS;
        }
        updateScores();
    }

    private void endGame() {
        cancelTimers();
        int myFinalScore = isMe1 ? player1Score : player2Score;
        Bundle result = new Bundle();
        result.putBoolean("finished", true);
        result.putInt("myScore", myFinalScore);
        getParentFragmentManager().setFragmentResult("game_finished", result);
    }

    private void awardPointsRound2(int r1, int r2) {
        if (r1 == targetNumber && r2 != targetNumber) {
            player1Score += EXACT_POINTS;
        } else if (r2 == targetNumber && r1 != targetNumber) {
            player2Score += EXACT_POINTS;
        } else if (r1 == targetNumber && r2 == targetNumber) {
            player1Score += EXACT_POINTS;
            player2Score += EXACT_POINTS;
        } else if (r1 != -1 || r2 != -1) {
            int d1 = r1 == -1 ? Integer.MAX_VALUE : Math.abs(r1 - targetNumber);
            int d2 = r2 == -1 ? Integer.MAX_VALUE : Math.abs(r2 - targetNumber);
            if (d1 < d2) player1Score += CLOSEST_POINTS;
            else if (d2 < d1) player2Score += CLOSEST_POINTS;
        }
        updateScores();
    }

    private void updateScores() {
        tvScores.setText("Igrač 1: " + player1Score + "  |  Igrač 2: " + player2Score);
    }

    private void cancelTimers() {
        if (roundTimer != null) { roundTimer.cancel(); roundTimer = null; }
        if (stopTimer != null)  { stopTimer.cancel();  stopTimer = null; }
    }

    private boolean lastTokenIsNumber() {
        if (tokens.isEmpty()) return false;
        String last = tokens.get(tokens.size() - 1);
        try {
            Integer.parseInt(last);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean lastTokenIsOperator() {
        if (tokens.isEmpty()) return true;
        String last = tokens.get(tokens.size() - 1);
        return last.equals("+") || last.equals("-") || last.equals("*")
                || last.equals("/") || last.equals("(");
    }
}