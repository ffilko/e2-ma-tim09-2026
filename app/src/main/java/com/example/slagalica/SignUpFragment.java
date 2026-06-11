package com.example.slagalica;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignUpFragment extends Fragment {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view =  inflater.inflate(R.layout.fragment_sign_up, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        TextInputEditText etEmail = view.findViewById(R.id.email);
        TextInputEditText etUsername = view.findViewById(R.id.username);
        TextInputEditText etPassword = view.findViewById(R.id.password);
        TextInputEditText etConfirm = view.findViewById(R.id.confirmPassword);

        view.findViewById(R.id.registerBtn).setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String confirm = etConfirm.getText().toString().trim();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(username)
                    || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirm)) {
                Toast.makeText(getContext(), "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(confirm)) {
                Toast.makeText(getContext(), "Password are not same", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            user.sendEmailVerification()
                                    .addOnCompleteListener(emailTask -> {
                                        mAuth.signOut();
                                        Toast.makeText(getContext(),
                                                "Registracija uspešna! Proverite email za potvrdu.",
                                                Toast.LENGTH_LONG).show();
                                        ((MainActivity) requireActivity()).navigate(new LoginFragment(), false);
                                    });

                            Map<String, Object> userData = new HashMap<>();
                            userData.put("username", username);
                            userData.put("email", email);
                            userData.put("tokens", 5);
                            userData.put("stars", 0);
                            userData.put("league", 0);
                            userData.put("region", "");
                            userData.put("avatarUrl", "");
                            userData.put("createdAt", System.currentTimeMillis());
                            userData.put("lastTokenRefill", System.currentTimeMillis());

                            db.collection("users").document(user.getUid())
                                    .set(userData)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(getContext(),
                                                "Successfully registration! Check your email.",
                                                Toast.LENGTH_LONG).show();
                                        mAuth.signOut();
                                        ((MainActivity) requireActivity()).navigate(new LoginFragment(), false);
                                    });
                        } else {
                            Toast.makeText(getContext(),
                                    "Error: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });

        return view;
    }
}