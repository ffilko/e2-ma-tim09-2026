package com.example.slagalica.ui.fragments.games;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AsocijacijeFragment extends Fragment {

    private static final long ROUND_DURATION_MS = 2 * 60 * 1000;
    private static final String[] COLS = {"A", "B", "C", "D"};

    private static final int COLOR_OPENED = Color.parseColor("#607D8B");
    private static final int COLOR_SOLVED = Color.parseColor("#4CAF50");
    private int colorHidden;

    private SessionManager sessionManager;
    private DatabaseReference stateRef;
    private ValueEventListener stateListener;
    private String myRole;
    private TextView tvRound, tvTimer, tvPhase, tvScore1, tvScore2, tvP1Name, tvP2Name;
    private final Button[][] btnFields = new Button[4][4];
    private final EditText[] etCols = new EditText[4];
    private EditText etFinal;
    private Button btnSubmitFinal, btnPass;
    private final String[][] fields = new String[4][4];
    private final String[] colSolutions = new String[4];
    private String finalSolution = "";
    private int loadedRound = -1;
    private int round = 1;
    private String turn = "player1";
    private boolean canGuess = false;
    private final boolean[][] opened = new boolean[4][4];
    private final boolean[] solved = new boolean[4];
    private boolean finished = false;
    private int scoreP1 = 0, scoreP2 = 0;
    private long roundStart = 0;

    private CountDownTimer timer;
    private long timerBase = -1;
    private boolean resultSent = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_asocijacije, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String sessionId = requireArguments().getString("sessionId");
        myRole = requireArguments().getString("myRole");

        sessionManager = new SessionManager();
        sessionManager.initSession(sessionId, myRole);
        stateRef = sessionManager.getGameStateRef();

        colorHidden = ContextCompat.getColor(requireContext(), R.color.slagalica_color);

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
        etFinal = v.findViewById(R.id.etFinalSolution);
        btnSubmitFinal = v.findViewById(R.id.btnSubmitAsocijacije);
        btnPass = v.findViewById(R.id.btnPassAsocijacije);

        int[][] btnIds = {
                {R.id.btnA1, R.id.btnA2, R.id.btnA3, R.id.btnA4},
                {R.id.btnB1, R.id.btnB2, R.id.btnB3, R.id.btnB4},
                {R.id.btnC1, R.id.btnC2, R.id.btnC3, R.id.btnC4},
                {R.id.btnD1, R.id.btnD2, R.id.btnD3, R.id.btnD4}
        };
        int[] etIds = {R.id.etColA, R.id.etColB, R.id.etColC, R.id.etColD};

        for (int c = 0; c < 4; c++) {
            etCols[c] = v.findViewById(etIds[c]);
            for (int i = 0; i < 4; i++) {
                btnFields[c][i] = v.findViewById(btnIds[c][i]);
            }
        }
    }

    private void setupListeners() {
        for (int c = 0; c < 4; c++) {
            final int col = c;
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                btnFields[c][i].setOnClickListener(v -> openField(col, idx));
            }
            etCols[c].setImeOptions(EditorInfo.IME_ACTION_DONE);
            etCols[c].setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    guessColumn(col);
                    return true;
                }
                return false;
            });
        }
        btnSubmitFinal.setOnClickListener(v -> guessFinal());
        btnPass.setOnClickListener(v -> {
            if (mayGuessNow()) passTurn();
        });
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

                if (round != loadedRound) loadRoundData(round);
                if (roundStart != timerBase) startLocalTimer();

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
        state.put("turn", "player1");
        state.put("canGuess", false);
        state.put("finished", false);
        state.put("scores", scores);
        state.put("roundStart", System.currentTimeMillis());
        stateRef.setValue(state);
    }

    private void readState(DataSnapshot snap) {
        Integer r = snap.child("round").getValue(Integer.class);
        round = r != null ? r : 1;

        String t = snap.child("turn").getValue(String.class);
        turn = t != null ? t : "player1";

        canGuess = Boolean.TRUE.equals(snap.child("canGuess").getValue(Boolean.class));
        finished = Boolean.TRUE.equals(snap.child("finished").getValue(Boolean.class));

        Long rs = snap.child("roundStart").getValue(Long.class);
        roundStart = rs != null ? rs : 0;

        Integer s1 = snap.child("scores/player1").getValue(Integer.class);
        Integer s2 = snap.child("scores/player2").getValue(Integer.class);
        scoreP1 = s1 != null ? s1 : 0;
        scoreP2 = s2 != null ? s2 : 0;

        for (int c = 0; c < 4; c++) {
            solved[c] = Boolean.TRUE.equals(
                    snap.child("solved").child(COLS[c]).getValue(Boolean.class));
            for (int i = 0; i < 4; i++) {
                opened[c][i] = Boolean.TRUE.equals(
                        snap.child("opened").child(COLS[c] + i).getValue(Boolean.class));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadRoundData(int r) {
        loadedRound = r;
        FirebaseFirestore.getInstance()
                .collection("games").document("asocijacije")
                .collection("rounds").document("round_" + r)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        android.util.Log.e("ASOC", "Nema dokumenta round_" + r
                                + " u games/asocijacije/rounds");
                        return;
                    }
                    String[] keys = {"a", "b", "c", "d"};
                    for (int c = 0; c < 4; c++) {
                        colSolutions[c] = doc.getString(keys[c] + "_solution");
                        List<String> f = (List<String>) doc.get(keys[c] + "_fields");
                        for (int i = 0; i < 4; i++) {
                            fields[c][i] = (f != null && i < f.size()) ? f.get(i) : "?";
                        }
                    }
                    String fin = doc.getString("final");
                    finalSolution = fin != null ? fin : "";
                    clearInputs();
                    render();
                })
                .addOnFailureListener(e ->
                        android.util.Log.e("ASOC", "Greska pri citanju asocijacije", e));
    }
    private boolean isMyTurn() {
        return myRole != null && myRole.equals(turn);
    }
    private boolean allFieldsOpened() {
        for (int c = 0; c < 4; c++) {
            if (solved[c]) continue;
            for (int i = 0; i < 4; i++) {
                if (!opened[c][i]) return false;
            }
        }
        return true;
    }

    private boolean mayGuessNow() {
        return isMyTurn() && !finished && (canGuess || allFieldsOpened());
    }

    private int unopenedCount(int c) {
        int n = 0;
        for (int i = 0; i < 4; i++) if (!opened[c][i]) n++;
        return n;
    }

    private int myScore() {
        return "player1".equals(myRole) ? scoreP1 : scoreP2;
    }

    private void openField(int c, int i) {
        if (finished || !isMyTurn() || canGuess || solved[c] || opened[c][i]) return;
        Map<String, Object> u = new HashMap<>();
        u.put("opened/" + COLS[c] + i, true);
        u.put("canGuess", true);
        stateRef.updateChildren(u);
    }

    private void guessColumn(int c) {
        if (solved[c] || !mayGuessNow()) return;
        String guess = etCols[c].getText().toString().trim();
        if (guess.isEmpty()) return;

        if (colSolutions[c] != null && guess.equalsIgnoreCase(colSolutions[c].trim())) {
            int pts = 2 + unopenedCount(c);
            Map<String, Object> u = new HashMap<>();
            u.put("solved/" + COLS[c], true);
            u.put("scores/" + myRole, myScore() + pts);
            stateRef.updateChildren(u);
            Toast.makeText(getContext(), "Tačno! +" + pts + " bodova", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "Netačno!", Toast.LENGTH_SHORT).show();
            etCols[c].setText("");
            passTurn();
        }
    }

    private void guessFinal() {
        if (!mayGuessNow()) return;
        String guess = etFinal.getText().toString().trim();
        if (guess.isEmpty()) return;

        if (guess.equalsIgnoreCase(finalSolution.trim())) {
            int pts = 7;
            Map<String, Object> u = new HashMap<>();
            for (int c = 0; c < 4; c++) {
                if (!solved[c]) {
                    pts += 2 + unopenedCount(c);
                    u.put("solved/" + COLS[c], true);
                }
            }
            u.put("scores/" + myRole, myScore() + pts);
            stateRef.updateChildren(u);
            Toast.makeText(getContext(), "Konačno rešenje! +" + pts + " bodova",
                    Toast.LENGTH_SHORT).show();
            endRound();
        } else {
            Toast.makeText(getContext(), "Netačno!", Toast.LENGTH_SHORT).show();
            etFinal.setText("");
            passTurn();
        }
    }

    private void passTurn() {
        Map<String, Object> u = new HashMap<>();
        u.put("turn", sessionManager.getOpponentId());
        u.put("canGuess", false);
        stateRef.updateChildren(u);
    }

    private void endRound() {
        if (finished) return;
        if (round == 1) {
            Map<String, Object> u = new HashMap<>();
            u.put("round", 2);
            u.put("turn", "player2");
            u.put("canGuess", false);
            u.put("opened", null);
            u.put("solved", null);
            u.put("roundStart", System.currentTimeMillis());
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
        timerBase = roundStart;
        if (timer != null) timer.cancel();
        if (roundStart == 0 || finished) return;

        final int roundAtStart = round;
        long remaining = ROUND_DURATION_MS - (System.currentTimeMillis() - roundStart);
        if (remaining <= 0) {
            if ("player1".equals(myRole)) endRound();
            return;
        }
        timer = new CountDownTimer(remaining, 500) {
            @Override
            public void onTick(long ms) {
                long sec = ms / 1000;
                tvTimer.setText(String.format(Locale.US, "%d:%02d", sec / 60, sec % 60));
            }
            @Override
            public void onFinish() {
                tvTimer.setText("0:00");
                if ("player1".equals(myRole) && !finished && round == roundAtStart) {
                    endRound();
                }
            }
        }.start();
    }

    private void clearInputs() {
        for (EditText et : etCols) if (et != null) et.setText("");
        if (etFinal != null) etFinal.setText("");
    }

    private void render() {
        tvRound.setText("Runda " + round + "/2");
        tvScore1.setText(String.valueOf(scoreP1));
        tvScore2.setText(String.valueOf(scoreP2));

        boolean mayGuess = mayGuessNow();
        boolean mayOpen = isMyTurn() && !finished && !mayGuess;

        if (finished) {
            tvPhase.setText("Kraj igre");
        } else if (isMyTurn()) {
            tvPhase.setText(mayGuess ? "Ti pogađaš" : "Ti otvaraš polje");
        } else {
            tvPhase.setText("Protivnik igra");
        }

        for (int c = 0; c < 4; c++) {
            for (int i = 0; i < 4; i++) {
                Button b = btnFields[c][i];
                if (solved[c] || opened[c][i]) {
                    b.setText(fields[c][i] != null ? fields[c][i] : "?");
                    b.setBackgroundTintList(ColorStateList.valueOf(
                            solved[c] ? COLOR_SOLVED : COLOR_OPENED));
                    b.setClickable(false);
                } else {
                    b.setText(COLS[c] + (i + 1));
                    b.setBackgroundTintList(ColorStateList.valueOf(colorHidden));
                    b.setClickable(mayOpen);
                }
            }
            if (solved[c]) {
                etCols[c].setText(colSolutions[c]);
                etCols[c].setEnabled(false);
            } else {
                etCols[c].setEnabled(mayGuess);
            }
        }

        etFinal.setEnabled(mayGuess);
        btnSubmitFinal.setEnabled(mayGuess);
        btnPass.setEnabled(mayGuess);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
        if (stateListener != null) stateRef.removeEventListener(stateListener);
    }
}