package com.example.smarthub.fragments;

import android.os.Bundle;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.example.smarthub.R;
import com.example.smarthub.fragments.DiseaseFragment;
import com.example.smarthub.fragments.PricesFragment;
import com.example.smarthub.fragments.MarketplaceFragment;
import com.example.smarthub.fragments.ProfileFragment;
import com.example.smarthub.fragments.WeatherFragment;
import com.example.smarthub.fragments.SchemesFragment;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.example.smarthub.services.VoiceCommandService;
import java.util.ArrayList;

public class HomeFragment extends Fragment {
    
    private static final String TAG = "HomeFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "HomeFragment onViewCreated - setting up click listeners");
        initializeViews();
        setupClickListeners();
        MaterialButton voiceButton = view.findViewById(R.id.btn_voice_command);
        voiceButton.setOnClickListener(v -> startVoiceCommand());
    }

    private void startVoiceCommand() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "बोलिए: आज का भाव, मौसम, बाजार या कोल्ड स्टोरेज");
        try { startActivityForResult(intent, 7001); }
        catch (Exception e) { Toast.makeText(requireContext(), "इस फोन में आवाज़ पहचान उपलब्ध नहीं है", Toast.LENGTH_LONG).show(); }
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 7001 || data == null) return;
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return;
        VoiceCommandService.Intent command = VoiceCommandService.parse(results.get(0));
        switch (command) {
            case PRICES: navigateToFragment(new PricesFragment(), "PricesFragment"); break;
            case MARKETPLACE: navigateToFragment(new MarketplaceFragment(), "MarketplaceFragment"); break;
            case WEATHER: navigateToFragment(new WeatherFragment(), "WeatherFragment"); break;
            case COLD_STORAGE: Toast.makeText(requireContext(), "कोल्ड स्टोरेज ट्रैकिंग जल्द उपलब्ध होगी", Toast.LENGTH_LONG).show(); break;
            default: Toast.makeText(requireContext(), "समझ नहीं आया: " + results.get(0), Toast.LENGTH_LONG).show();
        }
    }

    private void initializeViews() {
        Log.d(TAG, "HomeFragment initializeViews called");
        // Views are already initialized in the layout
    }

    private void setupClickListeners() {
        Log.d(TAG, "HomeFragment setupClickListeners called");
        
        try {
            // AI Disease Detection
            MaterialCardView cardDisease = getView().findViewById(R.id.card_disease);
            if (cardDisease != null) {
                Log.d(TAG, "Setting up disease card click listener");
                cardDisease.setOnClickListener(v -> {
                    Log.d(TAG, "Disease card clicked!");
                    navigateToFragment(new DiseaseFragment(), "DiseaseFragment");
                });
            } else {
                Log.e(TAG, "card_disease not found in layout!");
            }

            // Mandi Prices
            MaterialCardView cardPrices = getView().findViewById(R.id.card_prices);
            if (cardPrices != null) {
                Log.d(TAG, "Setting up prices card click listener");
                cardPrices.setOnClickListener(v -> {
                    Log.d(TAG, "Prices card clicked!");
                    navigateToFragment(new PricesFragment(), "PricesFragment");
                });
            } else {
                Log.e(TAG, "card_prices not found in layout!");
            }

            // Marketplace
            MaterialCardView cardMarketplace = getView().findViewById(R.id.card_marketplace);
            if (cardMarketplace != null) {
                Log.d(TAG, "Setting up marketplace card click listener");
                cardMarketplace.setOnClickListener(v -> {
                    Log.d(TAG, "Marketplace card clicked!");
                    navigateToFragment(new MarketplaceFragment(), "MarketplaceFragment");
                });
            } else {
                Log.e(TAG, "card_marketplace not found in layout!");
            }

            // Weather & Alerts
            MaterialCardView cardWeather = getView().findViewById(R.id.card_weather);
            if (cardWeather != null) {
                Log.d(TAG, "Setting up weather card click listener");
                cardWeather.setOnClickListener(v -> {
                    Log.d(TAG, "Weather card clicked!");
                    navigateToFragment(new WeatherFragment(), "WeatherFragment");
                });
            } else {
                Log.e(TAG, "card_weather not found in layout!");
            }

            // Government Schemes
            MaterialCardView cardSchemes = getView().findViewById(R.id.card_schemes);
            if (cardSchemes != null) {
                Log.d(TAG, "Setting up schemes card click listener");
                cardSchemes.setOnClickListener(v -> {
                    Log.d(TAG, "Schemes card clicked!");
                    navigateToFragment(new SchemesFragment(), "SchemesFragment");
                });
            } else {
                Log.e(TAG, "card_schemes not found in layout!");
            }

            // Profile
            MaterialCardView cardProfile = getView().findViewById(R.id.card_profile);
            if (cardProfile != null) {
                Log.d(TAG, "Setting up profile card click listener");
                cardProfile.setOnClickListener(v -> {
                    Log.d(TAG, "Profile card clicked!");
                    navigateToFragment(new ProfileFragment(), "ProfileFragment");
                });
            } else {
                Log.e(TAG, "card_profile not found in layout!");
            }
            
            Log.d(TAG, "All click listeners set up successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up click listeners: " + e.getMessage(), e);
            Toast.makeText(getContext(), "क्लिक लिसनर सेटअप में त्रुटि: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void navigateToFragment(Fragment fragment, String fragmentName) {
        try {
            Log.d(TAG, "Attempting to navigate to " + fragmentName);
            
            if (getActivity() != null) {
                FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                FragmentTransaction transaction = fragmentManager.beginTransaction();
                
                // Replace the current fragment
                transaction.replace(R.id.nav_host_fragment, fragment);
                
                // Add to back stack so user can go back
                transaction.addToBackStack(fragmentName);
                
                // Commit the transaction
                transaction.commit();
                
                Log.d(TAG, "Successfully navigated to " + fragmentName);
                Toast.makeText(getContext(), fragmentName + " खोल रहा है...", Toast.LENGTH_SHORT).show();
                
            } else {
                Log.e(TAG, "Activity is null, cannot navigate");
                Toast.makeText(getContext(), "नेविगेशन में त्रुटि: Activity null", Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to " + fragmentName + ": " + e.getMessage(), e);
            Toast.makeText(getContext(), "नेविगेशन में त्रुटि: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
