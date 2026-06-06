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

    private int currentRound = 1;
    private boolean isPlayer1Turn = true;
    private int player1Score = 0;
    private int player2Score = 0;
    private int player1Result = Integer.MIN_VALUE; // čuvamo za poređenje

    private enum Phase { WAITING_TARGET_STOP, WAITING_NUMBER_STOP, PLAYING }
    private Phase phase = Phase.WAITING_TARGET_STOP;

    private int targetNumber = 0;
    private int[] availableNumbers = new int[6];
    private Button[] numberButtons = new Button[6]; // referenca na svako dugme

    // Lista tokena umesto stringa — svaki token je broj ili operator
    private List<String> tokens = new ArrayList<>();

    private CountDownTimer roundTimer;
    private CountDownTimer stopTimer;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

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

    // ─── Sensor ─────────────────────────────────────────────────────────────

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

    // ─── Runda ──────────────────────────────────────────────────────────────

    private void startRound() {
        tokens.clear();
        tvExpression.setText("");
        tvTarget.setText("...");
        gridNumbers.removeAllViews();
        phase = Phase.WAITING_TARGET_STOP;

        tvRound.setText("Runda " + currentRound + "/2");
        tvPhase.setText((isPlayer1Turn ? "Igrač 1" : "Igrač 2") + " — Pritisni STOP za traženi broj");
        updateScores();
        btnStop.setEnabled(true);
        btnSubmit.setEnabled(false);

        stopTimer = new CountDownTimer(STOP_PHASE_DURATION_MS, 100) {
            @Override public void onTick(long ms) { tvTimer.setText(String.format("%.1f", ms / 1000.0)); }
            @Override public void onFinish() { handleStop(); }
        }.start();
    }

    private void handleStop() {
        if (phase == Phase.WAITING_TARGET_STOP) {
            stopTimer.cancel();
            targetNumber = new Random().nextInt(900) + 100;
            tvTarget.setText(String.valueOf(targetNumber));
            phase = Phase.WAITING_NUMBER_STOP;
            tvPhase.setText("Pritisni STOP za brojeve");

            stopTimer = new CountDownTimer(STOP_PHASE_DURATION_MS, 100) {
                @Override public void onTick(long ms) { tvTimer.setText(String.format("%.1f", ms / 1000.0)); }
                @Override public void onFinish() { handleStop(); }
            }.start();

        } else if (phase == Phase.WAITING_NUMBER_STOP) {
            stopTimer.cancel();
            generateNumbers();
            populateNumberButtons();
            phase = Phase.PLAYING;
            tvPhase.setText((isPlayer1Turn ? "Igrač 1" : "Igrač 2") + " — Kreiranje izraza");
            btnStop.setEnabled(false);
            btnSubmit.setEnabled(true);

            roundTimer = new CountDownTimer(ROUND_DURATION_MS, 100) {
                @Override public void onTick(long ms) { tvTimer.setText(String.format("%.1f", ms / 1000.0)); }
                @Override public void onFinish() {
                    tvTimer.setText("0.0");
                    handleSubmit();
                }
            }.start();
        }
    }

    // ─── Generisanje brojeva ─────────────────────────────────────────────────

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
            final int index = i;

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

    // ─── Operatori ───────────────────────────────────────────────────────────

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
                // Ako je bio broj, vrati dugme
                for (int i = 0; i < availableNumbers.length; i++) {
                    if (String.valueOf(availableNumbers[i]).equals(removed)
                            && numberButtons[i] != null
                            && !numberButtons[i].isEnabled()) {
                        numberButtons[i].setEnabled(true);
                        numberButtons[i].setAlpha(1f);
                        break; // Vrati samo jedno dugme
                    }
                }
                updateExpression();
            }
        });

        mainRootView.findViewById(R.id.btnClear).setOnClickListener(v -> {
            tokens.clear();
            // Vrati sva dugmad
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
        // Spoji tokene sa razmakom radi čitljivosti
        StringBuilder sb = new StringBuilder();
        for (String t : tokens) {
            sb.append(t);
        }
        tvExpression.setText(sb.toString());
    }

    // ─── Evaluacija ──────────────────────────────────────────────────────────

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

    // ─── Predaja ─────────────────────────────────────────────────────────────

    private void handleSubmit() {
        if (phase != Phase.PLAYING) return;
        cancelTimers();
        phase = Phase.WAITING_TARGET_STOP;

        String expr = tvExpression.getText().toString();
        double result = evaluate(expr);
        int playerResult = Double.isNaN(result) ? -1 : (int) result;

        if (currentRound == 1) {
            player1Result = playerResult;
            awardPoints(true, playerResult, Integer.MIN_VALUE);
            isPlayer1Turn = !isPlayer1Turn;
            currentRound = 2;
            mainRootView.postDelayed(this::startRound, 1500);
        } else {
            int p2Result = playerResult;
            // Poređenje oba rezultata za "bliže" pravilo
            awardPoints(false, player1Result, p2Result);
            mainRootView.postDelayed(this::endGame, 1500);
        }
    }

    private void awardPoints(boolean isRound1, int p1Result, int p2Result) {
        if (isRound1) {
            // Samo runda 1 — igrač 1 igra
            if (p1Result == targetNumber) {
                player1Score += EXACT_POINTS;
            }
        } else {
            // Runda 2 završena — proceni oba
            if (p1Result == targetNumber && p2Result != targetNumber) {
                player1Score += EXACT_POINTS;
            } else if (p2Result == targetNumber && p1Result != targetNumber) {
                player2Score += EXACT_POINTS;
            } else if (p1Result == targetNumber && p2Result == targetNumber) {
                // Oba tačna — oba dobijaju
                player1Score += EXACT_POINTS;
                player2Score += EXACT_POINTS;
            } else if (p1Result != -1 || p2Result != -1) {
                // Niko nije tačan — bliži dobija 5
                int d1 = p1Result == -1 ? Integer.MAX_VALUE : Math.abs(p1Result - targetNumber);
                int d2 = p2Result == -1 ? Integer.MAX_VALUE : Math.abs(p2Result - targetNumber);
                if (d1 < d2) player1Score += CLOSEST_POINTS;
                else if (d2 < d1) player2Score += CLOSEST_POINTS;
                // Ako jednako — niko ne dobija (ili možeš dodati logiku po spec)
            }
        }
        updateScores();
    }

    private void endGame() {
        cancelTimers();
        Bundle bundle = new Bundle();
        bundle.putBoolean("finished", true);
        bundle.putInt("player1Score", player1Score);
        bundle.putInt("player2Score", player2Score);
        getParentFragmentManager().setFragmentResult("game_finished", bundle);
    }

    private void updateScores() {
        tvScores.setText("Igrač 1: " + player1Score + "  |  Igrač 2: " + player2Score);
    }

    private void cancelTimers() {
        if (roundTimer != null) { roundTimer.cancel(); roundTimer = null; }
        if (stopTimer != null)  { stopTimer.cancel();  stopTimer = null; }
    }

    // Pomoćna metoda — šta je poslednji token
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
        if (tokens.isEmpty()) return true; // početak = može ići broj
        String last = tokens.get(tokens.size() - 1);
        return last.equals("+") || last.equals("-") || last.equals("*")
                || last.equals("/") || last.equals("(");
    }
}