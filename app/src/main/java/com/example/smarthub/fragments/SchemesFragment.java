package com.example.smarthub.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smarthub.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class SchemesFragment extends Fragment {
    
    private static final String TAG = "SchemesFragment";
    
    private AutoCompleteTextView cropSpinner, landSpinner, stateSpinner;
    private MaterialButton btnApplyKcc, btnApplyPmfby, btnApplyDrip;
    private LinearLayout schemesContainer;
    private TextView tvSelectedInfo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_schemes, container, false);
        
        initializeViews(view);
        setupSpinners();
        setupClickListeners();
        
        return view;
    }

    private void initializeViews(View view) {
        cropSpinner = view.findViewById(R.id.crop_spinner);
        landSpinner = view.findViewById(R.id.land_spinner);
        stateSpinner = view.findViewById(R.id.state_spinner);
        btnApplyKcc = view.findViewById(R.id.btn_apply_kcc);
        btnApplyPmfby = btnApplyPmfby = view.findViewById(R.id.btn_apply_pmfby);
        btnApplyDrip = view.findViewById(R.id.btn_apply_drip);
        schemesContainer = view.findViewById(R.id.schemes_container);
        tvSelectedInfo = view.findViewById(R.id.tv_selected_info);
    }

    private void setupSpinners() {
        // Crop spinner
        String[] crops = {
            getString(R.string.wheat),
            getString(R.string.rice),
            getString(R.string.maize),
            getString(R.string.potato),
            getString(R.string.onion),
            getString(R.string.tomato),
            "धान (Paddy)",
            "मक्का (Maize)",
            "दलहन (Pulses)",
            "तिलहन (Oilseeds)"
        };

        ArrayAdapter<String> cropAdapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            crops
        );

        cropSpinner.setAdapter(cropAdapter);
        cropSpinner.setText(crops[0], false);

        // Land size spinner
        String[] landSizes = {
            getString(R.string.less_than_one),
            getString(R.string.one_to_three),
            getString(R.string.more_than_three),
            "0.5 एकड़ से कम",
            "5-10 एकड़",
            "10 एकड़ से अधिक"
        };

        ArrayAdapter<String> landAdapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            landSizes
        );

        landAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
        landSpinner.setAdapter(landAdapter);
        landSpinner.setText(landSizes[0], false);

        // State spinner
        String[] states = {
            "बिहार (Bihar)",
            "उत्तर प्रदेश (UP)",
            "मध्य प्रदेश (MP)",
            "झारखंड (Jharkhand)",
            "पश्चिम बंगाल (West Bengal)",
            "अन्य (Other)"
        };

        ArrayAdapter<String> stateAdapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            states
        );

        stateAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
        stateSpinner.setAdapter(stateAdapter);
        stateSpinner.setText(states[0], false);

        // Enable spinners for user selection
        cropSpinner.setEnabled(true);
        landSpinner.setEnabled(true);
        stateSpinner.setEnabled(true);

        // Set up change listeners to update schemes
        cropSpinner.setOnItemClickListener((parent, view1, position, id) -> {
            Log.d(TAG, "Crop selected: " + crops[position]);
            updateSelectedInfo();
            displaySchemes();
        });

        landSpinner.setOnItemClickListener((parent, view1, position, id) -> {
            Log.d(TAG, "Land size selected: " + landSizes[position]);
            updateSelectedInfo();
            displaySchemes();
        });

        stateSpinner.setOnItemClickListener((parent, view1, position, id) -> {
            Log.d(TAG, "State selected: " + states[position]);
            updateSelectedInfo();
            displaySchemes();
        });

        // Also add text change listeners for better compatibility
        cropSpinner.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                cropSpinner.showDropDown();
            }
        });

        landSpinner.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                landSpinner.showDropDown();
            }
        });

        stateSpinner.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                stateSpinner.showDropDown();
            }
        });

        // Initial display
        updateSelectedInfo();
        displaySchemes();
    }

    private void updateSelectedInfo() {
        String crop = cropSpinner.getText().toString();
        String land = landSpinner.getText().toString();
        String state = stateSpinner.getText().toString();
        
        String info = String.format("फसल: %s | भूमि: %s | राज्य: %s", crop, land, state);
        if (tvSelectedInfo != null) {
            tvSelectedInfo.setText(info);
        }
        
        Log.d(TAG, "Selected info updated: " + info);
    }

    private void displaySchemes() {
        if (schemesContainer == null) return;
        
        schemesContainer.removeAllViews();
        
        String crop = cropSpinner.getText().toString();
        String land = landSpinner.getText().toString();
        String state = stateSpinner.getText().toString();
        
        Log.d(TAG, "Displaying schemes for: " + crop + ", " + land + ", " + state);
        
        // Add Central Government Schemes
        addSchemeCard("🏛️ केंद्र सरकार की योजनाएं", "राष्ट्रीय स्तर पर उपलब्ध", getCentralSchemes(crop, land));
        
        // Add State Government Schemes
        if (state.contains("बिहार")) {
            addSchemeCard("🌾 बिहार सरकार की योजनाएं", "बिहार राज्य के लिए विशेष", getBiharSchemes(crop, land));
        } else {
            addSchemeCard("🏛️ राज्य सरकार की योजनाएं", state + " के लिए उपलब्ध", getStateSchemes(crop, land, state));
        }
        
        // Add Crop-specific schemes
        addSchemeCard("🌱 फसल विशेष योजनाएं", crop + " के लिए विशेष", getCropSpecificSchemes(crop, land));
        
        // Add Quick Application Buttons
        addQuickApplicationButtons();
    }
    
    private void addQuickApplicationButtons() {
        if (schemesContainer == null) return;
        
        // Quick Application Section
        MaterialCardView quickCard = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 16);
        quickCard.setLayoutParams(cardParams);
        quickCard.setRadius(12);
        quickCard.setCardElevation(4);
        quickCard.setCardBackgroundColor(getResources().getColor(R.color.primary, null));

        LinearLayout cardContent = new LinearLayout(requireContext());
        cardContent.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(16, 16, 16, 16);

        // Title
        TextView titleView = new TextView(requireContext());
        titleView.setText("🚀 तुरंत आवेदन करें");
        titleView.setTextSize(18);
        titleView.setTextColor(getResources().getColor(R.color.white, null));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        cardContent.addView(titleView);

        // Subtitle
        TextView subtitleView = new TextView(requireContext());
        subtitleView.setText("मुख्य योजनाओं के लिए सीधे आवेदन करें");
        subtitleView.setTextSize(14);
        subtitleView.setTextColor(getResources().getColor(R.color.white, null));
        subtitleView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        subtitleView.setPadding(0, 8, 0, 16);
        cardContent.addView(subtitleView);

        // Buttons Grid
        LinearLayout buttonGrid = new LinearLayout(requireContext());
        buttonGrid.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        buttonGrid.setOrientation(LinearLayout.VERTICAL);

        // PM-KISAN Button
        MaterialButton pmKisanBtn = new MaterialButton(requireContext());
        pmKisanBtn.setText("PM-KISAN आवेदन");
        pmKisanBtn.setTextSize(14);
        LinearLayout.LayoutParams pmKisanParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pmKisanParams.setMargins(0, 0, 0, 8);
        pmKisanBtn.setLayoutParams(pmKisanParams);
        pmKisanBtn.setPadding(16, 12, 16, 12);
        pmKisanBtn.setBackgroundColor(getResources().getColor(R.color.success, null));
        pmKisanBtn.setOnClickListener(v -> openSchemeApplication("PM-KISAN", "https://pmkisan.gov.in"));
        buttonGrid.addView(pmKisanBtn);

        // PMFBY Button
        MaterialButton pmfbyBtn = new MaterialButton(requireContext());
        pmfbyBtn.setText("फसल बीमा आवेदन");
        pmfbyBtn.setTextSize(14);
        LinearLayout.LayoutParams pmfbyParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pmfbyParams.setMargins(0, 0, 0, 8);
        pmfbyBtn.setLayoutParams(pmfbyParams);
        pmfbyBtn.setPadding(16, 12, 16, 12);
        pmfbyBtn.setBackgroundColor(getResources().getColor(R.color.info, null));
        pmfbyBtn.setOnClickListener(v -> openSchemeApplication("PMFBY", "https://pmfby.gov.in"));
        buttonGrid.addView(pmfbyBtn);

        // KCC Button
        MaterialButton kccBtn = new MaterialButton(requireContext());
        kccBtn.setText("किसान क्रेडिट कार्ड");
        kccBtn.setTextSize(14);
        LinearLayout.LayoutParams kccParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        kccParams.setMargins(0, 0, 0, 8);
        kccBtn.setLayoutParams(kccParams);
        kccBtn.setPadding(16, 12, 16, 12);
        kccBtn.setBackgroundColor(getResources().getColor(R.color.warning, null));
        kccBtn.setOnClickListener(v -> openSchemeApplication("KCC", "https://pmkisan.gov.in/kcc"));
        buttonGrid.addView(kccBtn);

        // Bihar Schemes Button
        MaterialButton biharBtn = new MaterialButton(requireContext());
        biharBtn.setText("बिहार सरकार योजनाएं");
        biharBtn.setTextSize(14);
        LinearLayout.LayoutParams biharParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        biharBtn.setLayoutParams(biharParams);
        biharBtn.setPadding(16, 12, 16, 12);
        biharBtn.setBackgroundColor(getResources().getColor(R.color.secondary, null));
        biharBtn.setOnClickListener(v -> openSchemeApplication("Bihar Schemes", "https://bihar.gov.in/agriculture"));
        buttonGrid.addView(biharBtn);

        cardContent.addView(buttonGrid);
        quickCard.addView(cardContent);
        schemesContainer.addView(quickCard);
    }

    private void addSchemeCard(String title, String subtitle, SchemeInfo[] schemes) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardParams);
        card.setRadius(12);
        card.setCardElevation(4);
        card.setCardBackgroundColor(getResources().getColor(R.color.surface, null));

        LinearLayout cardContent = new LinearLayout(requireContext());
        cardContent.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(16, 16, 16, 16);

        // Title
        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextSize(18);
        titleView.setTextColor(getResources().getColor(R.color.text_primary, null));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        cardContent.addView(titleView);

        // Subtitle
        TextView subtitleView = new TextView(requireContext());
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(14);
        subtitleView.setTextColor(getResources().getColor(R.color.text_secondary, null));
        subtitleView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        subtitleView.setPadding(0, 8, 0, 16);
        cardContent.addView(subtitleView);

        // Schemes with application buttons
        for (SchemeInfo scheme : schemes) {
            // Scheme description
            TextView schemeView = new TextView(requireContext());
            schemeView.setText("• " + scheme.description);
            schemeView.setTextSize(14);
            schemeView.setTextColor(getResources().getColor(R.color.text_primary, null));
            schemeView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            schemeView.setPadding(0, 4, 0, 4);
            cardContent.addView(schemeView);

            // Application button
            if (scheme.applicationUrl != null && !scheme.applicationUrl.isEmpty()) {
                MaterialButton applyButton = new MaterialButton(requireContext());
                applyButton.setText("आवेदन करें");
                applyButton.setTextSize(12);
                LinearLayout.LayoutParams applyButtonParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                applyButtonParams.setMargins(32, 8, 0, 8);
                applyButton.setLayoutParams(applyButtonParams);
                applyButton.setPadding(16, 8, 16, 8);
                
                applyButton.setOnClickListener(v -> {
                    openSchemeApplication(scheme.name, scheme.applicationUrl);
                });
                
                cardContent.addView(applyButton);
            }
        }

        card.addView(cardContent);
        schemesContainer.addView(card);
    }

    // Scheme information class
    private static class SchemeInfo {
        String name;
        String description;
        String applicationUrl;

        SchemeInfo(String name, String description, String applicationUrl) {
            this.name = name;
            this.description = description;
            this.applicationUrl = applicationUrl;
        }
    }

    private SchemeInfo[] getCentralSchemes(String crop, String land) {
        return new SchemeInfo[]{
            new SchemeInfo("PM-KISAN", "प्रधानमंत्री किसान सम्मान निधि - ₹6,000/वर्ष", "https://pmkisan.gov.in"),
            new SchemeInfo("PMFBY", "प्रधानमंत्री फसल बीमा योजना - फसल क्षति का बीमा", "https://pmfby.gov.in"),
            new SchemeInfo("KCC", "किसान क्रेडिट कार्ड - कम ब्याज पर ऋण", "https://pmkisan.gov.in/kcc"),
            new SchemeInfo("PMKSY", "प्रधानमंत्री कृषि सिंचाई योजना - सिंचाई सुविधा", "https://pmksy.gov.in"),
            new SchemeInfo("RKVY", "राष्ट्रीय कृषि विकास योजना - कृषि बुनियादी ढांचा", "https://rkvy.nic.in"),
            new SchemeInfo("Soil Health Card", "मृदा स्वास्थ्य कार्ड योजना", "https://soilhealth.dac.gov.in")
        };
    }

    private SchemeInfo[] getBiharSchemes(String crop, String land) {
        return new SchemeInfo[]{
            new SchemeInfo("Bihar KCC", "बिहार किसान क्रेडिट कार्ड योजना - 3% ब्याज सब्सिडी", "https://bihar.gov.in/agriculture"),
            new SchemeInfo("Bihar Agriculture Roadmap", "बिहार कृषि रोड मैप - 2022-2027 कार्य योजना", "https://bihar.gov.in/agriculture"),
            new SchemeInfo("Bihar Kisan Kalyan", "बिहार किसान कल्याण योजना - आर्थिक सहायता", "https://bihar.gov.in/agriculture"),
            new SchemeInfo("Bihar Agriculture Diversification", "बिहार कृषि विविधीकरण योजना - नई फसलों के लिए", "https://bihar.gov.in/agriculture"),
            new SchemeInfo("Bihar Irrigation", "बिहार सिंचाई योजना - नहर और ट्यूबवेल सब्सिडी", "https://bihar.gov.in/agriculture"),
            new SchemeInfo("Bihar Agriculture Mechanization", "बिहार कृषि मशीनीकरण योजना - ट्रैक्टर और उपकरण सब्सिडी", "https://bihar.gov.in/agriculture")
        };
    }

    private SchemeInfo[] getStateSchemes(String crop, String land, String state) {
        return new SchemeInfo[]{
            new SchemeInfo(state + " KCC", state + " किसान क्रेडिट कार्ड योजना", "https://agricoop.gov.in"),
            new SchemeInfo(state + " Agriculture", state + " कृषि विकास योजना", "https://agricoop.gov.in"),
            new SchemeInfo(state + " Irrigation", state + " सिंचाई सब्सिडी योजना", "https://agricoop.gov.in"),
            new SchemeInfo(state + " Mechanization", state + " कृषि मशीनीकरण योजना", "https://agricoop.gov.in"),
            new SchemeInfo(state + " Crop Insurance", state + " फसल बीमा योजना", "https://agricoop.gov.in"),
            new SchemeInfo("Local Contact", "स्थानीय कृषि विभाग से संपर्क करें", null)
        };
    }

    private SchemeInfo[] getCropSpecificSchemes(String crop, String land) {
        if (crop.contains("धान") || crop.contains("rice")) {
            return new SchemeInfo[]{
                new SchemeInfo("Paddy Package", "धान उत्पादन बढ़ाने के लिए विशेष पैकेज", "https://agricoop.gov.in"),
                new SchemeInfo("Paddy Seed Subsidy", "धान किसानों के लिए बीज सब्सिडी", "https://agricoop.gov.in"),
                new SchemeInfo("Paddy Pest Management", "धान कीट प्रबंधन योजना", "https://agricoop.gov.in"),
                new SchemeInfo("Paddy Storage", "धान भंडारण सुविधा योजना", "https://agricoop.gov.in")
            };
        } else if (crop.contains("गेहूं") || crop.contains("wheat")) {
            return new SchemeInfo[]{
                new SchemeInfo("Wheat Package", "गेहूं उत्पादन बढ़ाने के लिए विशेष पैकेज", "https://agricoop.gov.in"),
                new SchemeInfo("Wheat Seed Subsidy", "गेहूं किसानों के लिए बीज सब्सिडी", "https://agricoop.gov.in"),
                new SchemeInfo("Wheat Pest Management", "गेहूं कीट प्रबंधन योजना", "https://agricoop.gov.in"),
                new SchemeInfo("Wheat Storage", "गेहूं भंडारण सुविधा योजना", "https://agricoop.gov.in")
            };
        } else if (crop.contains("मक्का") || crop.contains("maize")) {
            return new SchemeInfo[]{
                new SchemeInfo("Maize Package", "मक्का उत्पादन बढ़ाने के लिए विशेष पैकेज", "https://agricoop.gov.in"),
                new SchemeInfo("Maize Seed Subsidy", "मक्का किसानों के लिए बीज सब्सिडी", "https://agricoop.gov.in"),
                new SchemeInfo("Maize Pest Management", "मक्का कीट प्रबंधन योजना", "https://agricoop.gov.in"),
                new SchemeInfo("Maize Storage", "मक्का भंडारण सुविधा योजना", "https://agricoop.gov.in")
            };
        } else {
            return new SchemeInfo[]{
                new SchemeInfo("General Crop Schemes", "फसल विशेष योजनाएं उपलब्ध हैं", "https://agricoop.gov.in"),
                new SchemeInfo("Department Contact", "कृषि विभाग से संपर्क करें", null),
                new SchemeInfo("Subsidy Information", "फसल के अनुसार सब्सिडी मिलेगी", "https://agricoop.gov.in"),
                new SchemeInfo("Expert Advice", "विशेषज्ञ सलाह के लिए संपर्क करें", null)
            };
        }
    }

    private void setupClickListeners() {
        btnApplyKcc.setOnClickListener(v -> {
            showApplicationInfo("किसान क्रेडिट कार्ड (KCC)", 
                "• आधार कार्ड की प्रति\n• भूमि के कागजात\n• बैंक खाता\n• पासपोर्ट साइज फोटो\n• आय प्रमाण पत्र\n• जाति प्रमाण पत्र (यदि लागू हो)",
                "https://pmkisan.gov.in/kcc");
        });

        btnApplyPmfby.setOnClickListener(v -> {
            showApplicationInfo("प्रधानमंत्री फसल बीमा", 
                "• आधार कार्ड\n• भूमि के कागजात\n• बैंक खाता\n• फसल की जानकारी\n• बीज खरीद रसीद\n• सिंचाई का प्रमाण",
                "https://pmfby.gov.in");
        });

        btnApplyDrip.setOnClickListener(v -> {
            showApplicationInfo("ड्रिप सिंचाई सब्सिडी", 
                "• आधार कार्ड\n• भूमि के कागजात\n• सिंचाई योजना\n• अनुमानित लागत\n• तकनीकी रिपोर्ट\n• बैंक खाता",
                "https://pmksy.gov.in");
        });
    }

    private void showApplicationInfo(String schemeName, String requirements, String websiteUrl) {
        String message = String.format("योजना: %s\n\nआवश्यक दस्तावेज:\n%s\n\nआवेदन के लिए:\n1. निकटतम ग्राम सेवा केंद्र\n2. कृषि विभाग कार्यालय\n3. बैंक शाखा\n4. ऑनलाइन पोर्टल", 
                schemeName, requirements);
        
        // Show detailed info
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        
        // Open website if available
        if (websiteUrl != null && !websiteUrl.isEmpty()) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl));
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error opening website: " + e.getMessage());
                Toast.makeText(requireContext(), "वेबसाइट खोलने में त्रुटि", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openSchemeApplication(String schemeName, String applicationUrl) {
        if (applicationUrl != null && !applicationUrl.isEmpty()) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(applicationUrl));
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error opening application URL: " + e.getMessage());
                Toast.makeText(requireContext(), "आवेदन URL खोलने में त्रुटि", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(requireContext(), "योजना के लिए आवेदन URL उपलब्ध नहीं है", Toast.LENGTH_SHORT).show();
        }
    }
}
