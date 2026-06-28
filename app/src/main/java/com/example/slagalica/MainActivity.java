package com.example.slagalica;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.example.slagalica.data.AppLifecycleObserver;
import com.example.slagalica.data.NotificationHelper;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            FirebaseDatabase.getInstance(
                    "https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app/"
            ).setPersistenceEnabled(false);
        } catch (Exception ignored) {
        }

        ProcessLifecycleOwner.get()
                .getLifecycle()
                .addObserver(new AppLifecycleObserver());

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        NotificationHelper.createChannels(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }

        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    com.google.firebase.auth.FirebaseUser u =
                            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                    if (u != null && token != null) {
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("users").document(u.getUid())
                                .update("fcmToken", token);
                    }
                });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
            );
            return insets;
        });

        if (savedInstanceState == null) {
            if (getIntent().getBooleanExtra("open_notifications", false)) {
                navigate(new NotificationsFragment(), false);
            } else {
                navigate(new HomeFragment(), false);
            }
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("open_notifications", false)) {
            navigate(new NotificationsFragment(), true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        FirebaseDatabase.getInstance(
                "https://slagalica-8871d-default-rtdb.europe-west1.firebasedatabase.app/"
        ).purgeOutstandingWrites();
    }

    public void navigate(Fragment fragment, boolean addToBack) {
        FragmentTransaction tx = getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment);

        if (addToBack) {
            tx.addToBackStack(null);
        }

        tx.commit();
    }
}