package com.example.slagalica.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class SessionManager {
    private static final String SESSIONS_PATH = "sessions";
    private final DatabaseReference db;
    private DatabaseReference sessionRef;
    private String sessionId;
    private String myPlayerId;

    public SessionManager() {
        db = FirebaseDatabase.getInstance("https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app/").getReference();
    }

    public void createSession(String player1Id, String player1Name, OnSessionCreated callback) {
        sessionId = db.child(SESSIONS_PATH).push().getKey();
        sessionRef = db.child(SESSIONS_PATH).child(sessionId);
        myPlayerId = "player1";

        Map<String, Object> scores = new HashMap<>();
        scores.put("player1", 0);
        scores.put("player2", 0);

        Map<String, Object> session = new HashMap<>();
        session.put("player1Id", player1Id);
        session.put("player1Name", player1Name);
        session.put("currentGameIndex", 0);
        session.put("phase", "waiting");
        session.put("player1Ready", false);
        session.put("player2Ready", false);
        session.put("scores", scores);

        sessionRef.setValue(session).addOnSuccessListener(v -> callback.onCreated(sessionId));
    }

    public interface OnSessionCreated {
        void onCreated(String sessionId);
    }

    public void joinSession(String sessionId, String player2Id, String player2Name, Runnable callback) {
        this.sessionId = sessionId;
        this.sessionRef = db.child(SESSIONS_PATH).child(sessionId);
        this.myPlayerId = "player2";

        Map<String, Object> updates = new HashMap<>();
        updates.put("player2Id", player2Id);
        updates.put("player2Name", player2Name);
        updates.put("phase", "playing");

        sessionRef.updateChildren(updates).addOnSuccessListener(v -> callback.run());
    }

    public void setReady(boolean ready) {
        sessionRef.child(myPlayerId + "Ready").setValue(ready);
    }

    public void submitGameScore(int myScore) {
        sessionRef.child("scores").child(myPlayerId).runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                int current = currentData.getValue(Integer.class) == null ? 0 : currentData.getValue(Integer.class);
                currentData.setValue(current + myScore);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {}
        });
    }

    public void advanceToNextGame(int nextIndex) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("currentGameIndex", nextIndex);
        updates.put("player1Ready", false);
        updates.put("player2Ready", false);
        updates.put("gameState", null);
        sessionRef.updateChildren(updates);
    }

    public ValueEventListener listenToSession(ValueEventListener listener) {
        sessionRef.addValueEventListener(listener);
        return listener;
    }

    public void removeListener(ValueEventListener listener) {
        sessionRef.removeEventListener(listener);
    }

    public DatabaseReference getGameStateRef() {
        return sessionRef.child("gameState");
    }

    public String getMyPlayerId() { return myPlayerId; }

    public String getOpponentId() {
        return myPlayerId.equals("player1") ? "player2" : "player1";
    }

    public void initSession(String sessionId, String myRole) {
        this.sessionId  = sessionId;
        this.myPlayerId = myRole;
        this.sessionRef = db.child(SESSIONS_PATH).child(sessionId);
    }
}
