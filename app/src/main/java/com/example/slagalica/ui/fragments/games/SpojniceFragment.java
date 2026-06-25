package com.example.slagalica.ui.fragments.games;

import android.content.res.ColorStateList;
import android.os.Bundle;
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

public class SpojniceFragment extends Fragment {

    private Button selectedLeftButton;
    private Button selectedRightButton;

    private Button[] leftButtons;
    private Button[] rightButtons;

    private TextView tvSelectedPair;
    private TextView tvConnectedPairs;

    private int connectedPairs = 0;

    private TextView tvScores;
    private DatabaseReference sessionScoresRef;
    private ValueEventListener scoresListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_spojnice, container, false);

        String sessionId = getArguments() != null ? getArguments().getString("sessionId") : null;

        tvSelectedPair = view.findViewById(R.id.tvSelectedPair);
        tvConnectedPairs = view.findViewById(R.id.tvConnectedPairs);
        tvScores = view.findViewById(R.id.tvScores);

        leftButtons = new Button[]{
                view.findViewById(R.id.btnLeft1),
                view.findViewById(R.id.btnLeft2),
                view.findViewById(R.id.btnLeft3),
                view.findViewById(R.id.btnLeft4),
                view.findViewById(R.id.btnLeft5)
        };

        rightButtons = new Button[]{
                view.findViewById(R.id.btnRight1),
                view.findViewById(R.id.btnRight2),
                view.findViewById(R.id.btnRight3),
                view.findViewById(R.id.btnRight4),
                view.findViewById(R.id.btnRight5)
        };

        for (Button button : leftButtons) {
            button.setOnClickListener(v -> selectLeftButton((Button) v));
        }

        for (Button button : rightButtons) {
            button.setOnClickListener(v -> selectRightButton((Button) v));
        }

        view.findViewById(R.id.btnConnectPair).setOnClickListener(v -> connectPair());
        view.findViewById(R.id.btnClearSelection).setOnClickListener(v -> clearCurrentSelection());
        view.findViewById(R.id.btnFinishSpojnice).setOnClickListener(v -> finishGame());

        if (sessionId != null) listenToSessionScores(sessionId);

        return view;
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
                    tvScores.setText("Igrač 1: " + (s1 != null ? s1 : 0) + "  |  Igrač 2: " + (s2 != null ? s2 : 0));
            }
            @Override
            public void onCancelled(@NonNull DatabaseError e) {}
        };
        sessionScoresRef.addValueEventListener(scoresListener);
    }

    private void selectLeftButton(Button button) {
        resetGroup(leftButtons);
        highlightButton(button);
        selectedLeftButton = button;
        updateSelectedPairText();
    }

    private void selectRightButton(Button button) {
        resetGroup(rightButtons);
        highlightButton(button);
        selectedRightButton = button;
        updateSelectedPairText();
    }

    private void resetGroup(Button[] group) {
        for (Button button : group) {
            if (button.isEnabled()) {
                button.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.slagalica_color)
                ));
                button.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
            }
        }
    }

    private void highlightButton(Button button) {
        button.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.black)
        ));
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
    }

    private void updateSelectedPairText() {
        String left = selectedLeftButton == null ? "-" : selectedLeftButton.getText().toString();
        String right = selectedRightButton == null ? "-" : selectedRightButton.getText().toString();

        tvSelectedPair.setText("Izabrano: " + left + " — " + right);
    }

    private void connectPair() {
        if (selectedLeftButton == null || selectedRightButton == null) {
            Toast.makeText(requireContext(), "Izaberi po jedan pojam iz obe kolone.", Toast.LENGTH_SHORT).show();
            return;
        }

        String leftText = selectedLeftButton.getText().toString();
        String rightText = selectedRightButton.getText().toString();

        selectedLeftButton.setEnabled(false);
        selectedRightButton.setEnabled(false);

        selectedLeftButton.setAlpha(0.55f);
        selectedRightButton.setAlpha(0.55f);

        connectedPairs++;
        tvConnectedPairs.setText("Povezano parova: " + connectedPairs + "/5");
        tvSelectedPair.setText("Poslednji par: " + leftText + " — " + rightText);

        selectedLeftButton = null;
        selectedRightButton = null;

        resetGroup(leftButtons);
        resetGroup(rightButtons);

        if (connectedPairs == 5) {
            Toast.makeText(requireContext(), "Svi parovi su povezani.", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearCurrentSelection() {
        selectedLeftButton = null;
        selectedRightButton = null;

        resetGroup(leftButtons);
        resetGroup(rightButtons);

        tvSelectedPair.setText("Izabrano: -");
    }

    private void finishGame() {
        Bundle result = new Bundle();
        result.putBoolean("finished", true);
        getParentFragmentManager().setFragmentResult("game_finished", result);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (scoresListener != null && sessionScoresRef != null)
            sessionScoresRef.removeEventListener(scoresListener);
    }
}