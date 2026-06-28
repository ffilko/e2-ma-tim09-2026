package com.example.slagalica.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class FriendlyMatchManager {

    public interface InviteCallback {
        void onAccepted(String sessionId);
        void onDeclined();
        void onError(String msg);
    }

    public interface IncomingInviteCallback {
        void onInvite(String fromUid, String fromName, String inviteId);
    }

    private final DatabaseReference invitesRef = FirebaseDatabase.getInstance(
            "https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app"
    ).getReference("invites");

    private final DatabaseReference sessionsRef = FirebaseDatabase.getInstance(
            "https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app"
    ).getReference("sessions");

    private ValueEventListener inviteListener;
    private ValueEventListener responseListener;
    private String myUid;

    // Pošiljalac poziva — postaje player1 u sesiji
    public void sendInvite(String myUid, String myName,
                           String friendUid, InviteCallback callback) {
        this.myUid = myUid;

        FirebaseFirestore.getInstance()
                .collection("users").document(friendUid)
                .collection("notifications")
                .add(new HashMap<String, Object>() {{
                    put("category", "other");
                    put("title", "Poziv za duel");
                    put("text", myName + " te poziva na prijateljski duel! Otvori igru da prihvatiš.");
                    put("timestamp", System.currentTimeMillis());
                    put("read", false);
                }});

        String inviteId = invitesRef.push().getKey();
        Map<String, Object> invite = new HashMap<>();
        invite.put("fromUid", myUid);
        invite.put("fromName", myName);
        invite.put("toUid", friendUid);
        invite.put("status", "pending");
        invite.put("timestamp", System.currentTimeMillis());

        invitesRef.child(friendUid).child(inviteId).setValue(invite)
                .addOnSuccessListener(v -> {
                    /*NotificationHelper.sendToUser(
                            friendUid,
                            "Poziv za duel",
                            myName + " te poziva na prijateljski duel!"
                    );*/
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));


        DatabaseReference inviteRef = invitesRef.child(friendUid).child(inviteId);
        responseListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                String status = snapshot.child("status").getValue(String.class);
                if ("accepted".equals(status)) {
                    String sessionId = snapshot.child("sessionId").getValue(String.class);
                    inviteRef.removeEventListener(this);
                    responseListener = null;
                    inviteRef.removeValue();
                    callback.onAccepted(sessionId);
                } else if ("declined".equals(status)) {
                    inviteRef.removeEventListener(this);
                    responseListener = null;
                    inviteRef.removeValue();
                    callback.onDeclined();
                }
            }
            @Override public void onCancelled(DatabaseError error) {
                callback.onError(error.getMessage());
            }
        };
        inviteRef.addValueEventListener(responseListener);
    }

    public void listenForInvites(String myUid, IncomingInviteCallback callback) {
        this.myUid = myUid;
        inviteListener = invitesRef.child(myUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        for (DataSnapshot invite : snapshot.getChildren()) {
                            String status = invite.child("status").getValue(String.class);
                            if ("pending".equals(status)) {
                                String fromUid = invite.child("fromUid").getValue(String.class);
                                String fromName = invite.child("fromName").getValue(String.class);
                                String inviteId = invite.getKey();

                                invite.getRef().child("status").setValue("shown");

                                callback.onInvite(fromUid,
                                        fromName != null ? fromName : "Igrač",
                                        inviteId);
                                return;
                            }
                        }
                    }
                    @Override public void onCancelled(DatabaseError error) {}
                });
    }

    // Prihvati poziv — primalac postaje player2, pošiljalac (fromUid) je player1
    public void acceptInvite(String myUid, String myName,
                             String fromUid, String fromName,
                             String inviteId, InviteCallback callback) {
        String sessionId = sessionsRef.push().getKey();
        Map<String, Object> scores = new HashMap<>();
        scores.put("player1", 0);
        scores.put("player2", 0);

        Map<String, Object> session = new HashMap<>();
        // fromUid (pošiljalac) = player1, myUid (primalac) = player2
        session.put("player1Id", fromUid);
        session.put("player1Name", fromName);
        session.put("player2Id", myUid);
        session.put("player2Name", myName);
        session.put("currentGameIndex", 0);
        session.put("phase", "playing");
        session.put("player1Ready", false);
        session.put("player2Ready", false);
        session.put("scores", scores);
        session.put("isFriendly", true);
        session.put("player1Disconnected", false);
        session.put("player2Disconnected", false);

        sessionsRef.child(sessionId).setValue(session)
                .addOnSuccessListener(v -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("status", "accepted");
                    update.put("sessionId", sessionId);
                    // Poziv se čuva kod primaoca (myUid)
                    invitesRef.child(myUid).child(inviteId).updateChildren(update)
                            .addOnSuccessListener(v2 -> {
                                // Primalac ulazi kao player2
                                callback.onAccepted(sessionId);
                            })
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Odbij poziv
    public void declineInvite(String myUid, String inviteId) {
        invitesRef.child(myUid).child(inviteId).child("status").setValue("declined");
    }

    public void stopListening(String myUid) {
        if (inviteListener != null) {
            invitesRef.child(myUid).removeEventListener(inviteListener);
            inviteListener = null;
        }
        if (responseListener != null) {
            responseListener = null;
        }
    }
}