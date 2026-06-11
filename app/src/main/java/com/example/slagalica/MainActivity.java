package com.example.slagalica;

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
            navigate(new HomeFragment(), false);
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