package com.example.slagalica.data;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class MatchmakingManager {

    public interface MatchCallback {
        void onMatched(String sessionId, String myRole);
        void onWaiting();
        void onError(String msg);
    }

    private final DatabaseReference mmRef = FirebaseDatabase.getInstance(
            "https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app"
    ).getReference("matchmaking");

    private final DatabaseReference sessionsRef = FirebaseDatabase.getInstance(
            "https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app"
    ).getReference("sessions");

    private ValueEventListener waitListener;
    private String myUid;
    private String myName;

    public void findMatch(String uid, String displayName, MatchCallback callback) {
        this.myUid = uid;
        this.myName = displayName;

        mmRef.orderByChild("timestamp").limitToFirst(1)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            DataSnapshot waiting = snapshot.getChildren().iterator().next();
                            String opponentUid = waiting.getKey();

                            if (opponentUid.equals(myUid)) {
                                waitInQueue(callback);
                                return;
                            }

                            String opponentName = waiting.child("displayName")
                                    .getValue(String.class);

                            mmRef.child(opponentUid).removeValue();

                            String sessionId = sessionsRef.push().getKey();
                            Map<String, Object> scores = new HashMap<>();
                            scores.put("player1", 0);
                            scores.put("player2", 0);

                            Map<String, Object> session = new HashMap<>();
                            session.put("player1Id", opponentUid);
                            session.put("player1Name", opponentName != null ? opponentName : "Igrač 1");
                            session.put("player2Id", myUid);
                            session.put("player2Name", myName);
                            session.put("currentGameIndex", 0);
                            session.put("phase", "playing");
                            session.put("player1Ready", false);
                            session.put("player2Ready", false);
                            session.put("scores", scores);
                            session.put("isFriendly", false);
                            session.put("player1Disconnected", false);
                            session.put("player2Disconnected", false);

                            sessionsRef.child(sessionId).setValue(session)
                                    .addOnSuccessListener(v ->
                                            callback.onMatched(sessionId, "player2"))
                                    .addOnFailureListener(e ->
                                            callback.onError(e.getMessage()));
                        } else {
                            waitInQueue(callback);
                        }
                    }
                    @Override public void onCancelled(DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
    }

    private void waitInQueue(MatchCallback callback) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("uid", myUid);
        entry.put("displayName", myName);
        entry.put("timestamp", System.currentTimeMillis());
        mmRef.child(myUid).setValue(entry);

        callback.onWaiting();

        waitListener = sessionsRef.orderByChild("player1Id").equalTo(myUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        for (DataSnapshot s : snapshot.getChildren()) {
                            String phase = s.child("phase").getValue(String.class);
                            if ("playing".equals(phase)) {
                                cancelSearch();
                                callback.onMatched(s.getKey(), "player1");
                                return;
                            }
                        }
                    }
                    @Override public void onCancelled(DatabaseError error) {}
                });
    }

    public void cancelSearch() {
        if (myUid != null) mmRef.child(myUid).removeValue();
        if (waitListener != null) {
            sessionsRef.removeEventListener(waitListener);
            waitListener = null;
        }
    }
}