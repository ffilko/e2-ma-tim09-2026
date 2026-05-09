package com.example.slagalica;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        view.findViewById(R.id.btnEditAvatar).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Izmena avatara je samo GUI za sada.", Toast.LENGTH_SHORT).show()
        );

        view.findViewById(R.id.btnLogout).setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Odjava korisnika", Toast.LENGTH_SHORT).show();

            requireActivity()
                    .getSupportFragmentManager()
                    .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

            ((MainActivity) requireActivity()).navigate(new HomeFragment(), false);
        });

        return view;
    }
}