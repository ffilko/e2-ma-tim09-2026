package com.example.slagalica.data;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MissionManager {

    public static final String MISSION_WIN = "win";
    public static final String MISSION_CHAT = "chat";
    public static final String MISSION_FRIENDLY = "friendly";
    public static final String MISSION_TOURNAMENT = "tournament";

    private static final int STARS_PER_MISSION = 3;
    private static final int BONUS_STARS = 3;
    private static final int BONUS_TOKENS = 2;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface StatusCallback { void onResult(Status status); }
    public interface ResultCallback { void onResult(int starsGained, int tokensGained, boolean allDone); }

    public static class Status {
        public boolean win, chat, friendly, tournament, bonusGranted;

        public int completedCount() {
            int n = 0;
            if (win) n++;
            if (chat) n++;
            if (friendly) n++;
            if (tournament) n++;
            return n;
        }
    }

    private static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", new Locale("sr", "RS")).format(new Date());
    }

    private static boolean bool(DocumentSnapshot s, String field) {
        Boolean b = s.getBoolean(field);
        return b != null && b;
    }

    public void complete(String uid, String mission) {
        complete(uid, mission, null);
    }

    public void complete(String uid, String mission, ResultCallback callback) {
        DocumentReference ref = db.collection("users").document(uid)
                .collection("dailyMissions").document(todayKey());

        db.runTransaction((Transaction.Function<int[]>) tr -> {
            DocumentSnapshot s = tr.get(ref);

            boolean win = bool(s, MISSION_WIN);
            boolean chat = bool(s, MISSION_CHAT);
            boolean friendly = bool(s, MISSION_FRIENDLY);
            boolean tournament = bool(s, MISSION_TOURNAMENT);
            boolean bonus = bool(s, "bonusGranted");

            boolean alreadyDone = bool(s, mission);

            if (MISSION_WIN.equals(mission)) win = true;
            else if (MISSION_CHAT.equals(mission)) chat = true;
            else if (MISSION_FRIENDLY.equals(mission)) friendly = true;
            else if (MISSION_TOURNAMENT.equals(mission)) tournament = true;

            boolean allDone = win && chat && friendly && tournament;
            boolean grantBonus = allDone && !bonus;

            if (alreadyDone && !grantBonus) {
                return new int[]{0, 0, allDone ? 1 : 0};
            }

            int stars = 0, tokens = 0;
            Map<String, Object> data = new HashMap<>();
            data.put(mission, true);

            if (!alreadyDone) stars += STARS_PER_MISSION;
            if (grantBonus) {
                stars += BONUS_STARS;
                tokens += BONUS_TOKENS;
                data.put("bonusGranted", true);
            }

            tr.set(ref, data, SetOptions.merge());
            return new int[]{stars, tokens, allDone ? 1 : 0};
        }).addOnSuccessListener(res -> {
            if (res == null) {
                if (callback != null) callback.onResult(0, 0, false);
                return;
            }
            if (res[0] > 0 || res[1] > 0) {
                db.collection("users").document(uid).update(
                        "stars", FieldValue.increment(res[0]),
                        "tokens", FieldValue.increment(res[1]));
            }
            if (callback != null) callback.onResult(res[0], res[1], res[2] == 1);
        }).addOnFailureListener(e -> {
            if (callback != null) callback.onResult(0, 0, false);
        });
    }

    public void getStatus(String uid, @NonNull StatusCallback callback) {
        db.collection("users").document(uid)
                .collection("dailyMissions").document(todayKey())
                .get()
                .addOnSuccessListener(s -> {
                    Status st = new Status();
                    if (s.exists()) {
                        st.win = bool(s, MISSION_WIN);
                        st.chat = bool(s, MISSION_CHAT);
                        st.friendly = bool(s, MISSION_FRIENDLY);
                        st.tournament = bool(s, MISSION_TOURNAMENT);
                        st.bonusGranted = bool(s, "bonusGranted");
                    }
                    callback.onResult(st);
                })
                .addOnFailureListener(e -> callback.onResult(new Status()));
    }
}
