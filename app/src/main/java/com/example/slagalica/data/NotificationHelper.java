package com.example.slagalica.data;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.slagalica.MainActivity;
import com.example.slagalica.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class NotificationHelper {
    public static final String CHANNEL_CHAT = "channel_chat";
    public static final String CHANNEL_RANKING = "channel_ranking";
    public static final String CHANNEL_REWARDS = "channel_rewards";
    public static final String CHANNEL_OTHER = "channel_other";

    public static final String CAT_CHAT = "chat";
    public static final String CAT_RANKING = "ranking";
    public static final String CAT_REWARDS = "rewards";
    public static final String CAT_OTHER = "other";

    public static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager m = ctx.getSystemService(NotificationManager.class);
        if (m == null) return;

        m.createNotificationChannel(channel(CHANNEL_CHAT, "Čet",
                "Obaveštenja o porukama u četu"));
        m.createNotificationChannel(channel(CHANNEL_RANKING, "Rangiranje",
                "Obaveštenja o plasmanu na rang listama"));
        m.createNotificationChannel(channel(CHANNEL_REWARDS, "Nagrade",
                "Obaveštenja o osvojenim nagradama"));
        m.createNotificationChannel(channel(CHANNEL_OTHER, "Ostalo",
                "Ostala obaveštenja (pozivi za partiju, lige...)"));
    }

    private static NotificationChannel channel(String id, String name, String desc) {
        NotificationChannel c = new NotificationChannel(
                id, name, NotificationManager.IMPORTANCE_HIGH);
        c.setDescription(desc);
        return c;
    }

    public static void send(Context ctx, String category, String title, String text) {
        saveToHistory(category, title, text);
        showSystemNotification(ctx, category, title, text);
    }

    private static String channelFor(String category) {
        switch (category) {
            case CAT_CHAT: return CHANNEL_CHAT;
            case CAT_RANKING: return CHANNEL_RANKING;
            case CAT_REWARDS: return CHANNEL_REWARDS;
            default: return CHANNEL_OTHER;
        }
    }

    private static void saveToHistory(String category, String title, String text) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Map<String, Object> n = new HashMap<>();
        n.put("category", category);
        n.put("title", title);
        n.put("text", text);
        n.put("timestamp", System.currentTimeMillis());
        n.put("read", false);

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("notifications")
                .add(n);
    }

    private static void showSystemNotification(Context ctx, String category,
                                               String title, String text) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(ctx, MainActivity.class);
        intent.putExtra("open_notifications", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(
                ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, channelFor(category))
                .setSmallIcon(R.drawable.bell)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi);

        NotificationManagerCompat.from(ctx)
                .notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), b.build());
    }
}