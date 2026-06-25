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

public class KoZnaZnaFragment extends Fragment {

    private Button selectedAnswerButton;
    private TextView tvSelectedAnswer;
    private Button[] answerButtons;

    private TextView tvScores;
    private DatabaseReference sessionScoresRef;
    private ValueEventListener scoresListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_ko_zna_zna, container, false);

        String sessionId = getArguments() != null ? getArguments().getString("sessionId") : null;

        tvSelectedAnswer = view.findViewById(R.id.tvSelectedAnswer);
        tvScores = view.findViewById(R.id.tvScores);

        answerButtons = new Button[]{
                view.findViewById(R.id.btnAnswer1),
                view.findViewById(R.id.btnAnswer2),
                view.findViewById(R.id.btnAnswer3),
                view.findViewById(R.id.btnAnswer4)
        };

        for (Button button : answerButtons) {
            button.setOnClickListener(v -> selectAnswer((Button) v));
        }

        view.findViewById(R.id.btnSubmitKoZnaZna).setOnClickListener(v -> {
            if (selectedAnswerButton == null) {
                Toast.makeText(requireContext(), "Izaberi jedan odgovor.", Toast.LENGTH_SHORT).show();
                return;
            }

            Bundle result = new Bundle();
            result.putBoolean("finished", true);
            getParentFragmentManager().setFragmentResult("game_finished", result);
        });

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

    private void selectAnswer(Button clickedButton) {
        for (Button button : answerButtons) {
            button.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.slagalica_color)
            ));
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        }

        clickedButton.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.black)
        ));
        clickedButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));

        selectedAnswerButton = clickedButton;
        tvSelectedAnswer.setText("Odabran odgovor: " + clickedButton.getText().toString());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (scoresListener != null && sessionScoresRef != null)
            sessionScoresRef.removeEventListener(scoresListener);
    }
}