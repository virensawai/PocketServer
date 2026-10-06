package com.example.pocketserver.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.example.pocketserver.MainActivity;
import com.example.pocketserver.core.data.CredentialStore;
import com.example.pocketserver.databinding.ActivitySplashBinding;

/**
 * Splash activity presenting application branding and routing
 * authenticated users to the main dashboard or guests to onboarding.
 */
public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        handler.postDelayed(() -> {
            if (isFinishing()) {
                return;
            }
            CredentialStore credentialStore = new CredentialStore(this);
            Intent nextIntent;
            if (credentialStore.isLoggedIn()) {
                nextIntent = new Intent(SplashActivity.this, MainActivity.class);
            } else {
                nextIntent = new Intent(SplashActivity.this, AuthActivity.class);
            }
            startActivity(nextIntent);
            finish();
        }, 800);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        binding = null;
    }
}
