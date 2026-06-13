package com.example.slagalica;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        view.findViewById(R.id.btnEditAvatar).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Izmena avatara - u razvoju", Toast.LENGTH_SHORT).show()
        );

        view.findViewById(R.id.btnLogout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            requireActivity()
                    .getSupportFragmentManager()
                    .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            ((MainActivity) requireActivity()).navigate(new HomeFragment(), false);
        });

        loadProfileData(view);

        return view;
    }

    private void loadProfileData(View root) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Niste prijavljeni", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || !isAdded()) return;

                    String username = doc.getString("username");
                    String email = doc.getString("email");
                    String region = doc.getString("region");
                    Long tokens = doc.getLong("tokens");
                    Long stars = doc.getLong("stars");
                    Long league = doc.getLong("league");

                    // Update profile card - traverse the view tree
                    ViewGroup scrollContent = (ViewGroup) ((ViewGroup) root).getChildAt(0);

                    // Card 1 (index 1) - Profile info
                    updateTextAfter(scrollContent, "Korisničko ime",
                            username != null ? username : "N/A");
                    updateTextAfter(scrollContent, "Email adresa",
                            email != null ? email : "N/A");
                    updateTextAfter(scrollContent, "Region",
                            region != null && !region.isEmpty() ? region : "Nije postavljen");

                    // Card 2 (index 2) - Tokens, Stars, League
                    updateValueUnderLabel(scrollContent, "Tokeni",
                            tokens != null ? String.valueOf(tokens) : "0");
                    updateValueUnderLabel(scrollContent, "Zvezde",
                            stars != null ? String.valueOf(stars) : "0");
                    updateValueUnderLabel(scrollContent, "Liga",
                            getLeagueName(league != null ? league.intValue() : 0));

                    // Stats card
                    loadGameStats(scrollContent, user.getUid());
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Greška pri učitavanju profila", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadGameStats(ViewGroup root, String uid) {
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("stats")
                .document("gameStats")
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || !isAdded()) return;

                    Long totalGames = doc.getLong("totalGames");
                    Long wins = doc.getLong("wins");

                    if (totalGames != null) {
                        updateTextContaining(root, "Ukupan broj odigranih",
                                "Ukupan broj odigranih partija: " + totalGames);
                    }

                    if (totalGames != null && totalGames > 0 && wins != null) {
                        int winPct = (int) (wins * 100 / totalGames);
                        updateTextContaining(root, "Pobeđene",
                                "Pobeđene / izgubljene partije: " + winPct + "% / " + (100 - winPct) + "%");
                    }
                });
    }

    private void updateTextAfter(ViewGroup parent, String labelText, String newValue) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                if (tv.getText().toString().equals(labelText)) {
                    if (i + 1 < parent.getChildCount() && parent.getChildAt(i + 1) instanceof TextView) {
                        ((TextView) parent.getChildAt(i + 1)).setText(newValue);
                        return;
                    }
                }
            } else if (child instanceof ViewGroup) {
                updateTextAfter((ViewGroup) child, labelText, newValue);
            }
        }
    }

    private void updateValueUnderLabel(ViewGroup parent, String labelText, String newValue) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) child;
                boolean foundLabel = false;
                for (int j = 0; j < group.getChildCount(); j++) {
                    View v = group.getChildAt(j);
                    if (v instanceof TextView) {
                        if (((TextView) v).getText().toString().equals(labelText)) {
                            foundLabel = true;
                        } else if (foundLabel) {
                            ((TextView) v).setText(newValue);
                            return;
                        }
                    } else if (v instanceof ViewGroup) {
                        updateValueUnderLabel((ViewGroup) v, labelText, newValue);
                    }
                }
            }
        }
    }

    private void updateTextContaining(ViewGroup parent, String contains, String newText) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView) {
                if (((TextView) child).getText().toString().contains(contains)) {
                    ((TextView) child).setText(newText);
                    return;
                }
            } else if (child instanceof ViewGroup) {
                updateTextContaining((ViewGroup) child, contains, newText);
            }
        }
    }

    private String getLeagueName(int league) {
        switch (league) {
            case 1: return "★ Bronzana";
            case 2: return "★★ Srebrna";
            case 3: return "★★★ Zlatna";
            case 4: return "★★★★ Platinasta";
            case 5: return "★★★★★ Dijamantska";
            default: return "Početnik";
        }
    }
}