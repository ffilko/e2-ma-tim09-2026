package com.example.slagalica.ui.fragments.games;

import android.os.Bundle;
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

public class KorakPoKorakFragment extends Fragment {

    private TextView tvRound, tvPhase;
    private LinearLayout llClues;
    private EditText etGuess;
    private Button btnGuess;

    private static final String[] STEPS = {
            "Korak 1 — Rođen je 1856. godine.",
            "Korak 2 — Studirao je elektrotehniku u Gracu i Pragu, ali nije završio fakultet.",
            "Korak 3 — Radio je u Njujorku.",
            "Korak 4 — Ima veza sa AC.",
            "Korak 5 — Izgradio je kulu Vordenklif na Long Ajlendu za bežični prenos energije.",
            "Korak 6 — Međunarodna jedinica za magnetnu indukciju nosi njegovo ime.",
            "Korak 7 — Njegova fotografija se nalazi na srpskoj novčanici od 100 dinara."
    };

    private static final String ANSWER = "Nikola Tesla";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,@Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_korak_po_korak, container, false);

        tvRound = view.findViewById(R.id.tvRound);
        tvPhase = view.findViewById(R.id.tvPhase);
        llClues = view.findViewById(R.id.llClues);
        etGuess = view.findViewById(R.id.etGuess);
        btnGuess = view.findViewById(R.id.btnGuess);

        tvRound.setText("Runda 1/2");
        tvPhase.setText("Igrač 1 pogađa");

        addSteps();

        btnGuess.setOnClickListener(v -> {
            String text = etGuess.getText().toString().trim();
            etGuess.setText("");

            Bundle result = new Bundle();
            result.putBoolean("finished", true);
            result.putBoolean("correct", text.equalsIgnoreCase(ANSWER));
            getParentFragmentManager().setFragmentResult("game_finished", result);
        });

        return view;
    }

    private void addSteps() {
        int slagalicaColor = getResources().getColor(R.color.slagalica_color);

        for (String step : STEPS) {
            TextView tv = new TextView(requireContext());
            tv.setText(step);
            tv.setPadding(32, 24, 32, 24);
            tv.setTextColor(android.graphics.Color.WHITE);
            tv.setTextSize(16f);

            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            shape.setCornerRadius(15f);
            shape.setColor(slagalicaColor);
            tv.setBackground(shape);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 16);
            tv.setLayoutParams(params);

            llClues.addView(tv);
        }
    }
}