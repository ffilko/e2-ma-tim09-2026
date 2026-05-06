package com.example.slagalica.ui.fragments.games;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalica.R;

import java.util.Locale;
import java.util.Random;

public class MojBrojFragment extends Fragment {

    private TextView tvTarget, tvExpression;
    private GridLayout gridNumbers;
    private StringBuilder expression = new StringBuilder();
    private View mainRootView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        mainRootView = inflater.inflate(R.layout.fragment_moj_broj, container, false);

        tvTarget = mainRootView.findViewById(R.id.tvTarget);
        tvExpression = mainRootView.findViewById(R.id.tvExpression);
        gridNumbers = mainRootView.findViewById(R.id.gridNumbers);

        tvTarget.setText("123");

        createDummyNumbers();
        setupButtons(mainRootView);

        return mainRootView;
    }

    private void createDummyNumbers() {
        int[] nums = {1, 3, 5, 10, 25, 100};

        for (int num : nums) {
            Button btn = new Button(requireContext());
            btn.setText(String.valueOf(num));

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(4, 4, 4, 4);

            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> {
                expression.append(num);
                tvExpression.setText(expression.toString());
            });

            gridNumbers.addView(btn);
        }
    }

    private void setupButtons(View view) {
        view.findViewById(R.id.btnPlus).setOnClickListener(v -> add("+"));
        view.findViewById(R.id.btnMinus).setOnClickListener(v -> add("-"));
        view.findViewById(R.id.btnMul).setOnClickListener(v -> add("*"));
        view.findViewById(R.id.btnDiv).setOnClickListener(v -> add("/"));
        view.findViewById(R.id.btnLParen).setOnClickListener(v -> add("("));
        view.findViewById(R.id.btnRParen).setOnClickListener(v -> add(")"));
        view.findViewById(R.id.btnBackspace).setOnClickListener(v -> backspace());

        view.findViewById(R.id.btnClear).setOnClickListener(v -> {
            expression.setLength(0);
            tvExpression.setText("");
        });

        view.findViewById(R.id.btnStop).setOnClickListener(v -> {
            disableAllInputsExceptSubmit();
        });

        Button btnSubmit = view.findViewById(R.id.btnSubmit);
        btnSubmit.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putBoolean("finished", true);
            getParentFragmentManager().setFragmentResult("game_finished", result);
        });
    }

    private void add(String op) {
        expression.append(op);
        tvExpression.setText(expression.toString());
    }

    private void backspace() {
        if (expression.length() > 0) {
            expression.setLength(expression.length() - 1);
            tvExpression.setText(expression.toString());
        }
    }

    private void disableAllInputsExceptSubmit() {
        for (int i = 0; i < gridNumbers.getChildCount(); i++) {
            gridNumbers.getChildAt(i).setEnabled(false);
        }

        int[] idsToDisable = {
                R.id.btnPlus, R.id.btnMinus, R.id.btnMul, R.id.btnDiv,
                R.id.btnLParen, R.id.btnRParen, R.id.btnClear,
                R.id.btnBackspace, R.id.btnStop
        };

        for (int id : idsToDisable) {
            View v = mainRootView.findViewById(id);
            if (v != null) v.setEnabled(false);
        }

        Button btnSubmit = mainRootView.findViewById(R.id.btnSubmit);
        btnSubmit.setEnabled(true);
    }
}