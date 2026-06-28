package com.example.slagalica;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginFragment extends Fragment {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private boolean isResetMode = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        TextView tvTitle = view.findViewById(R.id.title);
        TextInputEditText etEmailOrUsername = view.findViewById(R.id.email);
        TextInputEditText etPassword = view.findViewById(R.id.password);

        TextInputLayout newPasswordLayout = view.findViewById(R.id.newPasswordLayout);
        TextInputLayout confirmNewPasswordLayout = view.findViewById(R.id.confirmNewPasswordLayout);
        TextInputEditText etNewPassword = view.findViewById(R.id.newPassword);
        TextInputEditText etConfirmNewPassword = view.findViewById(R.id.confirmNewPassword);

        Button loginBtn = view.findViewById(R.id.loginBtn);
        TextView tvToggleResetMode = view.findViewById(R.id.tvToggleResetMode);

        tvToggleResetMode.setOnClickListener(v -> {
            isResetMode = !isResetMode;
            if (isResetMode) {
                tvTitle.setText("Promena lozinke");
                newPasswordLayout.setVisibility(View.VISIBLE);
                confirmNewPasswordLayout.setVisibility(View.VISIBLE);
                loginBtn.setText("Promeni lozinku");
                tvToggleResetMode.setText("Nazad na prijavu");
            } else {
                tvTitle.setText("Prijava");
                newPasswordLayout.setVisibility(View.GONE);
                confirmNewPasswordLayout.setVisibility(View.GONE);
                loginBtn.setText("Prijavi se");
                tvToggleResetMode.setText("Želite da promenite lozinku?");
            }
        });

        loginBtn.setOnClickListener(v -> {
            String input = etEmailOrUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(input) || TextUtils.isEmpty(password)) {
                Toast.makeText(getContext(), "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isResetMode) {
                String newPassword = etNewPassword.getText().toString().trim();
                String confirmPassword = etConfirmNewPassword.getText().toString().trim();

                if (TextUtils.isEmpty(newPassword) || TextUtils.isEmpty(confirmPassword)) {
                    Toast.makeText(getContext(), "Fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!newPassword.equals(confirmPassword)) {
                    Toast.makeText(getContext(), "Passwords do not match", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (newPassword.length() < 6) {
                    Toast.makeText(getContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                    return;
                }

                executePasswordResetWorkflow(input, password, newPassword);

            } else {
                if (input.contains("@")) {
                    loginWithEmail(input, password);
                } else {
                    loginWithUsername(input, password);
                }
            }
        });

        return view;
    }

    private void loginWithEmail(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null && !user.isEmailVerified()) {
                            Toast.makeText(getContext(), "Please check your email.", Toast.LENGTH_LONG).show();
                            mAuth.signOut();
                        } else {
                            ((MainActivity) requireActivity()).navigate(new GameMenuFragment(), true);
                        }
                    } else {
                        Toast.makeText(getContext(), "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loginWithUsername(String username, String password) {
        db.collection("users")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        String email = task.getResult().getDocuments().get(0).getString("email");
                        loginWithEmail(email, password);
                    } else {
                        Toast.makeText(getContext(), "User not found.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void executePasswordResetWorkflow(String input, String oldPassword, String newPassword) {
        if (input.contains("@")) {
            reauthenticateAndChangePassword(input, oldPassword, newPassword);
        } else {
            db.collection("users")
                    .whereEqualTo("username", input)
                    .limit(1)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && !task.getResult().isEmpty()) {
                            String email = task.getResult().getDocuments().get(0).getString("email");
                            reauthenticateAndChangePassword(email, oldPassword, newPassword);
                        } else {
                            Toast.makeText(getContext(), "User not found.", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void reauthenticateAndChangePassword(String email, String oldPassword, String newPassword) {
        mAuth.signInWithEmailAndPassword(email, oldPassword)
                .addOnCompleteListener(loginTask -> {
                    if (loginTask.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            AuthCredential credential = EmailAuthProvider.getCredential(email, oldPassword);

                            user.reauthenticate(credential)
                                    .addOnCompleteListener(reauthTask -> {
                                        if (reauthTask.isSuccessful()) {
                                            user.updatePassword(newPassword)
                                                    .addOnCompleteListener(updateTask -> {
                                                        if (updateTask.isSuccessful()) {
                                                            Toast.makeText(getContext(), "Password updated successfully!", Toast.LENGTH_SHORT).show();
                                                            ((MainActivity) requireActivity()).navigate(new GameMenuFragment(), true);
                                                        } else {
                                                            Toast.makeText(getContext(), "Failed: " + updateTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                                        }
                                                    });
                                        } else {
                                            Toast.makeText(getContext(), "Reauthentication failed.", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        }
                    } else {
                        Toast.makeText(getContext(), "Incorrect current password.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}