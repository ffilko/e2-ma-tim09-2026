package com.example.slagalica;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalica.data.FriendlyMatchManager;
import com.example.slagalica.data.FriendsManager;
import com.example.slagalica.data.model.Friend;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

public class FriendsFragment extends Fragment {

    private FriendsManager friendsManager;
    private FriendlyMatchManager friendlyMatchManager;
    private List<Friend> friendList = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private List<String> displayNames = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Koristimo jednostavan ListView layout
        ListView listView = new ListView(requireContext());
        listView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        friendsManager = new FriendsManager();
        friendlyMatchManager = new FriendlyMatchManager();

        adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, displayNames);
        listView.setAdapter(adapter);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return listView;

        friendsManager.getFriends(user.getUid(), friends -> {
            friendList = friends;
            displayNames.clear();
            for (Friend f : friends) {
                displayNames.add(f.username);
            }
            adapter.notifyDataSetChanged();
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Friend friend = friendList.get(position);
            showInviteDialog(friend, user);
        });

        return listView;
    }

    private void showInviteDialog(Friend friend, FirebaseUser user) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Prijateljski duel")
                .setMessage("Pošalji poziv igraču " + friend.username + "?")
                .setPositiveButton("Pošalji", (dialog, which) -> sendInvite(friend, user))
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void sendInvite(Friend friend, FirebaseUser user) {
        String myName = user.getDisplayName();
        if (myName == null || myName.isEmpty()) myName = user.getEmail().split("@")[0];

        Toast.makeText(getContext(), "Poziv poslat...", Toast.LENGTH_SHORT).show();

        friendlyMatchManager.sendInvite(user.getUid(), myName, friend.uid,
                new FriendlyMatchManager.InviteCallback() {
                    @Override
                    public void onAccepted(String sessionId) {
                        if (!isAdded()) return;
                        openGame(sessionId, "player1");
                    }
                    @Override
                    public void onDeclined() {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(), "Prijatelj je odbio poziv.",
                                Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onError(String msg) {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(), "Greška: " + msg,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void openGame(String sessionId, String myRole) {
        Bundle args = new Bundle();
        args.putString("sessionId", sessionId);
        args.putString("myRole", myRole);
        GameFragment gameFragment = new GameFragment();
        gameFragment.setArguments(args);
        ((MainActivity) requireActivity()).navigate(gameFragment, true);
    }
}