package com.example.slagalica.data;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.google.firebase.auth.FirebaseAuth;

public class AppLifecycleObserver implements DefaultLifecycleObserver {

    @Override
    public void onStop(LifecycleOwner owner) {
        FirebaseAuth.getInstance().signOut();
    }
}
