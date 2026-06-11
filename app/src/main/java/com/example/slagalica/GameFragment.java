package com.example.slagalica;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.slagalica.data.SessionManager;
import com.example.slagalica.ui.fragments.games.AsocijacijeFragment;
import com.example.slagalica.ui.fragments.games.KoZnaZnaFragment;
import com.example.slagalica.ui.fragments.games.KorakPoKorakFragment;
import com.example.slagalica.ui.fragments.games.MojBrojFragment;
import com.example.slagalica.ui.fragments.games.SkockoFragment;
import com.example.slagalica.ui.fragments.games.SpojniceFragment;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

public class GameFragment extends Fragment {

    private final Class<? extends Fragment>[] games = new Class[]{
            KoZnaZnaFragment.class,
            SpojniceFragment.class,
            AsocijacijeFragment.class,
            SkockoFragment.class,
            KorakPoKorakFragment.class,
            MojBrojFragment.class
    };
    private SessionManager sessionManager;
    private ValueEventListener sessionListener;
    private ValueEventListener readyListener;
    private int localGameIndex = -1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String sessionId = getArguments().getString("sessionId");
        String myRole    = getArguments().getString("myRole");

        sessionManager = new SessionManager();
        sessionManager.initSession(sessionId, myRole);

        sessionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer gameIndex = snapshot.child("currentGameIndex").getValue(Integer.class);
                String phase = snapshot.child("phase").getValue(String.class);

                if (gameIndex == null) return;

                if ("playing".equals(phase) && gameIndex != localGameIndex) {
                    localGameIndex = gameIndex;
                    loadGameFragment(gameIndex);
                } else if ("finished".equals(phase)) {
                    showResults(snapshot);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        sessionManager.listenToSession(sessionListener);

        getChildFragmentManager().setFragmentResultListener(
                "game_finished", getViewLifecycleOwner(),
                (key, bundle) -> {
                    int myScore = bundle.getInt("myScore", 0);
                    sessionManager.submitGameScore(myScore);
                    sessionManager.setReady(true);
                    waitForOpponentAndAdvance();
                }
        );
    }

    private void loadGameFragment(int index) {
        if(index >= games.length) return;

        try {
            Bundle args = new Bundle();
            args.putString("sessionId", getArguments().getString("sessionId"));
            args.putString("myRole",    getArguments().getString("myRole"));

            Fragment gameFragment = games[index].newInstance();
            gameFragment.setArguments(args);

            FragmentTransaction tx = getChildFragmentManager().beginTransaction();
            if (index > 0) {
                tx.setCustomAnimations(android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right);
            }
            tx.replace(R.id.gameFragmentContainer, gameFragment).commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showResults(DataSnapshot snapshot) {
        if (sessionListener != null) sessionManager.removeListener(sessionListener);
        if (readyListener   != null) sessionManager.removeListener(readyListener);

        Integer s1 = snapshot.child("scores/player1").getValue(Integer.class);
        Integer s2 = snapshot.child("scores/player2").getValue(Integer.class);
        int score1 = s1 != null ? s1 : 0;
        int score2 = s2 != null ? s2 : 0;

        Bundle args = new Bundle();
        args.putInt("score1", score1);
        args.putInt("score2", score2);
        args.putString("player1Name", snapshot.child("player1Name").getValue(String.class));
        args.putString("player2Name", snapshot.child("player2Name").getValue(String.class));

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigate(new GameMenuFragment(), false);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sessionListener != null) sessionManager.removeListener(sessionListener);
        if (readyListener   != null) sessionManager.removeListener(readyListener);
    }

    private void waitForOpponentAndAdvance() {
        sessionManager.listenToSession(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                boolean p1 = Boolean.TRUE.equals(
                        snapshot.child("player1Ready").getValue(Boolean.class));
                boolean p2 = Boolean.TRUE.equals(
                        snapshot.child("player2Ready").getValue(Boolean.class));

                if (p1 && p2) {
                    sessionManager.removeListener(this);
                    int next = localGameIndex + 1;
                    if (next < 6) {
                        if ("player1".equals(sessionManager.getMyPlayerId())) {
                            sessionManager.advanceToNextGame(next);
                        }
                    } else {
                        if ("player1".equals(sessionManager.getMyPlayerId())) {
                            sessionManager.getGameStateRef().getParent()
                                    .child("phase").setValue("finished");
                        }
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError e) {}
        });
    }

    private void loadChildFragment(Fragment fragment, boolean animate) {
        FragmentTransaction tx = getChildFragmentManager().beginTransaction();
        if (animate) {
            tx.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        }
        tx.replace(R.id.gameFragmentContainer, fragment).commit();
    }
}