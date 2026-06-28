package com.example.slagalica.data;

import androidx.annotation.NonNull;

import com.example.slagalica.data.model.ChatContact;
import com.example.slagalica.data.model.ChatMessage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChatManager {

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final DatabaseReference rtdb = FirebaseDatabase
            .getInstance("https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app/")
            .getReference();
    private final String myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

    // Generiše konzistentan chatId od dva uid-a (uvek isti redosled)
    public String getChatId(String uid1, String uid2) {
        String[] ids = {uid1, uid2};
        Arrays.sort(ids);
        return ids[0] + "_" + ids[1];
    }

    // Poziva se na kraju partije — otključava chat između dva igrača
    public void unlockChat(String myUid, String opponentUid, String region) {
        android.util.Log.d("CHAT_UNLOCK", "unlockChat called: " + myUid
                + " <-> " + opponentUid + " region=" + region);

        String chatId = getChatId(myUid, opponentUid);

        // Oba igrača dobijaju kontakt jedni od drugima
        firestore.collection("users").document(myUid)
                .collection("chatContacts").document(opponentUid)
                .set(new java.util.HashMap<String, Object>() {{
                    put("chatId", chatId);
                    put("region", region);
                    put("unlockedAt", System.currentTimeMillis());
                }});

        firestore.collection("users").document(opponentUid)
                .collection("chatContacts").document(myUid)
                .set(new java.util.HashMap<String, Object>() {{
                    put("chatId", chatId);
                    put("region", region);
                    put("unlockedAt", System.currentTimeMillis());
                }});

        // Kreiraj chat sobu u RTDB
        String regionKey = region.replaceAll("[^a-zA-Z0-9]", "_");
        rtdb.child("chats").child(regionKey).child(chatId)
                .child("members").child(myUid).setValue(true);
        rtdb.child("chats").child(regionKey).child(chatId)
                .child("members").child(opponentUid).setValue(true);
    }

    // Učitaj listu kontakata za prikaz u ChatListFragment
    public void loadContacts(ContactsCallback callback) {
        firestore.collection("users").document(myUid)
                .collection("chatContacts")
                .get()
                .addOnSuccessListener(query -> {
                    List<ChatContact> contacts = new ArrayList<>();
                    int total = query.size();
                    if (total == 0) { callback.onLoaded(contacts); return; }

                    final int[] count = {0};
                    for (var doc : query.getDocuments()) {
                        String uid = doc.getId();
                        String chatId = doc.getString("chatId");
                        String region = doc.getString("region");

                        firestore.collection("users").document(uid).get()
                                .addOnSuccessListener(userDoc -> {
                                    String username = userDoc.getString("username");
                                    ChatContact c = new ChatContact(uid, username, region);
                                    c.lastMessageTime = doc.getLong("unlockedAt") != null
                                            ? doc.getLong("unlockedAt") : 0;
                                    contacts.add(c);
                                    count[0]++;
                                    if (count[0] == total) callback.onLoaded(contacts);
                                });
                    }
                });
    }

    // Slanje poruke
    public void sendMessage(String region, String chatId, String text, String senderName) {
        String regionKey = region.replaceAll("[^a-zA-Z0-9]", "_");
        DatabaseReference msgRef = rtdb.child("chats").child(regionKey)
                .child(chatId).child("messages").push();

        ChatMessage msg = new ChatMessage(myUid, senderName, text, System.currentTimeMillis());
        msgRef.setValue(msg);

        // Ažuriraj lastMessage kod oba korisnika u Firestore
        String[] uids = chatId.split("_");
        for (String uid : uids) {
            firestore.collection("users").document(uid)
                    .collection("chatContacts")
                    .document(uid.equals(myUid)
                            ? (uids[0].equals(myUid) ? uids[1] : uids[0])
                            : myUid)
                    .update("lastMessage", text,
                            "lastMessageTime", System.currentTimeMillis());
        }
    }

    // Učitaj postojeće poruke jednom, pa slušaj samo nove
    public ChildEventListener listenToMessages(String region, String chatId,
                                               MessageCallback onHistory,
                                               MessageCallback onNew) {
        String regionKey = region.replaceAll("[^a-zA-Z0-9]", "_");
        DatabaseReference ref = rtdb.child("chats").child(regionKey)
                .child(chatId).child("messages");

        // Prvo učitaj sve postojeće
        ref.get().addOnSuccessListener(snapshot -> {
            List<ChatMessage> history = new ArrayList<>();
            for (DataSnapshot child : snapshot.getChildren()) {
                ChatMessage msg = child.getValue(ChatMessage.class);
                if (msg != null) history.add(msg);
            }
            onHistory.onNewMessage(null); // signal za clear
            for (ChatMessage msg : history) onHistory.onNewMessage(msg);

            // Zatim slušaj samo poruke novije od poslednje
            long lastTimestamp = history.isEmpty()
                    ? System.currentTimeMillis()
                    : history.get(history.size() - 1).timestamp;

            ChildEventListener listener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot snapshot,
                                         String previousChildName) {
                    ChatMessage msg = snapshot.getValue(ChatMessage.class);
                    if (msg != null && msg.timestamp > lastTimestamp) {
                        onNew.onNewMessage(msg);
                    }
                }
                @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
                @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
                @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            };
            ref.addChildEventListener(listener);
        });

        // Vraćamo dummy listener za cleanup — pravi listener se registruje async
        // Bolje rešenje: čuvaj ref i listener posebno
        return null; // vidi napomenu ispod
    }

    public void removeMessageListener(String region, String chatId,
                                      ChildEventListener listener) {
        String regionKey = region.replaceAll("[^a-zA-Z0-9]", "_");
        rtdb.child("chats").child(regionKey).child(chatId)
                .child("messages").removeEventListener(listener);
    }

    public interface ContactsCallback {
        void onLoaded(List<ChatContact> contacts);
    }

    public interface MessageCallback {
        void onNewMessage(ChatMessage message);
    }
}