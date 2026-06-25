package com.example.slagalica.ui.fragments.games;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.example.slagalica.data.SessionManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class SkockoFragment extends Fragment {

    private static final long MAIN_DURATION_MS = 30_000;
    private static final long BONUS_DURATION_MS = 10_000;
    private static final int MAX_ATTEMPTS = 6;

    private static final int[] SYMBOLS = {
            R.drawable.skocko, R.drawable.square, R.drawable.circle,
            R.drawable.heart, R.drawable.triangle, R.drawable.star
    };

    private SessionManager sessionManager;
    private DatabaseReference stateRef;
    private ValueEventListener stateListener;
    private String myRole;
    private TextView tvRound, tvTimer, tvPhase, tvScore1, tvScore2, tvP1Name, tvP2Name;
    private LinearLayout llBoard;
    private final ImageView[] ivSlots = new ImageView[4];
    private Button btnDelete, btnSubmit;
    private final ImageButton[] btnSyms = new ImageButton[6];
    private int round = 1;
    private String phase = "main";
    private String activePlayer = "player1";
    private final int[] secret = new int[4];
    private final List<int[]> attempts = new ArrayList<>();
    private final List<int[]> feedback = new ArrayList<>();
    private int attemptCount = 0;
    private boolean finished = false;
    private int scoreP1 = 0, scoreP2 = 0;
    private long phaseStart = 0;
    private final List<Integer> currentGuess = new ArrayList<>();
    private String inputContext = "";

    private CountDownTimer timer;
    private long timerBase = -1;
    private boolean resultSent = false;
    private DatabaseReference sessionScoresRef;
    private ValueEventListener scoresListener;
    private int cumulativeP1 = 0, cumulativeP2 = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_skocko, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String sessionId = requireArguments().getString("sessionId");
        myRole = requireArguments().getString("myRole");

        sessionManager = new SessionManager();
        sessionManager.initSession(sessionId, myRole);
        stateRef = sessionManager.getGameStateRef();
        listenToSessionScores(sessionId);

        bindViews(view);
        setupListeners();
        loadPlayerNames();
        listenToState();
    }

    private void bindViews(View v) {
        tvRound = v.findViewById(R.id.tvRound);
        tvTimer = v.findViewById(R.id.tvTimer);
        tvPhase = v.findViewById(R.id.tvPhase);
        tvScore1 = v.findViewById(R.id.tvScore1);
        tvScore2 = v.findViewById(R.id.tvScore2);
        tvP1Name = v.findViewById(R.id.tvPlayer1Name);
        tvP2Name = v.findViewById(R.id.tvPlayer2Name);
        llBoard = v.findViewById(R.id.llBoard);
        btnDelete = v.findViewById(R.id.btnDeleteSkocko);
        btnSubmit = v.findViewById(R.id.btnSubmitSkocko);

        int[] slotIds = {R.id.ivSlot0, R.id.ivSlot1, R.id.ivSlot2, R.id.ivSlot3};
        int[] symIds = {R.id.btnSym0, R.id.btnSym1, R.id.btnSym2,
                R.id.btnSym3, R.id.btnSym4, R.id.btnSym5};
        for (int i = 0; i < 4; i++) ivSlots[i] = v.findViewById(slotIds[i]);
        for (int s = 0; s < 6; s++) btnSyms[s] = v.findViewById(symIds[s]);
    }

    private void setupListeners() {
        for (int s = 0; s < 6; s++) {
            final int sym = s;
            btnSyms[s].setOnClickListener(v -> {
                if (isActive() && !finished && currentGuess.size() < 4) {
                    currentGuess.add(sym);
                    renderSlots();
                }
            });
        }
        btnDelete.setOnClickListener(v -> {
            if (!currentGuess.isEmpty()) {
                currentGuess.remove(currentGuess.size() - 1);
                renderSlots();
            }
        });
        btnSubmit.setOnClickListener(v -> submitGuess());
    }

    private void loadPlayerNames() {
        stateRef.getParent().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                String n1 = s.child("player1Name").getValue(String.class);
                String n2 = s.child("player2Name").getValue(String.class);
                if (tvP1Name != null) tvP1Name.setText(n1 != null ? n1 : "Igrač 1");
                if (tvP2Name != null) tvP2Name.setText(n2 != null ? n2 : "Igrač 2");
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void listenToState() {
        stateListener = stateRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) {
                    if (resultSent) return;
                    if ("player1".equals(myRole)) initState();
                    return;
                }
                readState(snap);
                String ctx = round + "/" + phase + "/" + activePlayer;
                if (!ctx.equals(inputContext)) {
                    inputContext = ctx;
                    currentGuess.clear();
                }

                if (phaseStart != timerBase) startLocalTimer();

                render();

                if (finished) onGameFinished();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void initState() {
        Map<String, Object> scores = new HashMap<>();
        scores.put("player1", 0);
        scores.put("player2", 0);

        Map<String, Object> state = new HashMap<>();
        state.put("round", 1);
        state.put("phase", "main");
        state.put("activePlayer", "player1");
        state.put("secret", randomSecret());
        state.put("attemptCount", 0);
        state.put("finished", false);
        state.put("scores", scores);
        state.put("phaseStart", System.currentTimeMillis());
        stateRef.setValue(state);
    }

    private List<Integer> randomSecret() {
        Random rnd = new Random();
        List<Integer> s = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) s.add(rnd.nextInt(6));
        return s;
    }

    private void readState(DataSnapshot snap) {
        Integer r = snap.child("round").getValue(Integer.class);
        round = r != null ? r : 1;

        String p = snap.child("phase").getValue(String.class);
        phase = p != null ? p : "main";

        String a = snap.child("activePlayer").getValue(String.class);
        activePlayer = a != null ? a : "player1";

        finished = Boolean.TRUE.equals(snap.child("finished").getValue(Boolean.class));

        Long ps = snap.child("phaseStart").getValue(Long.class);
        phaseStart = ps != null ? ps : 0;

        Integer ac = snap.child("attemptCount").getValue(Integer.class);
        attemptCount = ac != null ? ac : 0;

        Integer s1 = snap.child("scores/player1").getValue(Integer.class);
        Integer s2 = snap.child("scores/player2").getValue(Integer.class);
        scoreP1 = s1 != null ? s1 : 0;
        scoreP2 = s2 != null ? s2 : 0;

        for (int i = 0; i < 4; i++) {
            Integer v = snap.child("secret").child(String.valueOf(i)).getValue(Integer.class);
            secret[i] = v != null ? v : 0;
        }

        attempts.clear();
        feedback.clear();
        DataSnapshot att = snap.child("attempts");
        for (int i = 0; i <= MAX_ATTEMPTS; i++) {
            DataSnapshot one = att.child(String.valueOf(i));
            if (!one.exists()) break;
            int[] combo = new int[4];
            for (int j = 0; j < 4; j++) {
                Integer v = one.child("combo").child(String.valueOf(j)).getValue(Integer.class);
                combo[j] = v != null ? v : 0;
            }
            Integer red = one.child("red").getValue(Integer.class);
            Integer yellow = one.child("yellow").getValue(Integer.class);
            attempts.add(combo);
            feedback.add(new int[]{red != null ? red : 0, yellow != null ? yellow : 0});
        }
    }

    private boolean isActive() {
        return myRole != null && myRole.equals(activePlayer);
    }

    private String roundOwner() {
        return round == 1 ? "player1" : "player2";
    }

    private String opponentOf(String role) {
        return "player1".equals(role) ? "player2" : "player1";
    }

    private int myScore() {
        return "player1".equals(myRole) ? scoreP1 : scoreP2;
    }

    private int pointsForAttempt(int attemptNumber) {
        if (attemptNumber <= 2) return 20;
        if (attemptNumber <= 4) return 15;
        return 10;
    }
    private int[] computeFeedback(int[] guess) {
        int red = 0;
        int[] sCount = new int[6], gCount = new int[6];
        for (int i = 0; i < 4; i++) {
            if (guess[i] == secret[i]) red++;
            sCount[secret[i]]++;
            gCount[guess[i]]++;
        }
        int total = 0;
        for (int s = 0; s < 6; s++) total += Math.min(sCount[s], gCount[s]);
        return new int[]{red, total - red};
    }

    private void submitGuess() {
        if (finished || !isActive()) return;
        if (currentGuess.size() < 4) {
            Toast.makeText(getContext(), "Izaberi sva 4 znaka", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("main".equals(phase) && attemptCount >= MAX_ATTEMPTS) return;

        int[] guess = new int[4];
        for (int i = 0; i < 4; i++) guess[i] = currentGuess.get(i);
        int[] fb = computeFeedback(guess);
        boolean correct = fb[0] == 4;
        int idx = attemptCount;

        Map<String, Object> u = new HashMap<>();
        u.put("attempts/" + idx + "/combo", currentGuess);
        u.put("attempts/" + idx + "/red", fb[0]);
        u.put("attempts/" + idx + "/yellow", fb[1]);
        u.put("attemptCount", idx + 1);

        if (correct) {
            int pts = "bonus".equals(phase) ? 10 : pointsForAttempt(idx + 1);
            u.put("scores/" + myRole, myScore() + pts);
            stateRef.updateChildren(u);
            Toast.makeText(getContext(), "Pogodak! +" + pts + " bodova",
                    Toast.LENGTH_SHORT).show();
            endRound();
        } else if ("bonus".equals(phase)) {
            stateRef.updateChildren(u);
            endRound();
        } else if (idx + 1 >= MAX_ATTEMPTS) {
            stateRef.updateChildren(u);
            startBonus();
        } else {
            stateRef.updateChildren(u);
        }

        currentGuess.clear();
        renderSlots();
    }

    private void startBonus() {
        Map<String, Object> u = new HashMap<>();
        u.put("phase", "bonus");
        u.put("activePlayer", opponentOf(roundOwner()));
        u.put("phaseStart", System.currentTimeMillis());
        stateRef.updateChildren(u);
    }

    private void endRound() {
        if (finished) return;
        if (round == 1) {
            Map<String, Object> u = new HashMap<>();
            u.put("round", 2);
            u.put("phase", "main");
            u.put("activePlayer", "player2");
            u.put("secret", randomSecret());
            u.put("attempts", null);
            u.put("attemptCount", 0);
            u.put("phaseStart", System.currentTimeMillis());
            stateRef.updateChildren(u);
        } else {
            stateRef.child("finished").setValue(true);
        }
    }

    private void onGameFinished() {
        if (resultSent) return;
        resultSent = true;
        if (timer != null) timer.cancel();
        if (stateListener != null) {
            stateRef.removeEventListener(stateListener);
            stateListener = null;
        }

        Bundle result = new Bundle();
        result.putInt("myScore", myScore());
        getParentFragmentManager().setFragmentResult("game_finished", result);
    }

    private void startLocalTimer() {
        timerBase = phaseStart;
        if (timer != null) timer.cancel();
        if (phaseStart == 0 || finished) return;

        final int roundAtStart = round;
        final String phaseAtStart = phase;
        long duration = "bonus".equals(phase) ? BONUS_DURATION_MS : MAIN_DURATION_MS;
        long remaining = duration - (System.currentTimeMillis() - phaseStart);
        if (remaining <= 0) {
            handleTimeout(roundAtStart, phaseAtStart);
            return;
        }
        timer = new CountDownTimer(remaining, 100) {
            @Override
            public void onTick(long ms) {
                tvTimer.setText(String.format(Locale.US, "%.1f", ms / 1000.0));
            }
            @Override
            public void onFinish() {
                tvTimer.setText("0.0");
                handleTimeout(roundAtStart, phaseAtStart);
            }
        }.start();
    }

    private void handleTimeout(int roundAtStart, String phaseAtStart) {
        if (!"player1".equals(myRole) || finished) return;
        if (round != roundAtStart || !phase.equals(phaseAtStart)) return;
        if ("main".equals(phase)) {
            startBonus();
        } else {
            endRound();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void render() {
        tvRound.setText("Runda " + round + "/2");

        boolean bonus = "bonus".equals(phase);
        if (finished) {
            tvPhase.setText("Kraj igre");
        } else if (isActive()) {
            tvPhase.setText(bonus
                    ? "Bonus: ti pogađaš (1 pokušaj)"
                    : "Ti pogađaš (pokušaj " + Math.min(attemptCount + 1, MAX_ATTEMPTS) + "/6)");
        } else {
            tvPhase.setText(bonus ? "Bonus: protivnik pogađa" : "Protivnik pogađa");
        }

        boolean inputEnabled = isActive() && !finished;
        for (ImageButton b : btnSyms) b.setEnabled(inputEnabled);
        btnDelete.setEnabled(inputEnabled);
        btnSubmit.setEnabled(inputEnabled);

        renderSlots();
        renderBoard();
    }

    private void renderSlots() {
        for (int i = 0; i < 4; i++) {
            if (i < currentGuess.size()) {
                ivSlots[i].setImageResource(SYMBOLS[currentGuess.get(i)]);
            } else {
                ivSlots[i].setImageDrawable(null);
            }
        }
    }

    private void renderBoard() {
        llBoard.removeAllViews();
        for (int a = 0; a < attempts.size(); a++) {
            llBoard.addView(buildAttemptRow(attempts.get(a), feedback.get(a)));
        }
    }

    private View buildAttemptRow(int[] combo, int[] fb) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(6);
        row.setLayoutParams(rp);

        for (int i = 0; i < 4; i++) {
            ImageView iv = new ImageView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.setMargins(dp(2), 0, dp(2), 0);
            iv.setLayoutParams(lp);
            iv.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white));
            iv.setPadding(dp(4), dp(4), dp(4), dp(4));
            iv.setImageResource(SYMBOLS[combo[i]]);
            row.addView(iv);
        }

        View spacer = new View(requireContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(20), 1));
        row.addView(spacer);

        for (int d = 0; d < 4; d++) {
            ImageView dot = new ImageView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(15), dp(15));
            lp.setMargins(dp(1), 0, dp(1), 0);
            dot.setLayoutParams(lp);
            if (d < fb[0]) {
                dot.setImageResource(R.drawable.red_dot);
            } else if (d < fb[0] + fb[1]) {
                dot.setImageResource(R.drawable.yellow_dot);
            } else {
                dot.setImageResource(R.drawable.white_dot);
            }
            row.addView(dot);
        }
        return row;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
        if (stateListener != null) stateRef.removeEventListener(stateListener);
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
                cumulativeP1 = s1 != null ? s1 : 0;
                cumulativeP2 = s2 != null ? s2 : 0;
                updateScoreDisplay();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        sessionScoresRef.addValueEventListener(scoresListener);
    }

    private void updateScoreDisplay() {
        if (tvScore1 != null) tvScore1.setText(String.valueOf(cumulativeP1));
        if (tvScore2 != null) tvScore2.setText(String.valueOf(cumulativeP2));
    }
}