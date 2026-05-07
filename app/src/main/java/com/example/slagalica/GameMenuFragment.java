package com.example.slagalica;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class GameMenuFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_game_menu, container, false);
        view.findViewById(R.id.btnStartGame).setOnClickListener(v -> ((MainActivity) requireActivity()).navigate(new GameFragment(), true));
        view.findViewById(R.id.btnBellIcon).setOnClickListener(v -> ((MainActivity) requireActivity()).navigate(new NotificationsFragment(), true));
        return view;
    }
}