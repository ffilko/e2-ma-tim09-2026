package com.example.slagalica;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private static final String[] AVATAR_EMOJIS = {
            "🧑", "👩", "🧔", "👨‍🎓",
            "👩‍🚀", "🦸", "🧙", "🤖",
            "👽", "🎅", "🐱", "🦊",
            "🐶", "🐼", "🦁", "🐯",
            "🐸", "🐵", "🦄", "🐧"
    };

    private static final String[] AVATAR_NAMES = {
            "Osoba", "Žena", "Bradoša", "Student",
            "Astronaut", "Heroj", "Čarobnjak", "Robot",
            "Vanzemaljac", "Deda Mraz", "Mačka", "Lisica",
            "Pas", "Panda", "Lav", "Tigar",
            "Žaba", "Majmun", "Jednorog", "Pingvin"
    };

    private static final int[] AVATAR_COLORS = {
            Color.parseColor("#2596be"),
            Color.parseColor("#e74c3c"),
            Color.parseColor("#2ecc71"),
            Color.parseColor("#f39c12"),
            Color.parseColor("#9b59b6"),
            Color.parseColor("#1abc9c"),
            Color.parseColor("#e67e22"),
            Color.parseColor("#3498db"),
            Color.parseColor("#e91e63"),
            Color.parseColor("#00bcd4"),
            Color.parseColor("#ff5722"),
            Color.parseColor("#607d8b"),
            Color.parseColor("#795548"),
            Color.parseColor("#9e9d24"),
            Color.parseColor("#c2185b"),
            Color.parseColor("#ff9800"),
            Color.parseColor("#4caf50"),
            Color.parseColor("#673ab7"),
            Color.parseColor("#f06292"),
            Color.parseColor("#37474f")
    };

    private TextView tvUsername, tvEmail, tvRegion, tvAvatarFrame, tvAvatarEmoji;
    private TextView tvTokens, tvStars, tvLeague;
    private TextView tvAvgKzz, tvAvgSpoj, tvAvgMb, tvAvgKpk, tvAvgSk, tvAvgAs;
    private TextView tvStatKoZnaZna, tvStatMojBroj, tvStatKorak;
    private TextView tvStatAsocijacije, tvStatSkocko, tvStatSpojnice;
    private TextView tvTotalGames, tvWinLoss;
    private ImageView ivQrCode;
    private MaterialCardView cardAvatar;

    private int currentAvatarIndex = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        bindViews(view);

        view.findViewById(R.id.btnEditAvatar).setOnClickListener(v -> showAvatarPicker());

        view.findViewById(R.id.btnLogout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            requireActivity().getSupportFragmentManager()
                    .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            ((MainActivity) requireActivity()).navigate(new HomeFragment(), false);
        });

        loadProfileData();
        return view;
    }

    private void bindViews(View v) {
        tvUsername = v.findViewById(R.id.tvUsername);
        tvEmail = v.findViewById(R.id.tvEmail);
        tvRegion = v.findViewById(R.id.tvRegion);
        tvAvatarFrame = v.findViewById(R.id.tvAvatarFrame);
        tvAvatarEmoji = v.findViewById(R.id.tvAvatarEmoji);
        tvTokens = v.findViewById(R.id.tvTokens);
        tvStars = v.findViewById(R.id.tvStars);
        tvLeague = v.findViewById(R.id.tvLeague);
        tvAvgKzz = v.findViewById(R.id.tvAvgKzz);
        tvAvgSpoj = v.findViewById(R.id.tvAvgSpoj);
        tvAvgMb = v.findViewById(R.id.tvAvgMb);
        tvAvgKpk = v.findViewById(R.id.tvAvgKpk);
        tvAvgSk = v.findViewById(R.id.tvAvgSk);
        tvAvgAs = v.findViewById(R.id.tvAvgAs);
        tvStatKoZnaZna = v.findViewById(R.id.tvStatKoZnaZna);
        tvStatMojBroj = v.findViewById(R.id.tvStatMojBroj);
        tvStatKorak = v.findViewById(R.id.tvStatKorak);
        tvStatAsocijacije = v.findViewById(R.id.tvStatAsocijacije);
        tvStatSkocko = v.findViewById(R.id.tvStatSkocko);
        tvStatSpojnice = v.findViewById(R.id.tvStatSpojnice);
        tvTotalGames = v.findViewById(R.id.tvTotalGames);
        tvWinLoss = v.findViewById(R.id.tvWinLoss);
        ivQrCode = v.findViewById(R.id.ivQrCode);
        cardAvatar = v.findViewById(R.id.cardAvatar);
    }

    private void showAvatarPicker() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_avatar_picker, null);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        GridLayout grid = dialogView.findViewById(R.id.gridAvatars);
        grid.setColumnCount(4);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int dialogPadding = dp(24);
        int marginTotal = dp(8) * 4;
        int available = screenWidth - dialogPadding - marginTotal - dp(40);
        int size = available / 4;

        if (size < dp(48)) size = dp(48);
        if (size > dp(80)) size = dp(80);

        for (int i = 0; i < AVATAR_EMOJIS.length; i++) {
            final int index = i;

            android.widget.FrameLayout wrapper = new android.widget.FrameLayout(requireContext());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = size;
            params.height = size;
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            wrapper.setLayoutParams(params);

            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(AVATAR_COLORS[i]);
            circle.setSize(size, size);

            TextView tv = new TextView(requireContext());
            android.widget.FrameLayout.LayoutParams tvParams = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
            tv.setLayoutParams(tvParams);
            tv.setGravity(Gravity.CENTER);
            tv.setText(AVATAR_EMOJIS[i]);
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size * 0.45f);
            tv.setBackground(circle);

            wrapper.addView(tv);
            wrapper.setOnClickListener(v -> {
                selectAvatar(index);
                dialog.dismiss();
            });

            grid.addView(wrapper);
        }

        dialog.show();
    }

    private void selectAvatar(int index) {
        currentAvatarIndex = index;

        tvAvatarEmoji.setText(AVATAR_EMOJIS[index]);
        cardAvatar.setCardBackgroundColor(AVATAR_COLORS[index]);
        cardAvatar.setStrokeColor(AVATAR_COLORS[index]);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Map<String, Object> update = new HashMap<>();
        update.put("avatarIndex", index);

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .update(update)
                .addOnSuccessListener(v ->
                        Toast.makeText(requireContext(),
                                "Avatar: " + AVATAR_NAMES[index],
                                Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Greška pri čuvanju",
                                Toast.LENGTH_SHORT).show());
    }

    private void loadProfileData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Niste prijavljeni", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();

        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || !isAdded()) return;

                    tvUsername.setText(doc.getString("username") != null
                            ? doc.getString("username") : "N/A");
                    tvEmail.setText(doc.getString("email") != null
                            ? doc.getString("email") : "N/A");

                    String region = doc.getString("region");
                    tvRegion.setText(region != null && !region.isEmpty()
                            ? region : "Nije postavljen");

                    Long tokens = doc.getLong("tokens");
                    Long stars = doc.getLong("stars");
                    Long league = doc.getLong("league");

                    tvTokens.setText(tokens != null ? String.valueOf(tokens) : "0");
                    tvStars.setText(stars != null ? String.valueOf(stars) : "0");

                    int leagueVal = league != null ? league.intValue() : 0;
                    tvLeague.setText(getLeagueName(leagueVal));
                    tvAvatarFrame.setText(getFrameName(leagueVal));

                    Long avatarIdx = doc.getLong("avatarIndex");
                    if (avatarIdx != null && avatarIdx >= 0 && avatarIdx < AVATAR_EMOJIS.length) {
                        currentAvatarIndex = avatarIdx.intValue();
                        tvAvatarEmoji.setText(AVATAR_EMOJIS[currentAvatarIndex]);
                        cardAvatar.setCardBackgroundColor(AVATAR_COLORS[currentAvatarIndex]);
                        cardAvatar.setStrokeColor(AVATAR_COLORS[currentAvatarIndex]);
                    }

                    generateQrCode(uid);
                    loadGameStats(uid);
                })
                .addOnFailureListener(e -> {
                    if (isAdded())
                        Toast.makeText(requireContext(),
                                "Greška pri učitavanju profila",
                                Toast.LENGTH_SHORT).show();
                });
    }

    private void loadGameStats(String uid) {
        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("stats").document("gameStats")
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;
                    if (!doc.exists()) return;

                    tvAvgKzz.setText("Ko zna zna\n" + val(doc.getLong("avgKoZnaZna")));
                    tvAvgSpoj.setText("Spojnice\n" + val(doc.getLong("avgSpojnice")));
                    tvAvgMb.setText("Moj broj\n" + val(doc.getLong("avgMojBroj")));
                    tvAvgKpk.setText("Korak po korak\n" + val(doc.getLong("avgKorakPoKorak")));
                    tvAvgSk.setText("Skočko\n" + val(doc.getLong("avgSkocko")));
                    tvAvgAs.setText("Asocijacije\n" + val(doc.getLong("avgAsocijacije")));

                    tvStatKoZnaZna.setText("🎯 Ko zna zna: "
                            + val(doc.getLong("kzzCorrect")) + " pogođenih / "
                            + val(doc.getLong("kzzWrong")) + " promašenih");

                    tvStatMojBroj.setText("🔢 Moj broj: "
                            + val(doc.getLong("mojBrojPercent")) + "% tačnih");

                    String korakStat = doc.getString("korakPoKorakStat");
                    tvStatKorak.setText("👣 Korak po korak: "
                            + (korakStat != null ? korakStat : "0%"));

                    tvStatAsocijacije.setText("🧩 Asocijacije: "
                            + val(doc.getLong("asocijacijeSolved")) + " rešenih / "
                            + val(doc.getLong("asocijacijeUnsolved")) + " nerešenih");

                    String skockoStat = doc.getString("skockoStat");
                    tvStatSkocko.setText("⭐ Skočko: "
                            + (skockoStat != null ? skockoStat : "0%"));

                    tvStatSpojnice.setText("🔗 Spojnice: "
                            + val(doc.getLong("spojnicePercent")) + "% uspešnih");

                    Long totalGames = doc.getLong("totalGames");
                    tvTotalGames.setText(val(totalGames));

                    Long wins = doc.getLong("wins");
                    if (totalGames != null && totalGames > 0 && wins != null) {
                        int winPct = (int) (wins * 100 / totalGames);
                        tvWinLoss.setText(winPct + "% / " + (100 - winPct) + "%");
                    } else {
                        tvWinLoss.setText("0% / 0%");
                    }
                });
    }

    private void generateQrCode(String uid) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode("slagalica:friend:" + uid,
                    BarcodeFormat.QR_CODE, 400, 400);

            int w = matrix.getWidth();
            int h = matrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);

            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            ivQrCode.setImageBitmap(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private String val(Long v) {
        return v != null ? String.valueOf(v) : "0";
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

    private String getFrameName(int league) {
        switch (league) {
            case 1: return "Bronzani okvir";
            case 2: return "Srebrni okvir";
            case 3: return "Zlatni okvir";
            case 4: return "Platinasti okvir";
            case 5: return "Dijamantski okvir";
            default: return "Podrazumevani okvir";
        }
    }
}