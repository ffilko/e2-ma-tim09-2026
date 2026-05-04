package com.example.slagalica;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class SignUpFragment extends Fragment {


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view =  inflater.inflate(R.layout.fragment_sign_up, container, false);
        view.findViewById(R.id.registerBtn).setOnClickListener(v ->
                ((MainActivity) requireActivity()).navigate(new GameMenuFragment(), true));
        return view;
    }
}