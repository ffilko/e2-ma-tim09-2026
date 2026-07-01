package com.example.slagalica;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.data.FriendlyMatchManager;
import com.example.slagalica.data.MatchmakingManager;
import com.example.slagalica.data.NotificationHelper;
import com.example.slagalica.data.TokenManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class GameMenuFragment extends Fragment {

    private TokenManager tokenManager;
    private MatchmakingManager matchmakingManager;
    private TextView tvTokens, tvStars, tvSearchStatus;
    private boolean searching = false;
    private View rootView;
    private FriendlyMatchManager friendlyMatchManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_game_menu, container, false);

        tokenManager = new TokenManager();
        matchmakingManager = new MatchmakingManager();
        friendlyMatchManager = new FriendlyMatchManager();

        tvTokens = rootView.findViewById(R.id.tvTokens);
        tvStars = rootView.findViewById(R.id.tvStars);
        tvSearchStatus = rootView.findViewById(R.id.tvSearchStatus);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return rootView;

        String uid = user.getUid();

        tokenManager.checkAndRefillTokens(uid, (ok, msg) ->
                tokenManager.getStats(uid, (tokens, stars) -> {
                    if (tvTokens != null) tvTokens.setText("Tokeni: " + tokens);
                    if (tvStars != null) tvStars.setText("Zvezde: " + stars);
                })
        );

        rootView.findViewById(R.id.btnFindMatch)
                .setOnClickListener(v -> startSearch());
        rootView.findViewById(R.id.btnCancelSearch)
                .setOnClickListener(v -> cancelSearch());
        rootView.findViewById(R.id.btnBellIcon).setOnClickListener(v ->
                ((MainActivity) requireActivity()).navigate(new NotificationsFragment(), true));
        rootView.findViewById(R.id.btnFriends).setOnClickListener(v ->
                ((MainActivity) requireActivity()).navigate(new FriendsFragment(), true));
        rootView.findViewById(R.id.btnChat).setOnClickListener(v ->
                ((MainActivity) requireActivity()).navigate(new ChatListFragment(), true));
        rootView.findViewById(R.id.btnChallenges).setOnClickListener(v ->
                ((MainActivity) requireActivity()).navigate(new ChallengesFragment(), true));
        rootView.findViewById(R.id.btnLeaderboard).setOnClickListener(v ->
                ((MainActivity) requireActivity()).navigate(new LeaderboardFragment(), true));
        rootView.findViewById(R.id.btnMissions).setOnClickListener(v ->
                ((MainActivity) requireActivity()).navigate(new MissionsFragment(), true));
        FriendlyMatchManager friendlyMatchManager = new FriendlyMatchManager();
        FirebaseUser finalUser = user;
        friendlyMatchManager.listenForInvites(uid, (fromUid, fromName, inviteId) -> {
            if (!isAdded()) return;
            new AlertDialog.Builder(requireContext())
                    .setTitle("Poziv za duel")
                    .setMessage(fromName + " te poziva na prijateljski duel!")
                    .setPositiveButton("Prihvati", (dialog, which) -> {
                        String myName2 = finalUser.getDisplayName() != null
                                ? finalUser.getDisplayName()
                                : finalUser.getEmail().split("@")[0];
                        friendlyMatchManager.acceptInvite(uid, myName2,
                                fromUid, fromName, inviteId,
                                new FriendlyMatchManager.InviteCallback() {
                                    @Override
                                    public void onAccepted(String sessionId) {
                                        if (!isAdded()) return;
                                        openGameFragment(sessionId, "player2");
                                    }
                                    @Override public void onDeclined() {}
                                    @Override public void onError(String msg) {}
                                });
                    })
                    .setNegativeButton("Odbij", (dialog, which) ->
                            friendlyMatchManager.declineInvite(uid, inviteId))
                    .setCancelable(false)
                    .show();
        });
        rootView.findViewById(R.id.btnBellIcon).setOnLongClickListener(v -> {
            NotificationHelper.send(requireContext(), NotificationHelper.CAT_CHAT,
                    "Nova poruka", "Ana: Hoćemo partiju večeras?");
            NotificationHelper.send(requireContext(), NotificationHelper.CAT_RANKING,
                    "Rang lista", "Zauzeo/la si 3. mesto na nedeljnoj listi!");
            NotificationHelper.send(requireContext(), NotificationHelper.CAT_REWARDS,
                    "Nagrada", "Osvojio/la si 2 tokena za plasman!");
            NotificationHelper.send(requireContext(), NotificationHelper.CAT_OTHER,
                    "Liga", "Prešao/la si u Srebrnu ligu!");
            return true;
        });
        com.example.slagalica.data.LeaderboardManager leaderboardManager =
                new com.example.slagalica.data.LeaderboardManager();
        leaderboardManager.processCycleRewards(
                requireContext().getApplicationContext(), uid,
                () -> showPendingRewards(uid, leaderboardManager));

        rootView.findViewById(R.id.btnProfileIcon).setOnClickListener(v ->
                ((MainActivity) requireActivity()).navigate(new ProfileFragment(), true));
        rootView.findViewById(R.id.btnLogout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            ((MainActivity) requireActivity()).navigate(new HomeFragment(), false);
        });

        return rootView;
    }

    private void showPendingRewards(String uid,
                                    com.example.slagalica.data.LeaderboardManager lm) {
        if (!isAdded()) return;
        lm.fetchNextRewardPopup(uid, popup -> {
            if (!isAdded() || popup == null) return;
            RewardDialog.show(requireContext(), popup, () ->
                    lm.consumeRewardPopup(uid, popup.docId, () -> showPendingRewards(uid, lm)));
        });
    }

    private void startSearch() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();

        tokenManager.useToken(uid, (ok, msg) -> {
            if (!ok) {
                Toast.makeText(getContext(), "Nemaš tokena!", Toast.LENGTH_SHORT).show();
                return;
            }
            searching = true;
            updateSearchUI(true);

            String name = user.getDisplayName();
            if (name == null || name.trim().isEmpty()) {
                name = user.getEmail() != null ? user.getEmail().split("@")[0] : "Igrač";
            }

            matchmakingManager.findMatch(uid, name, new MatchmakingManager.MatchCallback() {
                @Override
                public void onMatched(String sessionId, String myRole) {
                    if (!isAdded()) return;
                    searching = false;
                    updateSearchUI(false);
                    openGameFragment(sessionId, myRole);
                }
                @Override
                public void onWaiting() {
                    if (tvSearchStatus != null) {
                        tvSearchStatus.setVisibility(View.VISIBLE);
                        tvSearchStatus.setText("Čekamo protivnika...");
                    }
                }
                @Override
                public void onError(String msg) {
                    if (!isAdded()) return;
                    searching = false;
                    updateSearchUI(false);
                    // vrati token
                    FirebaseFirestore.getInstance().collection("users").document(uid)
                            .update("tokens",
                                    com.google.firebase.firestore.FieldValue.increment(1));
                    Toast.makeText(getContext(), "Greška: " + msg, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void cancelSearch() {
        if (!searching) return;
        searching = false;
        matchmakingManager.cancelSearch();
        // vrati token
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .update("tokens",
                            com.google.firebase.firestore.FieldValue.increment(1));
        }
        updateSearchUI(false);
    }

    private void updateSearchUI(boolean isSearching) {
        if (rootView == null) return;
        rootView.findViewById(R.id.btnFindMatch).setEnabled(!isSearching);
        rootView.findViewById(R.id.btnCancelSearch).setVisibility(
                isSearching ? View.VISIBLE : View.GONE);
        if (tvSearchStatus != null) {
            tvSearchStatus.setVisibility(isSearching ? View.VISIBLE : View.INVISIBLE);
            if (!isSearching) tvSearchStatus.setText("");
        }
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
        if (searching) matchmakingManager.cancelSearch();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && friendlyMatchManager != null) {
            friendlyMatchManager.stopListening(user.getUid());
        }
    }
}