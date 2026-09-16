package com.example.smarthub;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.appbar.MaterialToolbar;
import com.example.smarthub.api.APIService;
import com.razorpay.PaymentData;
import com.razorpay.PaymentResultWithDataListener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements PaymentResultWithDataListener {

    private static final String TAG = "MainActivity";
    private NavController navController;
    private final APIService apiService = new APIService(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if user is logged in
        if (!isUserLoggedIn()) {
            // User not logged in, redirect to login
            redirectToLogin();
            return;
        }
        
        setContentView(R.layout.activity_main);
        setupNavigation();
    }
    
    private boolean isUserLoggedIn() {
        return getSharedPreferences("auth_prefs", MODE_PRIVATE)
            .getBoolean("is_logged_in", false);
    }
    
    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    private void setupNavigation() {
        try {
            Log.d(TAG, "Setting up navigation...");
            
            // Setup toolbar
            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                setSupportActionBar(toolbar);
                Log.d(TAG, "Toolbar setup completed");
            } else {
                Log.e(TAG, "Toolbar not found");
            }
            
            // Setup bottom navigation
            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            if (bottomNav == null) {
                Log.e(TAG, "Bottom navigation view not found");
                return;
            }
            Log.d(TAG, "Bottom navigation found");
            
            // Check if nav_host_fragment exists
            androidx.fragment.app.FragmentContainerView navHost = findViewById(R.id.nav_host_fragment);
            if (navHost == null) {
                Log.e(TAG, "NavHostFragment not found");
                return;
            }
            Log.d(TAG, "NavHostFragment found");
            
            // Use post to ensure the view is fully created
            bottomNav.post(() -> {
                try {
                    Log.d(TAG, "Attempting to find NavController...");
                    
                    // Try to find NavController
                    NavController foundController = null;
                    try {
                        foundController = Navigation.findNavController(MainActivity.this, R.id.nav_host_fragment);
                    } catch (Exception e) {
                        Log.e(TAG, "Error finding NavController: " + e.getMessage());
                    }
                    
                    if (foundController == null) {
                        Log.e(TAG, "NavController not found, trying alternative method...");
                        // Try alternative method
                        try {
                            foundController = Navigation.findNavController(navHost);
                        } catch (Exception e) {
                            Log.e(TAG, "Alternative method also failed: " + e.getMessage());
                        }
                    }
                    
                    if (foundController == null) {
                        Log.e(TAG, "NavController still not found, trying to create manually...");
                        // Try to create NavController manually
                        try {
                            // This is a fallback - create a simple navigation setup
                            setupSimpleNavigation(bottomNav, toolbar);
                            return;
                        } catch (Exception e) {
                            Log.e(TAG, "Manual navigation setup also failed: " + e.getMessage());
                            return;
                        }
                    }
                    
                    navController = foundController;
                    Log.d(TAG, "NavController found successfully");
                    
                    // Configure the top level destinations
                    AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                        R.id.navigation_home,
                        R.id.navigation_disease,
                        R.id.navigation_prices,
                        R.id.navigation_marketplace,
                        R.id.navigation_cold_storage,
                        R.id.navigation_profile
                    ).build();
                    
                    // Setup the toolbar with navigation
                    if (toolbar != null) {
                        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);
                        Log.d(TAG, "Toolbar navigation setup completed");
                    }
                    
                    // Setup bottom navigation with navigation controller
                    NavigationUI.setupWithNavController(bottomNav, navController);
                    Log.d(TAG, "Bottom navigation setup completed");
                    
                    Log.d(TAG, "Navigation setup completed successfully");
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error setting up navigation: " + e.getMessage(), e);
                    // Fallback to simple navigation
                    setupSimpleNavigation(bottomNav, toolbar);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up navigation: " + e.getMessage(), e);
        }
    }
    
    private void setupSimpleNavigation(BottomNavigationView bottomNav, MaterialToolbar toolbar) {
        try {
            Log.d(TAG, "Setting up simple navigation as fallback...");
            
            // Set up simple click listeners for bottom navigation
            bottomNav.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.navigation_home) {
                    // Load HomeFragment directly
                    getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, new com.example.smarthub.fragments.HomeFragment())
                        .commit();
                    if (toolbar != null) toolbar.setTitle(R.string.nav_home);
                    return true;
                } else if (itemId == R.id.navigation_disease) {
                    getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, new com.example.smarthub.fragments.DiseaseFragment())
                        .commit();
                    if (toolbar != null) toolbar.setTitle(R.string.nav_disease);
                    return true;
                } else if (itemId == R.id.navigation_prices) {
                    getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, new com.example.smarthub.fragments.PricesFragment())
                        .commit();
                    if (toolbar != null) toolbar.setTitle(R.string.nav_prices);
                    return true;
                } else if (itemId == R.id.navigation_marketplace) {
                    getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, new com.example.smarthub.fragments.MarketplaceFragment())
                        .commit();
                    if (toolbar != null) toolbar.setTitle(R.string.nav_marketplace);
                    return true;
                } else if (itemId == R.id.navigation_profile) {
                    getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, new com.example.smarthub.fragments.ProfileFragment())
                        .commit();
                    if (toolbar != null) toolbar.setTitle(R.string.nav_profile);
                    return true;
                } else if (itemId == R.id.navigation_cold_storage) {
                    getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, new com.example.smarthub.fragments.ColdStorageFragment())
                        .commit();
                    if (toolbar != null) toolbar.setTitle(R.string.nav_cold_storage);
                    return true;
                }
                return false;
            });
            
            // Set initial fragment
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, new com.example.smarthub.fragments.HomeFragment())
                .commit();
            if (toolbar != null) toolbar.setTitle(R.string.nav_home);
            
            Log.d(TAG, "Simple navigation setup completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up simple navigation: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (navController != null) {
            return navController.navigateUp() || super.onSupportNavigateUp();
        }
        return super.onSupportNavigateUp();
    }

    @Override public void onPaymentSuccess(String paymentId, PaymentData paymentData) {
        String orderId = paymentData.getOrderId();
        String signature = paymentData.getSignature();
        String token = getSharedPreferences("auth_prefs", MODE_PRIVATE).getString("auth_token", null);
        String expectedOrder = getSharedPreferences("payment_state", MODE_PRIVATE).getString("pending_payment_order", null);
        if (token == null || orderId == null || signature == null || !orderId.equals(expectedOrder)) {
            android.widget.Toast.makeText(this, "भुगतान स्थिति सत्यापित नहीं हो सकी। सहायता से संपर्क करें।", android.widget.Toast.LENGTH_LONG).show(); return;
        }
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> { try {
            retrofit2.Response<APIService.PaymentVerification> response = apiService.backend()
                    .verifyPayment("Bearer " + token, new APIService.PaymentVerificationRequest(orderId, paymentId, signature)).execute();
            runOnUiThread(() -> {
                getSharedPreferences("payment_state", MODE_PRIVATE).edit().remove("pending_payment_order").apply();
                android.widget.Toast.makeText(this, response.isSuccessful() && response.body() != null && response.body().ok
                        ? "भुगतान सफल और सत्यापित हुआ" : "भुगतान की पुष्टि नहीं हुई। डिलीवरी से पहले स्थिति जाँचें।", android.widget.Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) { runOnUiThread(() -> android.widget.Toast.makeText(this, "भुगतान सत्यापन लंबित है। कृपया फिर जाँचें।", android.widget.Toast.LENGTH_LONG).show()); }
        finally { executor.shutdown(); } });
    }

    @Override public void onPaymentError(int code, String response, PaymentData paymentData) {
        getSharedPreferences("payment_state", MODE_PRIVATE).edit().remove("pending_payment_order").apply();
        android.widget.Toast.makeText(this, "भुगतान पूरा नहीं हुआ; आप फिर कोशिश कर सकते हैं।", android.widget.Toast.LENGTH_LONG).show();
    }
}
