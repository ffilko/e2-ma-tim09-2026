package com.example.slagalica;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import com.example.slagalica.R;
import com.example.slagalica.data.ChatManager;
import com.example.slagalica.data.FcmSender;
import com.example.slagalica.data.model.ChatMessage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.*;

public class ChatFragment extends Fragment {

    private RecyclerView recyclerView;
    private EditText etMessage;
    private MessageAdapter adapter;
    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatManager chatManager;
    private ChildEventListener messageListener;

    private String chatId, region, contactUid, contactName;
    private String myUid, myUsername;
    private com.google.firebase.database.DatabaseReference chatMessagesRef;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        chatId      = getArguments().getString("chatId");
        region      = getArguments().getString("region");
        contactUid  = getArguments().getString("contactUid");
        contactName = getArguments().getString("contactName");
        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        chatManager = new ChatManager();

        // Učitaj username iz Firestore
        FirebaseFirestore.getInstance().collection("users").document(myUid).get()
                .addOnSuccessListener(doc -> myUsername = doc.getString("username"));

        TextView tvTitle = view.findViewById(R.id.tvChatTitle);
        tvTitle.setText(contactName);

        recyclerView = view.findViewById(R.id.rvMessages);
        etMessage    = view.findViewById(R.id.etMessage);

        adapter = new MessageAdapter(messages, myUid);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        view.findViewById(R.id.btnSend).setOnClickListener(v -> sendMessage());

        // Referenca na poruke
        String regionKey = region.replaceAll("[^a-zA-Z0-9]", "_");
        com.google.firebase.database.DatabaseReference messagesRef =
                com.google.firebase.database.FirebaseDatabase
                        .getInstance("https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app/")
                        .getReference()
                        .child("chats").child(regionKey).child(chatId).child("messages");

// Korak 1: učitaj istoriju jednom
        messagesRef.get().addOnSuccessListener(snapshot -> {
            messages.clear();
            long[] lastTs = {0};
            for (com.google.firebase.database.DataSnapshot child : snapshot.getChildren()) {
                com.example.slagalica.data.model.ChatMessage msg =
                        child.getValue(com.example.slagalica.data.model.ChatMessage.class);
                if (msg != null) {
                    messages.add(msg);
                    if (msg.timestamp > lastTs[0]) lastTs[0] = msg.timestamp;
                }
            }
            adapter.notifyDataSetChanged();
            if (!messages.isEmpty())
                recyclerView.scrollToPosition(messages.size() - 1);

            // Korak 2: slušaj samo poruke novije od poslednje
            messageListener = new com.google.firebase.database.ChildEventListener() {
                @Override
                public void onChildAdded(
                        @NonNull com.google.firebase.database.DataSnapshot snap,
                        String prev) {
                    com.example.slagalica.data.model.ChatMessage msg =
                            snap.getValue(com.example.slagalica.data.model.ChatMessage.class);
                    if (msg != null && msg.timestamp > lastTs[0]) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                messages.add(msg);
                                adapter.notifyItemInserted(messages.size() - 1);
                                recyclerView.scrollToPosition(messages.size() - 1);
                            });
                        }
                    }
                }
                @Override public void onChildChanged(
                        @NonNull com.google.firebase.database.DataSnapshot s, String p) {}
                @Override public void onChildRemoved(
                        @NonNull com.google.firebase.database.DataSnapshot s) {}
                @Override public void onChildMoved(
                        @NonNull com.google.firebase.database.DataSnapshot s, String p) {}
                @Override public void onCancelled(
                        @NonNull com.google.firebase.database.DatabaseError e) {}
            };
            messagesRef.addChildEventListener(messageListener);
            // Sačuvaj ref za cleanup
            chatMessagesRef = messagesRef;
        });

        return view;
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty() || myUsername == null) return;
        chatManager.sendMessage(region, chatId, text, myUsername);
        etMessage.setText("");

        new com.example.slagalica.data.MissionManager()
                .complete(myUid, com.example.slagalica.data.MissionManager.MISSION_CHAT);

        // Pošalji FCM notifikaciju kontaktu (ako nije u appu)
        sendPushToContact(text);
    }

    private void sendPushToContact(String text) {
        FirebaseFirestore.getInstance().collection("users").document(contactUid).get()
                .addOnSuccessListener(doc -> {
                    String token = doc.getString("fcmToken");
                    if (token != null && !token.isEmpty()) {
                        // Pozovi Cloud Function ili direktno FCM HTTP v1 API
                        // Ovde koristimo jednostavan helper
                        // Dodaj context u poziv
                        FcmSender.sendChatNotification(requireContext(), token, myUsername, text);
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (messageListener != null && chatMessagesRef != null) {
            chatMessagesRef.removeEventListener(messageListener);
        }
    }

    // --- Adapter ---
    static class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {
        private final List<ChatMessage> data;
        private final String myUid;
        private final SimpleDateFormat sdf =
                new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

        MessageAdapter(List<ChatMessage> data, String myUid) {
            this.data = data;
            this.myUid = myUid;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == 1
                    ? R.layout.item_message_sent
                    : R.layout.item_message_received;
            View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ChatMessage msg = data.get(position);
            holder.tvText.setText(msg.text);
            holder.tvSender.setText(msg.senderName);
            holder.tvTime.setText(sdf.format(new Date(msg.timestamp)));
        }

        @Override public int getItemViewType(int pos) {
            return data.get(pos).senderId.equals(myUid) ? 1 : 0;
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvText, tvSender, tvTime;
            VH(View v) {
                super(v);
                tvText   = v.findViewById(R.id.tvMessageText);
                tvSender = v.findViewById(R.id.tvSenderName);
                tvTime   = v.findViewById(R.id.tvMessageTime);
            }
        }
    }
}