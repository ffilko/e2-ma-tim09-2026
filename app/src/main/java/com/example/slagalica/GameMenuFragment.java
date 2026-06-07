package com.example.slagalica;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.slagalica.data.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class GameMenuFragment extends Fragment {

    private SessionManager sessionManager;
    private DatabaseReference queueRef;
    private String myUid;
    private String myName;
    private ValueEventListener waitingListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_game_menu, container, false);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if(user != null) {
            myUid = user.getUid();
            myName = user.getDisplayName() != null ? user.getDisplayName() : "Igrač";
        } else {
            myUid = "anon_" + FirebaseDatabase.getInstance("https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app/").getReference().push().getKey();
            myName = "Anonimus";
        }

        queueRef = FirebaseDatabase.getInstance("https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app/").getReference("matchmaking_queue");
        sessionManager = new SessionManager();

        view.findViewById(R.id.btnStartGame).setOnClickListener(v -> startMatchmaking());

        view.findViewById(R.id.btnBellIcon).setOnClickListener(v ->
                ((MainActivity) requireActivity()).navigate(new NotificationsFragment(), true));
        view.findViewById(R.id.btnProfileIcon).setOnClickListener(v ->
                ((MainActivity) requireActivity()).navigate(new ProfileFragment(), true));

        return view;
    }

    private void startMatchmaking() {
        queueRef.limitToFirst(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if(snapshot.exists()) {
                    DataSnapshot entry = snapshot.getChildren().iterator().next();
                    String waitingUid = entry.getKey();
                    String existingSessionId = entry.child("sessionId").getValue(String.class);

                    queueRef.child(waitingUid).removeValue();
                    sessionManager.joinSession(existingSessionId, myUid, myName, () -> {
                        openGameFragment(existingSessionId, "player2");
                    });
                } else {
                    sessionManager.createSession(myUid, myName, newSessionId -> {
                        Map<String, Object> entry2 = new HashMap<>();
                        entry2.put("sessionId", newSessionId);
                        queueRef.child(myUid).setValue(entry2);

                        waitForOpponent(newSessionId);
                    });
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    private void waitForOpponent(String sessionId) {
        waitingListener = sessionManager.listenToSession(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String phase = snapshot.child("phase").getValue(String.class);
                if ("playing".equals(phase)) {
                    sessionManager.removeListener(waitingListener);
                    openGameFragment(sessionId, "player1");
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    private void openGameFragment(String sessionId, String myRole) {
        Bundle args = new Bundle();
        args.putString("sessionId", sessionId);
        args.putString("myRole", myRole);

        GameFragment gameFragment = new GameFragment();
        gameFragment.setArguments(args);
        ((MainActivity) requireActivity()).navigate(gameFragment, true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (waitingListener != null) sessionManager.removeListener(waitingListener);
        queueRef.child(myUid).removeValue();
    }
}