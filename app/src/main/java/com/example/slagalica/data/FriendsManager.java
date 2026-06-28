package com.example.slagalica.data;

import com.example.slagalica.data.model.Friend;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FriendsManager {

    public interface FriendsCallback {
        void onResult(List<Friend> friends);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void getFriends(String myUid, FriendsCallback callback) {
        db.collection("users").document(myUid)
                .collection("friends")
                .get()
                .addOnSuccessListener(query -> {
                    List<Friend> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        String uid = doc.getId();
                        String username = doc.getString("username");
                        list.add(new Friend(uid, username != null ? username : "Igrač"));
                    }
                    callback.onResult(list);
                })
                .addOnFailureListener(e -> callback.onResult(new ArrayList<>()));
    }
}