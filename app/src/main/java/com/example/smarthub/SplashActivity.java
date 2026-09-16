package com.example.smarthub;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.example.smarthub.auth.AuthenticationService;

public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_splash);
        
        Log.d(TAG, "SplashActivity onCreate started");
        
        // Check if user is already logged in
        if (isUserLoggedIn()) {
            // User is logged in, go directly to main app
            Log.d(TAG, "User already logged in, going to main app");
            startMainApp();
            return;
        }
        
        // Show welcome screen for 1 second, then show login button
        new Handler().postDelayed(() -> {
            Log.d(TAG, "Showing welcome content");
            showWelcomeContent();
        }, 1000);
        
        // Setup login button
        setupStartButton();
    }
    
    private void setupStartButton() {
        try {
            Button startButton = findViewById(R.id.btn_start);
            if (startButton != null) {
                Log.d(TAG, "Start button found, setting up click listener");
                startButton.setOnClickListener(v -> {
                    Log.d(TAG, "Start button clicked!");
                    Toast.makeText(this, "शुरू कर रहे हैं...", Toast.LENGTH_SHORT).show();
                    
                    // Add delay to show the Toast message
                    new Handler().postDelayed(() -> {
                        startLoginActivity();
                    }, 1000);
                });
            } else {
                Log.e(TAG, "Start button not found in layout!");
                Toast.makeText(this, "बटन नहीं मिला!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up start button: " + e.getMessage(), e);
            Toast.makeText(this, "बटन सेटअप में त्रुटि: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void showWelcomeContent() {
        try {
            // Show the welcome content
            View welcomeContent = findViewById(R.id.welcome_content);
            View loadingIndicator = findViewById(R.id.loading_indicator);
            
            if (welcomeContent != null && loadingIndicator != null) {
                welcomeContent.setVisibility(View.VISIBLE);
                loadingIndicator.setVisibility(View.GONE);
                Log.d(TAG, "Welcome content shown successfully");
            } else {
                Log.e(TAG, "Welcome content or loading indicator not found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing welcome content: " + e.getMessage(), e);
        }
    }
    
    private boolean isUserLoggedIn() {
        return new AuthenticationService(this).isUserLoggedIn();
    }
    
    private void startMainApp() {
        try {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error starting MainActivity: " + e.getMessage(), e);
        }
    }

    private void startLoginActivity() {
        try {
            Log.d(TAG, "Attempting to start LoginActivity...");
            
            // Check if LoginActivity exists
            Class<?> loginActivityClass = Class.forName("com.example.smarthub.LoginActivity");
            Log.d(TAG, "LoginActivity class found: " + loginActivityClass.getName());
            
            Intent intent = new Intent(SplashActivity.this, loginActivityClass);
            Log.d(TAG, "Intent created successfully");
            
            startActivity(intent);
            Log.d(TAG, "LoginActivity started successfully");
            
            // Only finish after successful start
            finish();
            
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "LoginActivity class not found: " + e.getMessage(), e);
            Toast.makeText(this, "त्रुटि: LoginActivity नहीं मिला", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error starting LoginActivity: " + e.getMessage(), e);
            Toast.makeText(this, "त्रुटि: " + e.getMessage(), Toast.LENGTH_LONG).show();
            
            // Don't finish the activity, let user try again
            Log.d(TAG, "Keeping SplashActivity alive for retry");
        }
    }
}
