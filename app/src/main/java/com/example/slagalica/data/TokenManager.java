package com.example.slagalica.data;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TokenManager {

    private static final int DAILY_TOKENS = 5;
    private static final int INITIAL_TOKENS = 5;
    private static final int STARS_PER_TOKEN = 50;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface Callback { void onResult(boolean success, String message); }
    public interface TokenCallback { void onResult(int tokens, int stars); }

    public void initializeNewUser(String uid, Callback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("tokens", INITIAL_TOKENS);
        data.put("stars", 0);
        data.put("lastTokenRefill", Timestamp.now());
        data.put("wins", 0);
        data.put("losses", 0);

        db.collection("users").document(uid)
                .set(data)
                .addOnSuccessListener(v -> callback.onResult(true, "OK"))
                .addOnFailureListener(e -> callback.onResult(false, e.getMessage()));
    }

    public void checkAndRefillTokens(String uid, Callback callback) {
        DocumentReference ref = db.collection("users").document(uid);
        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    long tokens = 0;
                    Object t = transaction.get(ref).get("tokens");
                    if (t instanceof Long) tokens = (Long) t;

                    Date lastRefillDate;
                    Object lr = transaction.get(ref).get("lastTokenRefill");
                    if (lr instanceof Timestamp) {
                        lastRefillDate = ((Timestamp) lr).toDate();
                    } else if (lr instanceof Long) {
                        lastRefillDate = new Date((Long) lr);
                    } else {
                        lastRefillDate = new Date();
                    }

                    Calendar last = Calendar.getInstance();
                    last.setTime(lastRefillDate);
                    last.set(Calendar.HOUR_OF_DAY, 0);
                    last.set(Calendar.MINUTE, 0);
                    last.set(Calendar.SECOND, 0);
                    last.set(Calendar.MILLISECOND, 0);

                    Calendar today = Calendar.getInstance();
                    today.set(Calendar.HOUR_OF_DAY, 0);
                    today.set(Calendar.MINUTE, 0);
                    today.set(Calendar.SECOND, 0);
                    today.set(Calendar.MILLISECOND, 0);

                    long daysDiff = (today.getTimeInMillis() - last.getTimeInMillis())
                            / (1000 * 60 * 60 * 24);

                    if (daysDiff >= 1) {
                        tokens += daysDiff * DAILY_TOKENS;
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("tokens", tokens);
                        updates.put("lastTokenRefill", new Timestamp(today.getTime()));
                        transaction.update(ref, updates);
                    }
                    return null;
                }).addOnSuccessListener(v -> callback.onResult(true, "OK"))
                .addOnFailureListener(e -> callback.onResult(false, e.getMessage()));
    }

    public void useToken(String uid, Callback callback) {
        DocumentReference ref = db.collection("users").document(uid);
        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    long tokens = 0;
                    Object t = transaction.get(ref).get("tokens");
                    if (t instanceof Long) tokens = (Long) t;

                    if (tokens <= 0) try {
                        throw new Exception("No tokens");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    transaction.update(ref, "tokens", tokens - 1);
                    return null;
                }).addOnSuccessListener(v -> callback.onResult(true, "OK"))
                .addOnFailureListener(e -> callback.onResult(false, e.getMessage()));
    }

    public void applyMatchResult(String uid, boolean won, int totalScore, Callback callback) {
        DocumentReference ref = db.collection("users").document(uid);
        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    long stars = 0;
                    Object s = transaction.get(ref).get("stars");
                    if (s instanceof Long) stars = (Long) s;

                    long tokens = 0;
                    Object t = transaction.get(ref).get("tokens");
                    if (t instanceof Long) tokens = (Long) t;

                    long wins = 0, losses = 0;
                    Object w = transaction.get(ref).get("wins");
                    Object l = transaction.get(ref).get("losses");
                    if (w instanceof Long) wins = (Long) w;
                    if (l instanceof Long) losses = (Long) l;

                    int scoreStars = totalScore / 40;

                    long newStars;
                    if (won) {
                        newStars = stars + 10 + scoreStars;
                        wins++;
                    } else {
                        newStars = stars - 10 + scoreStars;
                        if (newStars < 0) newStars = 0;
                        losses++;
                    }

                    long tokensEarned = newStars / STARS_PER_TOKEN;
                    long starsRemainder = newStars % STARS_PER_TOKEN;
                    tokens += tokensEarned;

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("stars", starsRemainder);
                    updates.put("tokens", tokens);
                    updates.put("wins", wins);
                    updates.put("losses", losses);
                    transaction.update(ref, updates);
                    return null;
                }).addOnSuccessListener(v -> callback.onResult(true, "OK"))
                .addOnFailureListener(e -> callback.onResult(false, e.getMessage()));
    }

    public void getStats(String uid, TokenCallback callback) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    int tokens = 0, stars = 0;
                    if (doc.exists()) {
                        Long t = doc.getLong("tokens");
                        Long s = doc.getLong("stars");
                        if (t != null) tokens = t.intValue();
                        if (s != null) stars = s.intValue();
                    }
                    callback.onResult(tokens, stars);
                });
    }

    public void distributeChallengeRewards(String challengeId, List<String> participants, List<Integer> scores, int starsBet, int tokensBet) {
        // Implementiraj logiku: 75% pobedniku, ostali dobijaju ulog nazad
        // Koristi runTransaction za sigurnost
    }
}