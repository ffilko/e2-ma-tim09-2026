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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_korak_po_korak, container, false);

        tvRound = view.findViewById(R.id.tvRound);
        tvPhase = view.findViewById(R.id.tvPhase);
        llClues = view.findViewById(R.id.llClues);
        etGuess = view.findViewById(R.id.etGuess);
        btnGuess = view.findViewById(R.id.btnGuess);

        tvRound.setText("Runda 1/2");
        tvPhase.setText("Igrač 1 pogađa");

        addDummySteps();

        btnGuess.setOnClickListener(v -> {
            String text = etGuess.getText().toString();
            etGuess.setText("");

            Bundle result = new Bundle();
            result.putBoolean("finished", true);
            getParentFragmentManager().setFragmentResult("game_finished", result);
        });

        return view;
    }

    private void addDummySteps() {
        // Boja tvoje aplikacije (izvučena iz resursa)
        int slagalicaColor = getResources().getColor(R.color.slagalica_color);

        for (int i = 1; i <= 7; i++) {
            TextView tv = new TextView(requireContext());
            tv.setText("Korak " + i + " — neki tekst");

            // 1. Unutrašnji razmak (padding) da tekst ne udara u ivice pravougaonika
            tv.setPadding(32, 24, 32, 24);
            tv.setTextColor(android.graphics.Color.WHITE); // Beli tekst na tamnoj pozadini
            tv.setTextSize(16f);

            // 2. Kreiranje pravougaonika sa zaobljenim uglovima
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            shape.setCornerRadius(15f); // Zaobljeni uglovi
            shape.setColor(slagalicaColor); // Boja unutrašnjosti

            tv.setBackground(shape);

            // 3. Spoljašnji razmak (margina) između koraka
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 16); // 16px razmaka ispod svakog pravougaonika
            tv.setLayoutParams(params);

            llClues.addView(tv);
        }
    }

}