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

public class KoZnaZnaFragment extends Fragment {

    private Button selectedAnswerButton;
    private TextView tvSelectedAnswer;
    private Button[] answerButtons;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_ko_zna_zna, container, false);

        tvSelectedAnswer = view.findViewById(R.id.tvSelectedAnswer);

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

        return view;
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
}