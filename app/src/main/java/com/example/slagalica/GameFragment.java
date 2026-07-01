package com.example.slagalica;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.slagalica.data.ChallengeManager;
import com.example.slagalica.data.ChatManager;
import com.example.slagalica.data.SessionManager;
import com.example.slagalica.data.TokenManager;
import com.example.slagalica.data.model.Challenge;
import com.example.slagalica.ui.fragments.games.AsocijacijeFragment;
import com.example.slagalica.ui.fragments.games.KoZnaZnaFragment;
import com.example.slagalica.ui.fragments.games.KorakPoKorakFragment;
import com.example.slagalica.ui.fragments.games.MojBrojFragment;
import com.example.slagalica.ui.fragments.games.SkockoFragment;
import com.example.slagalica.ui.fragments.games.SpojniceFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
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
    private ValueEventListener disconnectListener;
    private DatabaseReference disconnectRef;
    private int localGameIndex = -1;
    private boolean resultSent = false;
    private boolean opponentLeftHandled = false;
    private String challengeId;
    private ChallengeManager challengeManager = new ChallengeManager();
    private int myTotalScore = 0;
    private boolean isChallengeMode;
    private String myUid;
    private ValueEventListener allScoresListener;

    // Referenca na challenges u RTDB — potrebna za checkIfAllPlayersFinished
    private final DatabaseReference challengesRef = FirebaseDatabase.getInstance(
            "https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app"
    ).getReference("challenges");

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        String sessionId = getArguments().getString("sessionId");
        String myRole = getArguments().getString("myRole");
        challengeId = getArguments().getString("challengeId");
        isChallengeMode = (challengeId != null);

        String opponentRole = "player1".equals(myRole) ? "player2" : "player1";

        sessionManager = new SessionManager();
        sessionManager.initSession(sessionId, myRole);

        // U challenge modu nema pravog protivnika — disable disconnect logiku
        if (isChallengeMode) {
            opponentLeftHandled = true;
        }

        view.findViewById(R.id.btnLeaveGame).setOnClickListener(v ->
                showLeaveConfirmDialog());

        if (!isChallengeMode) {
            disconnectRef = sessionManager.getGameStateRef().getParent()
                    .child(opponentRole + "Disconnected");
            disconnectListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) {
                        onOpponentLeft();
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            };
            disconnectRef.addValueEventListener(disconnectListener);
        }

        sessionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer gameIndex = snapshot.child("currentGameIndex").getValue(Integer.class);
                String phase = snapshot.child("phase").getValue(String.class);

                if (gameIndex == null) return;

                if ("playing".equals(phase) && gameIndex != localGameIndex) {
                    localGameIndex = gameIndex;
                    opponentLeftHandled = isChallengeMode; // u challenge modu uvek true
                    loadGameFragment(gameIndex);
                } else if ("finished".equals(phase)) {
                    if (isChallengeMode) {
                        // U challenge modu finished znači da je igrač završio sve igre
                        // Ništa ne radimo ovde — ChallengeGameFragment to obrađuje
                    } else {
                        applyMatchResults(snapshot);
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        sessionManager.listenToSession(sessionListener);

        getChildFragmentManager().setFragmentResultListener(
                "game_finished", getViewLifecycleOwner(),
                (key, bundle) -> {
                    int gameScore = bundle.getInt("myScore", 0);
                    myTotalScore += gameScore;

                    sessionManager.submitGameScore(gameScore);

                    if (isChallengeMode) {
                        challengeManager.submitScore(challengeId, myUid, myTotalScore);

                        int next = localGameIndex + 1;
                        if (next < games.length) {
                             sessionManager.advanceToNextGame(next);
                        } else {
                            sessionManager.getGameStateRef().getParent()
                                    .child("phase").setValue("finished");
                            checkIfAllPlayersFinished();
                        }
                    } else {
                        sessionManager.setReady(true);
                        waitForOpponentAndAdvance();
                    }
                }
        );
    }

    private void checkIfAllPlayersFinished() {
        challengeManager.getChallenge(challengeId, challenge -> {
            if (challenge == null) return;
            int expected = challenge.participants.size();

            if (allScoresListener != null) {
                challengesRef.child(challengeId).child("scores")
                        .removeEventListener(allScoresListener);
            }

            allScoresListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.getChildrenCount() >= expected) {
                        snapshot.getRef().removeEventListener(this);
                        allScoresListener = null;
                        showChallengeResults(challenge);
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            };

            challengesRef.child(challengeId).child("scores")
                    .addValueEventListener(allScoresListener);
        });
    }

    private void showChallengeResults(Challenge staleChallenge) {
        if (!isAdded() || getActivity() == null) return;
        challengeManager.getChallenge(staleChallenge.id, freshChallenge -> {
            if (freshChallenge == null) freshChallenge = staleChallenge;
            final Challenge ch = freshChallenge;

            boolean iAmDistributor = ch.participants.keySet().stream()
                    .min(String::compareTo)
                    .map(uid -> uid.equals(myUid))
                    .orElse(false);
            if (iAmDistributor) {
                challengeManager.distributeRewards(ch);
            }

            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                cleanup();
                Bundle args = new Bundle();
                args.putString("challengeId", ch.id);
                ChallengeResultFragment resultFragment = new ChallengeResultFragment();
                resultFragment.setArguments(args);
                ((MainActivity) requireActivity()).navigate(resultFragment, false);
            });
        });
    }

    private void showLeaveConfirmDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Napusti partiju")
                .setMessage("Da li sigurno želiš da napustiš? Izgubićeš partiju.")
                .setPositiveButton("Napusti", (dialog, which) -> leaveGame())
                .setNegativeButton("Ostani", null)
                .show();
    }

    private void leaveGame() {
        if (resultSent) return;
        resultSent = true;

        if (isChallengeMode) {
            // U challenge modu, napuštanje znači score = 0
            challengeManager.submitScore(challengeId, myUid, 0);
            cleanup();
            if (isAdded() && getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigate(new GameMenuFragment(), false);
            }
            return;
        }

        sessionManager.markDisconnected();

        sessionManager.listenToSession(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                sessionManager.removeListener(this);

                boolean isFriendly = Boolean.TRUE.equals(
                        snapshot.child("isFriendly").getValue(Boolean.class));

                if (!isFriendly) {
                    new TokenManager().applyMatchResult(myUid, false, 0, (ok, msg) -> {});
                }

                cleanup();
                if (isAdded() && getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigate(new GameMenuFragment(), false);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                cleanup();
                if (isAdded() && getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigate(new GameMenuFragment(), false);
                }
            }
        });
    }

    private void onOpponentLeft() {
        if (!isAdded() || opponentLeftHandled) return;
        opponentLeftHandled = true;

        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (!isAdded()) return;

            Fragment currentGame = getChildFragmentManager()
                    .findFragmentById(R.id.gameFragmentContainer);
            if (currentGame != null && currentGame.getView() != null) {
                String myRole = getArguments().getString("myRole");
                int opponentNameId = "player1".equals(myRole)
                        ? R.id.tvPlayer2Name
                        : R.id.tvPlayer1Name;
                View nameView = currentGame.getView().findViewById(opponentNameId);
                if (nameView instanceof android.widget.TextView) {
                    ((android.widget.TextView) nameView)
                            .setTextColor(android.graphics.Color.RED);
                }
            }

            sessionManager.setReady(true);

            if (getView() != null) {
                getView().postDelayed(() -> {
                    if (!isAdded()) return;
                    int next = localGameIndex + 1;
                    if (next < 6) {
                        sessionManager.advanceToNextGame(next);
                    } else {
                        sessionManager.getGameStateRef().getParent()
                                .child("phase").setValue("finished");
                    }
                }, 1500);
            }
        });
    }

    private void loadGameFragment(int index) {
        if (index >= games.length) return;
        try {
            Bundle args = new Bundle();
            args.putString("sessionId", getArguments().getString("sessionId"));
            args.putString("myRole", getArguments().getString("myRole"));
            args.putBoolean("isChallengeMode", isChallengeMode);
            String roundSeed = getArguments().getString("roundSeed");
            if (roundSeed != null) args.putString("roundSeed", roundSeed);

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
        cleanup();
        if (isAdded() && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigate(new GameMenuFragment(), false);
        }
    }

    private void applyMatchResults(DataSnapshot snapshot) {
        if (resultSent) return;
        resultSent = true;

        String myRole = getArguments().getString("myRole");

        boolean isFriendly = Boolean.TRUE.equals(
                snapshot.child("isFriendly").getValue(Boolean.class));

        if (isFriendly) {
            showResults(snapshot);
            return;
        }

        Integer myScore = "player1".equals(myRole)
                ? snapshot.child("scores/player1").getValue(Integer.class)
                : snapshot.child("scores/player2").getValue(Integer.class);
        Integer oppScore = "player1".equals(myRole)
                ? snapshot.child("scores/player2").getValue(Integer.class)
                : snapshot.child("scores/player1").getValue(Integer.class);

        int my = myScore != null ? myScore : 0;
        int opp = oppScore != null ? oppScore : 0;
        boolean won = my > opp;

        String opponentUid = "player1".equals(myRole)
                ? snapshot.child("player2Id").getValue(String.class)
                : snapshot.child("player1Id").getValue(String.class);

        if (opponentUid != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(myUid).get()
                    .addOnSuccessListener(myDoc -> {
                        String myRegion = myDoc.getString("region");
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("users").document(opponentUid).get()
                                .addOnSuccessListener(oppDoc -> {
                                    String oppRegion = oppDoc.getString("region");
                                    if (myRegion != null && myRegion.equals(oppRegion)) {
                                        new ChatManager().unlockChat(myUid, opponentUid, myRegion);
                                    }
                                });
                    });
        }

        new com.example.slagalica.data.LeaderboardManager().recordMatch(myUid, won, my);

        new TokenManager().applyMatchResult(myUid, won, my, (ok, msg) ->
                showResults(snapshot));
    }

    private void cleanup() {
        if (sessionListener != null) {
            sessionManager.removeListener(sessionListener);
            sessionListener = null;
        }
        if (disconnectListener != null && disconnectRef != null) {
            disconnectRef.removeEventListener(disconnectListener);
            disconnectListener = null;
        }
        if (allScoresListener != null && challengeId != null) {
            challengesRef.child(challengeId).child("scores")
                    .removeEventListener(allScoresListener);
            allScoresListener = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cleanup();
    }

    private void waitForOpponentAndAdvance() {
        sessionManager.listenToSession(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (opponentLeftHandled) {
                    sessionManager.removeListener(this);
                    return;
                }

                String myRole = getArguments().getString("myRole");
                String oppRole = "player1".equals(myRole) ? "player2" : "player1";

                boolean myReady = Boolean.TRUE.equals(
                        snapshot.child(myRole + "Ready").getValue(Boolean.class));
                boolean oppReady = Boolean.TRUE.equals(
                        snapshot.child(oppRole + "Ready").getValue(Boolean.class));

                if (myReady && oppReady) {
                    sessionManager.removeListener(this);
                    int next = localGameIndex + 1;
                    if ("player1".equals(sessionManager.getMyPlayerId())) {
                        if (next < 6) {
                            sessionManager.advanceToNextGame(next);
                        } else {
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
}