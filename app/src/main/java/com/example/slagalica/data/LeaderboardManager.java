package com.example.slagalica.data;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.slagalica.data.model.LeaderboardEntry;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LeaderboardManager {

    public static final String COL_WEEKLY = "leaderboard_weekly";
    public static final String COL_MONTHLY = "leaderboard_monthly";

    public static final String TYPE_WEEKLY = "weekly";
    public static final String TYPE_MONTHLY = "monthly";

    private static final int REWARD_LIMIT = 10;
    private static final int DISPLAY_LIMIT = 100;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface ListCallback { void onResult(List<LeaderboardEntry> entries); }
    public interface PopupCallback { void onResult(RewardPopup popup); }
    public interface SimpleCallback { void onDone(); }

    public void recordMatch(String uid, boolean won, int score) {
        final long delta = (won ? 10 : -10) + (score / 40);

        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            String username = doc.getString("username");
            if (username == null) username = "Igrač";
            Long lg = doc.getLong("league");
            int league = lg != null ? lg.intValue() : 0;

            updateCycleDoc(COL_WEEKLY, CycleUtils.currentWeekKey(), uid, username, league, delta);
            updateCycleDoc(COL_MONTHLY, CycleUtils.currentMonthKey(), uid, username, league, delta);
        });
    }

    private void updateCycleDoc(String collection, String cycleKey, String uid,
                                String username, int league, long delta) {
        DocumentReference ref = db.collection(collection).document(cycleKey)
                .collection("players").document(uid);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            long stars = 0, games = 0;
            if (snap.exists()) {
                Long s = snap.getLong("stars");
                Long g = snap.getLong("games");
                if (s != null) stars = s;
                if (g != null) games = g;
            }
            long newStars = stars + delta;
            if (newStars < 0) newStars = 0;

            Map<String, Object> data = new HashMap<>();
            data.put("username", username);
            data.put("league", league);
            data.put("stars", newStars);
            data.put("games", games + 1);
            data.put("updatedAt", Timestamp.now());
            transaction.set(ref, data, SetOptions.merge());
            return null;
        });
    }

    public void getWeekly(ListCallback callback) {
        getCycle(COL_WEEKLY, CycleUtils.currentWeekKey(), callback);
    }

    public void getMonthly(ListCallback callback) {
        getCycle(COL_MONTHLY, CycleUtils.currentMonthKey(), callback);
    }

    private void getCycle(String collection, String cycleKey, ListCallback callback) {
        db.collection(collection).document(cycleKey).collection("players")
                .orderBy("stars", Query.Direction.DESCENDING)
                .limit(DISPLAY_LIMIT)
                .get()
                .addOnSuccessListener(qs -> {
                    List<LeaderboardEntry> list = new ArrayList<>();
                    int rank = 1;
                    for (QueryDocumentSnapshot d : qs) {
                        Long stars = d.getLong("stars");
                        Long games = d.getLong("games");
                        Long lg = d.getLong("league");
                        LeaderboardEntry e = new LeaderboardEntry(
                                d.getId(),
                                d.getString("username") != null ? d.getString("username") : "Igrač",
                                lg != null ? lg.intValue() : 0,
                                stars != null ? stars : 0,
                                games != null ? games : 0);
                        e.rank = rank++;
                        list.add(e);
                    }
                    callback.onResult(list);
                })
                .addOnFailureListener(e -> callback.onResult(new ArrayList<>()));
    }

    public void processCycleRewards(Context context, String uid, SimpleCallback onComplete) {
        db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
            String lastWeekly = userDoc.getString("lastWeeklyRewardCycle");
            String lastMonthly = userDoc.getString("lastMonthlyRewardCycle");

            String prevWeek = CycleUtils.previousWeekKey();
            String prevMonth = CycleUtils.previousMonthKey();

            final int[] pending = {0};
            final Runnable done = () -> {
                if (--pending[0] <= 0 && onComplete != null) onComplete.onDone();
            };

            if (!prevWeek.equals(lastWeekly)) pending[0]++;
            if (!prevMonth.equals(lastMonthly)) pending[0]++;
            if (pending[0] == 0) { if (onComplete != null) onComplete.onDone(); return; }

            if (!prevWeek.equals(lastWeekly)) {
                processOne(context, uid, COL_WEEKLY, prevWeek, "lastWeeklyRewardCycle",
                        TYPE_WEEKLY, CycleUtils.weekRangeLabel(prevWeek), "nedeljnoj", done);
            }
            if (!prevMonth.equals(lastMonthly)) {
                processOne(context, uid, COL_MONTHLY, prevMonth, "lastMonthlyRewardCycle",
                        TYPE_MONTHLY, CycleUtils.monthRangeLabel(prevMonth), "mesečnoj", done);
            }
        }).addOnFailureListener(e -> { if (onComplete != null) onComplete.onDone(); });
    }

    private void processOne(Context context, String uid, String collection, String prevKey,
                            String markerField, String type, String cycleLabel,
                            String listName, Runnable done) {
        db.collection(collection).document(prevKey).collection("players")
                .orderBy("stars", Query.Direction.DESCENDING)
                .limit(REWARD_LIMIT)
                .get()
                .addOnSuccessListener(qs -> {
                    int rank = 0, i = 0;
                    for (QueryDocumentSnapshot d : qs) {
                        i++;
                        if (d.getId().equals(uid)) { rank = i; break; }
                    }

                    db.collection("users").document(uid).update(markerField, prevKey);

                    if (rank >= 1 && rank <= REWARD_LIMIT) {
                        int tokens = tokensForRank(type, rank);
                        db.collection("users").document(uid)
                                .update("tokens", FieldValue.increment(tokens));

                        String title = "Nagrada za rang listu";
                        String message = "Osvojio/la si " + rank + ". mesto na " + listName
                                + " rang listi (" + cycleLabel + ") i " + tokens + " "
                                + (tokens == 1 ? "token" : "tokena") + "! ⭐";

                        NotificationHelper.send(context, NotificationHelper.CAT_REWARDS,
                                title, message);

                        Map<String, Object> popup = new HashMap<>();
                        popup.put("type", type);
                        popup.put("rank", rank);
                        popup.put("tokens", tokens);
                        popup.put("cycleLabel", cycleLabel);
                        popup.put("title", title);
                        popup.put("message", message);
                        popup.put("timestamp", System.currentTimeMillis());
                        db.collection("users").document(uid)
                                .collection("rewardPopups").add(popup);
                    }
                    done.run();
                })
                .addOnFailureListener(e -> {
                    db.collection("users").document(uid).update(markerField, prevKey);
                    done.run();
                });
    }

    private int tokensForRank(String type, int rank) {
        boolean monthly = TYPE_MONTHLY.equals(type);
        switch (rank) {
            case 1: return monthly ? 10 : 5;
            case 2: return monthly ? 6 : 3;
            case 3: return monthly ? 4 : 2;
            default: return monthly ? 2 : 1;
        }
    }

    public static class RewardPopup {
        public String docId;
        public String type;
        public int rank;
        public int tokens;
        public String cycleLabel;
        public String title;
        public String message;
    }

    public void fetchNextRewardPopup(String uid, PopupCallback callback) {
        db.collection("users").document(uid).collection("rewardPopups")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs.isEmpty()) { callback.onResult(null); return; }
                    DocumentSnapshot d = qs.getDocuments().get(0);
                    RewardPopup p = new RewardPopup();
                    p.docId = d.getId();
                    p.type = d.getString("type");
                    p.rank = d.getLong("rank") != null ? d.getLong("rank").intValue() : 0;
                    p.tokens = d.getLong("tokens") != null ? d.getLong("tokens").intValue() : 0;
                    p.cycleLabel = d.getString("cycleLabel");
                    p.title = d.getString("title");
                    p.message = d.getString("message");
                    callback.onResult(p);
                })
                .addOnFailureListener(e -> callback.onResult(null));
    }

    public void consumeRewardPopup(String uid, String docId, @NonNull SimpleCallback done) {
        db.collection("users").document(uid).collection("rewardPopups").document(docId)
                .delete()
                .addOnCompleteListener(t -> done.onDone());
    }
}
