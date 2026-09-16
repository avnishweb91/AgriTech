package com.example.smarthub.fragments;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smarthub.R;
import com.example.smarthub.services.MandiPriceService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProfileFragment extends Fragment {
    
    private static final String TAG = "ProfileFragment";
    private static final String PREFS_NAME = "profile_prefs";
    
    // Profile fields
    private EditText etName, etVillage, etLand;
    private AutoCompleteTextView spinnerState, spinnerDistrict;
    private ChipGroup chipGroupCrops;
    private MaterialButton btnSaveProfile, btnEditProfile, btnAddCrop, btnViewCrops, btnLogout;
    private SwitchMaterial switchNotifications, switchLanguage;
    
    // Profile data
    private String farmerName = "";
    private String selectedState = "बिहार (Bihar)";
    private String selectedDistrict = "Patna";
    private String selectedVillage = "";
    private String landSize = "";
    private Set<String> selectedCrops = new HashSet<>();
    
    // Services
    private MandiPriceService mandiPriceService;
    
    // Available options
    private String[] states = {
        "बिहार (Bihar)",
        "उत्तर प्रदेश (UP)",
        "मध्य प्रदेश (MP)",
        "झारखंड (Jharkhand)",
        "पश्चिम बंगाल (West Bengal)",
        "राजस्थान (Rajasthan)",
        "महाराष्ट्र (Maharashtra)",
        "कर्नाटक (Karnataka)",
        "तमिलनाडु (Tamil Nadu)",
        "आंध्र प्रदेश (Andhra Pradesh)"
    };
    
    private String[] allCrops = {
        "गेहूँ (Wheat)",
        "धान (Rice)",
        "मक्का (Maize)",
        "आलू (Potato)",
        "प्याज़ (Onion)",
        "टमाटर (Tomato)",
        "अंगूर (Grapes)",
        "दलहन (Pulses)",
        "तिलहन (Oilseeds)",
        "गन्ना (Sugarcane)",
        "कपास (Cotton)",
        "जूट (Jute)",
        "सरसों (Mustard)",
        "मूंगफली (Groundnut)",
        "सोयाबीन (Soybean)",
        "चना (Chickpea)",
        "मसूर (Lentil)",
        "उड़द (Black Gram)",
        "मूंग (Green Gram)",
        "अरहर (Pigeon Pea)"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        
        initializeViews(view);
        setupMandiService();
        setupSpinners();
        loadProfileData();
        setupClickListeners();
        setEditMode(false); // Start in view mode
        
        return view;
    }
    
    private void initializeViews(View view) {
        // Profile fields
        etName = view.findViewById(R.id.et_farmer_name);
        etVillage = view.findViewById(R.id.et_village);
        etLand = view.findViewById(R.id.et_land_size);
        spinnerState = view.findViewById(R.id.spinner_state);
        spinnerDistrict = view.findViewById(R.id.spinner_district);
        chipGroupCrops = view.findViewById(R.id.chip_group_crops);
        
        // Buttons
        btnSaveProfile = view.findViewById(R.id.btn_save_profile);
        btnEditProfile = view.findViewById(R.id.btn_edit_profile);
        btnAddCrop = view.findViewById(R.id.btn_add_crop);
        btnViewCrops = view.findViewById(R.id.btn_view_crops);
        btnLogout = view.findViewById(R.id.btn_logout);
        
        // Switches
        switchNotifications = view.findViewById(R.id.switch_notifications);
        switchLanguage = view.findViewById(R.id.switch_language);
    }
    
    private void setupMandiService() {
        mandiPriceService = new MandiPriceService(requireContext());
    }
    
    private void setupSpinners() {
        // State spinner
        ArrayAdapter<String> stateAdapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            states
        );
        spinnerState.setAdapter(stateAdapter);
        spinnerState.setText(states[0], false);
        
        // District spinner
        updateDistrictOptions(extractEnglishStateName(states[0]));
        
        // State change listener
        spinnerState.setOnItemClickListener((parent, view1, position, id) -> {
            selectedState = states[position];
            String englishStateName = extractEnglishStateName(selectedState);
            updateDistrictOptions(englishStateName);
            Log.d(TAG, "State selected: " + selectedState + " -> " + englishStateName);
        });
        
        // Focus change listeners
        spinnerState.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) spinnerState.showDropDown();
        });
        
        spinnerDistrict.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) spinnerDistrict.showDropDown();
        });
    }
    
    private String extractEnglishStateName(String fullStateName) {
        if (fullStateName.contains("(") && fullStateName.contains(")")) {
            String englishPart = fullStateName.substring(fullStateName.indexOf("(") + 1, fullStateName.indexOf(")"));
            return englishPart.trim();
        }
        return fullStateName;
    }
    
    private void updateDistrictOptions(String state) {
        if (mandiPriceService == null) return;
        
        try {
            List<String> districts = mandiPriceService.getDistrictsByState(state);
            
            ArrayAdapter<String> districtAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                districts
            );
            
            spinnerDistrict.setAdapter(districtAdapter);
            
            if (!districts.isEmpty()) {
                selectedDistrict = districts.get(0);
                spinnerDistrict.setText(selectedDistrict, false);
            }
            
            Log.d(TAG, "Updated district options for " + state + ": " + districts.size() + " districts");
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating district options: " + e.getMessage(), e);
        }
    }
    
    private void loadProfileData() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, requireContext().MODE_PRIVATE);
        
        farmerName = prefs.getString("farmer_name", "");
        selectedState = prefs.getString("state", states[0]);
        selectedDistrict = prefs.getString("district", "Patna");
        selectedVillage = prefs.getString("village", "");
        landSize = prefs.getString("land_size", "");
        
        // Load selected crops
        Set<String> savedCrops = prefs.getStringSet("selected_crops", new HashSet<>());
        selectedCrops.clear();
        if (savedCrops != null) {
            selectedCrops.addAll(savedCrops);
        }
        
        // Load switches
        boolean notificationsEnabled = prefs.getBoolean("notifications_enabled", true);
        boolean languageHindi = prefs.getBoolean("language_hindi", true);
        
        // Update UI
        updateProfileDisplay();
        switchNotifications.setChecked(notificationsEnabled);
        switchLanguage.setChecked(languageHindi);
    }
    
    private void updateProfileDisplay() {
        etName.setText(farmerName);
        etVillage.setText(selectedVillage);
        etLand.setText(landSize);
        
        // Update spinners
        spinnerState.setText(selectedState, false);
        String englishStateName = extractEnglishStateName(selectedState);
        updateDistrictOptions(englishStateName);
        spinnerDistrict.setText(selectedDistrict, false);
        
        // Update crop chips
        updateCropChips();
    }
    
    private void updateCropChips() {
        chipGroupCrops.removeAllViews();
        
        for (String crop : allCrops) {
            Chip chip = new Chip(requireContext());
            chip.setText(crop);
            chip.setCheckable(true);
            chip.setChecked(selectedCrops.contains(crop));
            
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedCrops.add(crop);
                } else {
                    selectedCrops.remove(crop);
                }
                Log.d(TAG, "Crop selection changed: " + crop + " -> " + isChecked);
            });
            
            chipGroupCrops.addView(chip);
        }
    }
    
    private void setupClickListeners() {
        btnEditProfile.setOnClickListener(v -> setEditMode(true));
        btnSaveProfile.setOnClickListener(v -> saveProfile());
        btnAddCrop.setOnClickListener(v -> showAddCropDialog());
        btnViewCrops.setOnClickListener(v -> showMyCropsDialog());
        btnLogout.setOnClickListener(v -> logout());
        
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String message = isChecked ? "नोटिफिकेशन सक्षम किया गया" : "नोटिफिकेशन अक्षम किया गया";
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            saveSwitchState("notifications_enabled", isChecked);
        });
        
        switchLanguage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String message = isChecked ? "हिंदी भाषा सक्षम" : "अंग्रेजी भाषा सक्षम";
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            saveSwitchState("language_hindi", isChecked);
        });
    }
    
    private void setEditMode(boolean editMode) {
        // Enable/disable editing
        etName.setEnabled(editMode);
        etVillage.setEnabled(editMode);
        etLand.setEnabled(editMode);
        spinnerState.setEnabled(editMode);
        spinnerDistrict.setEnabled(editMode);
        
        // Show/hide buttons
        btnEditProfile.setVisibility(editMode ? View.GONE : View.VISIBLE);
        btnSaveProfile.setVisibility(editMode ? View.VISIBLE : View.GONE);
        
        // Update crop chips editability
        for (int i = 0; i < chipGroupCrops.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupCrops.getChildAt(i);
            chip.setEnabled(editMode);
        }
        
        Log.d(TAG, "Profile edit mode: " + editMode);
    }
    
    private void saveProfile() {
        // Validate inputs
        if (etName.getText().toString().trim().isEmpty()) {
            etName.setError("कृपया अपना नाम दर्ज करें");
            return;
        }
        
        if (etVillage.getText().toString().trim().isEmpty()) {
            etVillage.setError("कृपया गाँव का नाम दर्ज करें");
            return;
        }
        
        if (etLand.getText().toString().trim().isEmpty()) {
            etLand.setError("कृपया जमीन का आकार दर्ज करें");
            return;
        }
        
        if (selectedCrops.isEmpty()) {
            Toast.makeText(requireContext(), "कृपया कम से कम एक फसल चुनें", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Save profile data
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, requireContext().MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putString("farmer_name", etName.getText().toString().trim());
        editor.putString("state", selectedState);
        editor.putString("district", selectedDistrict);
        editor.putString("village", etVillage.getText().toString().trim());
        editor.putString("land_size", etLand.getText().toString().trim());
        editor.putStringSet("selected_crops", selectedCrops);
        
        editor.apply();
        
        // Update local variables
        farmerName = etName.getText().toString().trim();
        selectedVillage = etVillage.getText().toString().trim();
        landSize = etLand.getText().toString().trim();
        
        // Switch to view mode
        setEditMode(false);
        
        Toast.makeText(requireContext(), "प्रोफाइल सफलतापूर्वक सहेजा गया", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Profile saved successfully");
    }
    
    private void saveSwitchState(String key, boolean value) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, requireContext().MODE_PRIVATE);
        prefs.edit().putBoolean(key, value).apply();
    }
    
    private void showAddCropDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("फसल जोड़ें");
        
        // Create custom view for crop selection
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        
        TextView instructionText = new TextView(requireContext());
        instructionText.setText("नई फसल जोड़ने के लिए नीचे दिए गए विकल्पों में से चुनें:");
        instructionText.setTextSize(16);
        instructionText.setPadding(0, 0, 0, 16);
        layout.addView(instructionText);
        
        // Add crop checkboxes
        for (String crop : allCrops) {
            CheckBox checkBox = new CheckBox(requireContext());
            checkBox.setText(crop);
            checkBox.setChecked(selectedCrops.contains(crop));
            checkBox.setPadding(0, 8, 0, 8);
            
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedCrops.add(crop);
                } else {
                    selectedCrops.remove(crop);
                }
            });
            
            layout.addView(checkBox);
        }
        
        scrollView.addView(layout);
        builder.setView(scrollView);
        
        builder.setPositiveButton("जोड़ें", (dialog, which) -> {
            updateCropChips();
            Toast.makeText(requireContext(), selectedCrops.size() + " फसलें चुनी गईं", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("रद्द करें", (dialog, which) -> dialog.dismiss());
        
        builder.show();
    }
    
    private void showMyCropsDialog() {
        if (selectedCrops.isEmpty()) {
            Toast.makeText(requireContext(), "कोई फसल नहीं चुनी गई है", Toast.LENGTH_SHORT).show();
            return;
        }
        
        StringBuilder message = new StringBuilder();
        message.append("आपकी चयनित फसलें:\n\n");
        
        for (String crop : selectedCrops) {
            message.append("🌾 ").append(crop).append("\n");
        }
        
        message.append("\nकुल फसलें: ").append(selectedCrops.size());
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("मेरी फसलें")
               .setMessage(message.toString())
               .setPositiveButton("ठीक है", (dialog, which) -> dialog.dismiss())
               .show();
    }
    
    private void logout() {
        // Clear all profile data
        requireActivity().getSharedPreferences(PREFS_NAME, requireActivity().MODE_PRIVATE)
            .edit()
            .clear()
            .apply();
        
        // Clear login state
        requireActivity().getSharedPreferences("auth_prefs", requireActivity().MODE_PRIVATE)
            .edit()
            .clear()
            .apply();
        
        // Show logout message
        Toast.makeText(requireContext(), "सफलतापूर्वक लॉगआउट हो गया", Toast.LENGTH_SHORT).show();
        
        // Redirect to login
        requireActivity().finish();
        requireActivity().startActivity(new android.content.Intent(requireActivity(), com.example.smarthub.LoginActivity.class));
    }
}
