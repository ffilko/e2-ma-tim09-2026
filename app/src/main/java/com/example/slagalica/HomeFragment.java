package com.example.slagalica;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalica.MainActivity;
import com.example.slagalica.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HomeFragment extends Fragment {

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        MainActivity activity = (MainActivity) requireActivity();

        view.findViewById(R.id.btnSignIn).setOnClickListener(v ->
                activity.navigate(new LoginFragment(), true));

        view.findViewById(R.id.btnSignUp).setOnClickListener(v ->
                activity.navigate(new SignUpFragment(), true));

        view.findViewById(R.id.btnPlayAnon).setOnClickListener(v ->
                activity.navigate(new GameMenuFragment(), true));

        return view;
    }
}