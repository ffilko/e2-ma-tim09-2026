package com.example.slagalica;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import com.example.slagalica.MainActivity;
import com.example.slagalica.R;
import com.example.slagalica.data.ChatManager;
import com.example.slagalica.data.model.ChatContact;

import java.util.ArrayList;
import java.util.List;

public class ChatListFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private final ChatManager chatManager = new ChatManager();
    private final List<ChatContact> contacts = new ArrayList<>();
    private ContactAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_list, container, false);

        recyclerView = view.findViewById(R.id.rvContacts);
        tvEmpty = view.findViewById(R.id.tvEmpty);

        adapter = new ContactAdapter(contacts, contact -> {
            Bundle args = new Bundle();
            args.putString("contactUid", contact.uid);
            args.putString("contactName", contact.username);
            args.putString("region", contact.region);
            args.putString("chatId",
                    chatManager.getChatId(
                            com.google.firebase.auth.FirebaseAuth
                                    .getInstance().getCurrentUser().getUid(),
                            contact.uid));

            ChatFragment chatFragment = new ChatFragment();
            chatFragment.setArguments(args);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigate(chatFragment, true);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        loadContacts();
        return view;
    }

    private void loadContacts() {
        chatManager.loadContacts(list -> {
            contacts.clear();
            contacts.addAll(list);
            contacts.sort((a, b) ->
                    Long.compare(b.lastMessageTime, a.lastMessageTime));

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(contacts.isEmpty() ? View.VISIBLE : View.GONE);
                    recyclerView.setVisibility(contacts.isEmpty() ? View.GONE : View.VISIBLE);
                });
            }
        });
    }

    // --- Adapter ---
    static class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.VH> {
        interface OnClick { void onClick(ChatContact c); }

        private final List<ChatContact> data;
        private final OnClick listener;

        ContactAdapter(List<ChatContact> data, OnClick listener) {
            this.data = data;
            this.listener = listener;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_contact, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ChatContact c = data.get(position);
            holder.tvName.setText(c.username);
            holder.tvRegion.setText(c.region);
            holder.tvLast.setText(c.lastMessage != null ? c.lastMessage : "");
            holder.itemView.setOnClickListener(v -> listener.onClick(c));
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvRegion, tvLast;
            VH(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvContactName);
                tvRegion = v.findViewById(R.id.tvContactRegion);
                tvLast = v.findViewById(R.id.tvLastMessage);
            }
        }
    }
}