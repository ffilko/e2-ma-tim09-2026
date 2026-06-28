package com.example.slagalica.data;

import androidx.annotation.NonNull;

import com.example.slagalica.data.model.Challenge;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChallengeManager {

    private final DatabaseReference challengesRef = FirebaseDatabase.getInstance(
            "https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app"
    ).getReference("challenges");

    public interface ChallengesCallback { void onLoaded(List<Challenge> challenges); }
    public interface CreateCallback { void onComplete(boolean success, String challengeId); }
    public interface SimpleCallback { void onComplete(boolean success); }
    public interface SingleChallengeCallback { void onResult(Challenge challenge); }

    public void createChallenge(String region, int starsBet, int tokensBet,
                                CreateCallback onSuccess,
                                SimpleCallback onError) {
        com.google.firebase.auth.FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { onError.onComplete(false); return; }

        String uid = user.getUid();

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("username");
                    if (name == null) name = "Igrač";

                    String id = challengesRef.push().getKey();
                    Challenge challenge = new Challenge(id, uid, name, region, starsBet, tokensBet);
                    challengesRef.child(id).setValue(challenge)
                            .addOnSuccessListener(a -> onSuccess.onComplete(true, id))  // <- vrati id
                            .addOnFailureListener(e -> onError.onComplete(false));
                })
                .addOnFailureListener(e -> onError.onComplete(false));


    }

    public void joinChallenge(String challengeId, SimpleCallback callback) {
        com.google.firebase.auth.FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { callback.onComplete(false); return; }

        String uid = user.getUid();

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("username");
                    if (name == null) name = "Igrač";
                    final String finalName = name;

                    challengesRef.child(challengeId).runTransaction(new Transaction.Handler() {
                        @Override
                        public Transaction.Result doTransaction(MutableData mutableData) {
                            Challenge ch = mutableData.getValue(Challenge.class);
                            if (ch == null) return Transaction.abort();
                            if (ch.participants == null) ch.participants = new java.util.HashMap<>();
                            if (ch.names == null) ch.names = new java.util.HashMap<>();

                            if (ch.participants.size() >= 4) return Transaction.abort();
                            if (ch.participants.containsKey(uid)) return Transaction.abort();
                            if (!"open".equals(ch.status)) return Transaction.abort();

                            ch.participants.put(uid, true);
                            ch.names.put(uid, finalName);

                            mutableData.setValue(ch);
                            return Transaction.success(mutableData);
                        }

                        @Override
                        public void onComplete(DatabaseError error, boolean committed,
                                               DataSnapshot dataSnapshot) {
                            callback.onComplete(committed && error == null);
                        }
                    });
                });
    }

    public void lockChallenge(String challengeId) {
        challengesRef.child(challengeId).child("status").setValue("locked");
    }

    public void submitScore(String challengeId, String uid, int score) {
        challengesRef.child(challengeId).child("scores").child(uid).setValue(score);
    }

    public void listenToOpenChallenges(String region, ChallengesCallback callback) {
        challengesRef.orderByChild("region").equalTo(region)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        List<Challenge> list = new ArrayList<>();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            Challenge ch = ds.getValue(Challenge.class);
                            if (ch != null && "open".equals(ch.status)) {
                                ch.id = ds.getKey();
                                list.add(ch);
                            }
                        }
                        callback.onLoaded(list);
                    }
                    @Override public void onCancelled(DatabaseError error) {}
                });
    }

    public void listenToMyChallenge(String challengeId, SingleChallengeCallback callback) {
        challengesRef.child(challengeId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Challenge ch = snapshot.getValue(Challenge.class);
                if (ch != null) ch.id = challengeId;
                callback.onResult(ch);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    public void startChallenge(String challengeId, SimpleCallback callback) {
        challengesRef.child(challengeId).child("status").setValue("locked")
                .addOnSuccessListener(v -> callback.onComplete(true))
                .addOnFailureListener(e -> callback.onComplete(false));
    }

    public void listenForStart(String challengeId, String myUid, Runnable onStart) {
        challengesRef.child(challengeId).child("status")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if ("locked".equals(snapshot.getValue(String.class))) {
                            snapshot.getRef().getParent()
                                    .removeEventListener(this);
                            onStart.run();
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    public void getChallenge(String challengeId, SingleChallengeCallback callback) {
        challengesRef.child(challengeId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Challenge ch = snapshot.getValue(Challenge.class);
                if (ch != null) ch.id = challengeId;
                callback.onResult(ch);
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    public void listenForAllScores(String challengeId, int expectedCount,
                                   ChallengesCallback callback) {
        challengesRef.child(challengeId).child("scores")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.getChildrenCount() >= expectedCount) {
                            getChallenge(challengeId, ch -> {
                                List<Challenge> list = new ArrayList<>();
                                if (ch != null) list.add(ch);
                                callback.onLoaded(list);
                            });
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    public void distributeRewards(Challenge challenge) {
        challengesRef.child(challenge.id).child("status")
                .runTransaction(new Transaction.Handler() {
                    @Override
                    public Transaction.Result doTransaction(MutableData mutableData) {
                        String status = mutableData.getValue(String.class);
                        if ("finished".equals(status) || "distributing".equals(status)) {
                            return Transaction.abort();
                        }
                        mutableData.setValue("distributing");
                        return Transaction.success(mutableData);
                    }
                    @Override
                    public void onComplete(DatabaseError error, boolean committed, DataSnapshot snap) {
                        if (!committed) return;

                        List<Map.Entry<String, Integer>> sorted =
                                new ArrayList<>(challenge.scores.entrySet());
                        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

                        int totalStars = challenge.starsBet * challenge.participants.size();
                        int totalTokens = challenge.tokensBet * challenge.participants.size();

                        int winnerStars = (int) (totalStars * 0.75);
                        int winnerTokens = (int) (totalTokens * 0.75);

                        for (int i = 0; i < sorted.size(); i++) {
                            String uid = sorted.get(i).getKey();
                            DocumentReference ref =
                                    FirebaseFirestore.getInstance().collection("users").document(uid);

                            if (i == 0) {
                                ref.update(
                                        "stars", FieldValue.increment(winnerStars),
                                        "tokens", FieldValue.increment(winnerTokens)
                                );
                            } else if (i == 1) {
                                ref.update(
                                        "stars", FieldValue.increment(challenge.starsBet),
                                        "tokens", FieldValue.increment(challenge.tokensBet)
                                );
                            }
                        }

                        challengesRef.child(challenge.id).child("status").setValue("finished");
                    }
                });
    }

    public void deductBet(String uid, int stars, int tokens, SimpleCallback callback) {
        com.google.firebase.firestore.DocumentReference ref =
                FirebaseFirestore.getInstance().collection("users").document(uid);

        FirebaseFirestore.getInstance().runTransaction(transaction -> {
                    Long currentStars = transaction.get(ref).getLong("stars");
                    Long currentTokens = transaction.get(ref).getLong("tokens");

                    long s = currentStars != null ? currentStars : 0;
                    long t = currentTokens != null ? currentTokens : 0;

                    if (s < stars || t < tokens)
                        throw new RuntimeException("Nema dovoljno resursa");

                    transaction.update(ref, "stars", s - stars, "tokens", t - tokens);
                    return null;
                }).addOnSuccessListener(v -> callback.onComplete(true))
                .addOnFailureListener(e -> callback.onComplete(false));
    }


}