package com.example.slagalica.data;

import android.app.ActivityManager;
import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.List;

public class MySlagalicaMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (remoteMessage.getData().isEmpty()) return;

        String category = remoteMessage.getData().getOrDefault("category",
                NotificationHelper.CAT_CHAT);
        String title = remoteMessage.getData().getOrDefault("title", "Nova poruka");
        String text  = remoteMessage.getData().getOrDefault("text", "");

        NotificationHelper.showPublicNotification(this, category, title, text);
    }

    @Override
    public void onNewToken(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .update("fcmToken", token);
    }

    private boolean isAppInForeground() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return false;
        for (ActivityManager.RunningAppProcessInfo p : procs) {
            if (p.processName.equals(getPackageName())
                    && p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true;
            }
        }
        return false;
    }
}