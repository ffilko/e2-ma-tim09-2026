package com.example.slagalica.data;

import android.content.Context;
import android.util.Log;

import com.google.auth.oauth2.GoogleCredentials;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.Executors;

public class FcmSender {

    private static final String FCM_URL =
            "https://fcm.googleapis.com/v1/projects/slagalica-8871d/messages:send";
    private static final String SCOPE =
            "https://www.googleapis.com/auth/firebase.messaging";

    public static void sendChatNotification(Context context,
                                            String toToken,
                                            String senderName,
                                            String text) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String accessToken = getAccessToken(context);

                JSONObject data = new JSONObject();
                data.put("category", NotificationHelper.CAT_CHAT);
                data.put("title", "Nova poruka od " + senderName);
                data.put("text", text);

                JSONObject androidConfig = new JSONObject();
                androidConfig.put("priority", "high");

                JSONObject msgObj = new JSONObject();
                msgObj.put("token", toToken);
                //msgObj.put("notification", notification);
                msgObj.put("data", data);
                msgObj.put("android", androidConfig);

                JSONObject payload = new JSONObject();
                payload.put("message", msgObj);

                URL url = new URL(FCM_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes());
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                Log.d("FCM", "HTTP response: " + code);
                conn.disconnect();

            } catch (Exception e) {
                Log.e("FCM", "Greška pri slanju notifikacije", e);
            }
        });
    }

    private static String getAccessToken(Context context) throws Exception {
        InputStream is = context.getAssets().open("service_account.json");
        GoogleCredentials credentials = GoogleCredentials
                .fromStream(is)
                .createScoped(Collections.singletonList(SCOPE));
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }
}