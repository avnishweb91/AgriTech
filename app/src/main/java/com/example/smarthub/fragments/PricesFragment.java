package com.example.smarthub.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.smarthub.R;
import com.example.smarthub.services.MandiPriceService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PricesFragment extends Fragment {
    
    private static final String TAG = "PricesFragment";
    private static final int LOCATION_PERMISSION_REQUEST = 1002;
    private static final String DISTRICT_PLACEHOLDER = "जिला चुनें";
    
    private AutoCompleteTextView cropSpinner, locationSpinner, districtSpinner;
    private LinearLayout pricesContainer, trendsContainer;
    private TextView tvLastUpdated, tvSelectedInfo;
    private MaterialButton btnRefresh, btnViewTrends;
    
    private MandiPriceService mandiPriceService;
    private Location currentLocation;
    private int activePriceRequest = 0;
    private String selectedCrop = "गेहूँ";
    private String selectedLocation = "बिहार";
    private String selectedDistrict = "";
    private List<String> availableDistricts = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_prices, container, false);
        
        initializeViews(view);
        setupMandiService(); // Set up service first
        setupSpinners(); // Then set up spinners
        String englishStateName = extractEnglishStateName(selectedLocation);
        updateDistrictOptions(englishStateName); // Finally update districts with English state name
        updateSelectedInfo(); // Update display info
        
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Location is optional: farmers can choose a state manually and should not
        // be blocked by a location-permission prompt to see mandi prices.
        if (checkLocationPermission()) {
            getCurrentLocationAndPrices();
        } else {
            currentLocation = createDefaultLocation();
            fetchMandiPrices();
        }
    }
    
    private void initializeViews(View view) {
        cropSpinner = view.findViewById(R.id.crop_spinner);
        locationSpinner = view.findViewById(R.id.location_spinner);
        districtSpinner = view.findViewById(R.id.district_spinner);
        pricesContainer = view.findViewById(R.id.prices_container);
        trendsContainer = view.findViewById(R.id.trends_container);
        tvLastUpdated = view.findViewById(R.id.tv_last_updated);
        tvSelectedInfo = view.findViewById(R.id.tv_selected_info);
        btnRefresh = view.findViewById(R.id.btn_refresh);
        btnViewTrends = view.findViewById(R.id.btn_view_trends);
        
        // Initially hide trends container
        if (trendsContainer != null) {
            trendsContainer.setVisibility(View.GONE);
        }
        
        // Set up test button
        MaterialButton btnTestDistrict = view.findViewById(R.id.btn_test_district);
        if (btnTestDistrict != null) {
            btnTestDistrict.setVisibility(View.GONE);
        }
        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> fetchMandiPrices());
        if (btnViewTrends != null) btnViewTrends.setOnClickListener(v ->
                Toast.makeText(requireContext(), "ऐतिहासिक सरकारी मूल्य-रुझान अभी उपलब्ध नहीं हैं।", Toast.LENGTH_LONG).show());
    }
    
    private void testDistrictSelection() {
        Log.d(TAG, "Testing district selection...");
        
        if (mandiPriceService == null) {
            Toast.makeText(requireContext(), "सेवा उपलब्ध नहीं है", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Test current state
        String currentState = selectedLocation;
        String englishStateName = extractEnglishStateName(currentState);
        Log.d(TAG, "Current state: " + currentState + " -> English: " + englishStateName);
        
        // Get districts for current state
        List<String> districts = mandiPriceService.getDistrictsByState(englishStateName);
        Log.d(TAG, "Districts for " + englishStateName + ": " + districts.size());
        
        // Show district info
        StringBuilder info = new StringBuilder();
        info.append("राज्य: ").append(currentState).append("\n");
        info.append("English: ").append(englishStateName).append("\n");
        info.append("जिले: ").append(districts.size()).append("\n");
        info.append("वर्तमान जिला: ").append(selectedDistrict).append("\n");
        info.append("पहले 5 जिले: ");
        
        for (int i = 0; i < Math.min(5, districts.size()); i++) {
            info.append(districts.get(i));
            if (i < Math.min(4, districts.size() - 1)) {
                info.append(", ");
            }
        }
        
        Toast.makeText(requireContext(), info.toString(), Toast.LENGTH_LONG).show();
        
        // Test district spinner
        if (districtSpinner != null) {
            Log.d(TAG, "District spinner adapter count: " + districtSpinner.getAdapter().getCount());
            Log.d(TAG, "District spinner text: " + districtSpinner.getText());
            Log.d(TAG, "District spinner enabled: " + districtSpinner.isEnabled());
        }
    }
    
    private void setupSpinners() {
        // Crop spinner
        String[] crops = {
            "गेहूँ (Wheat)",
            "धान (Rice)",
            "मक्का (Maize)",
            "भिंडी (Bhindi)",
            "करेला (Bitter gourd)",
            "लौकी (Bottle gourd)",
            "बैंगन (Brinjal)",
            "फूलगोभी (Cauliflower)",
            "लहसुन (Garlic)",
            "परवल (Pointed gourd)",
            "आलू (Potato)",
            "प्याज़ (Onion)",
            "टमाटर (Tomato)",
            "अंगूर (Grapes)",
            "दलहन (Pulses)",
            "तिलहन (Oilseeds)",
            "गन्ना (Sugarcane)",
            "कपास (Cotton)",
            "जूट (Jute)"
        };

        ArrayAdapter<String> cropAdapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            crops
        );

        cropSpinner.setAdapter(cropAdapter);
        cropSpinner.setText(crops[0], false);
        selectedCrop = crops[0];
        cropSpinner.setEnabled(true);

        // Location spinner
        String[] locations = {
            "बिहार (Bihar)",
            "उत्तर प्रदेश (Uttar Pradesh)",
            "मध्य प्रदेश (Madhya Pradesh)",
            "झारखंड (Jharkhand)",
            "पश्चिम बंगाल (West Bengal)",
            "राजस्थान (Rajasthan)",
            "महाराष्ट्र (Maharashtra)",
            "कर्नाटक (Karnataka)",
            "तमिलनाडु (Tamil Nadu)",
            "आंध्र प्रदेश (Andhra Pradesh)",
            "छत्तीसगढ़ (Chhattisgarh)", "गोवा (Goa)", "गुजरात (Gujarat)",
            "हरियाणा (Haryana)", "हिमाचल प्रदेश (Himachal Pradesh)",
            "मणिपुर (Manipur)", "मेघालय (Meghalaya)", "मिज़ोरम (Mizoram)",
            "नागालैंड (Nagaland)", "ओडिशा (Odisha)", "पंजाब (Punjab)",
            "सिक्किम (Sikkim)", "तेलंगाना (Telangana)", "त्रिपुरा (Tripura)",
            "उत्तराखंड (Uttarakhand)", "अरुणाचल प्रदेश (Arunachal Pradesh)",
            "असम (Assam)", "केरल (Kerala)",
            "अंडमान और निकोबार द्वीपसमूह (Andaman and Nicobar Islands)",
            "चंडीगढ़ (Chandigarh)",
            "दादरा और नगर हवेली और दमन और दीव (Dadra and Nagar Haveli and Daman and Diu)",
            "दिल्ली (Delhi)", "जम्मू और कश्मीर (Jammu and Kashmir)",
            "लद्दाख (Ladakh)", "लक्षद्वीप (Lakshadweep)", "पुडुचेरी (Puducherry)"
        };

        ArrayAdapter<String> locationAdapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            locations
        );

        locationAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
        locationSpinner.setAdapter(locationAdapter);
        locationSpinner.setText(locations[0], false);
        selectedLocation = locations[0];
        locationSpinner.setEnabled(true);

        // Set up change listeners
        cropSpinner.setOnItemClickListener((parent, view1, position, id) -> {
            selectedCrop = crops[position];
            Log.d(TAG, "Crop selected: " + selectedCrop);
            updateSelectedInfo();
            fetchMandiPrices();
        });

        locationSpinner.setOnItemClickListener((parent, view1, position, id) -> {
            selectedLocation = locations[position];
            Log.d(TAG, "Location selected: " + selectedLocation);
            
            // Extract English state name for district lookup
            String englishStateName = extractEnglishStateName(selectedLocation);
            Log.d(TAG, "Extracted English state name: " + englishStateName);
            
            updateDistrictOptions(englishStateName);
            updateSelectedInfo();
            fetchMandiPrices();
        });

        districtSpinner.setOnItemClickListener((parent, view1, position, id) -> {
            selectedDistrict = position == 0 || position > availableDistricts.size()
                    ? "" : availableDistricts.get(position - 1);
            Log.d(TAG, "District selected: " + selectedDistrict);
            updateSelectedInfo();
            if (selectedDistrict.isEmpty()) showPricesUnavailable("कृपया सूची से जिला चुनें।");
            else fetchMandiPrices();
        });

        // Focus change listeners for better compatibility
        cropSpinner.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                cropSpinner.showDropDown();
            }
        });

        locationSpinner.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                locationSpinner.showDropDown();
            }
        });

        districtSpinner.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                districtSpinner.showDropDown();
            }
        });

        // Initial display - will be updated after service is set up
        Log.d(TAG, "Spinners setup completed");
    }
    
    private String extractEnglishStateName(String fullStateName) {
        // Extract English state name from "हिंदी (English)" format
        if (fullStateName.contains("(") && fullStateName.contains(")")) {
            String englishPart = fullStateName.substring(fullStateName.indexOf("(") + 1, fullStateName.indexOf(")"));
            Log.d(TAG, "Extracted English state name: " + englishPart + " from: " + fullStateName);
            return englishPart.trim();
        }
        
        // If no parentheses, return the original name
        Log.d(TAG, "No English state name found, using original: " + fullStateName);
        return fullStateName;
    }
    
    private void updateDistrictOptions(String state) {
        Log.d(TAG, "Updating district options for state: " + state);
        
        if (mandiPriceService == null) {
            Log.e(TAG, "MandiPriceService is null, cannot update districts");
            Toast.makeText(requireContext(), "सेवा लोड हो रही है, कृपया प्रतीक्षा करें", Toast.LENGTH_SHORT).show();
            return;
        }
        
        selectedDistrict = "";
        availableDistricts.clear();
        setDistrictAdapter(new ArrayList<>());
        districtSpinner.setText("जिले लोड हो रहे हैं…", false);
        mandiPriceService.loadDistrictsByState(state, new MandiPriceService.DistrictsCallback() {
            @Override
            public void onDistrictsReceived(List<String> officialDistricts) {
                if (!isAdded()) return;
                androidx.fragment.app.FragmentActivity activity = getActivity();
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    if (!isAdded() || getView() == null || !state.equals(extractEnglishStateName(selectedLocation))) return;
                    java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(officialDistricts);
                    merged.addAll(mandiPriceService.getDistrictsByState(state));
                    availableDistricts = new ArrayList<>(merged);
                    setDistrictAdapter(availableDistricts);
                    districtSpinner.setText(DISTRICT_PLACEHOLDER, false);
                    updateSelectedInfo();
                    if (availableDistricts.isEmpty()) showPricesUnavailable("इस राज्य के लिए मंडी जिले उपलब्ध नहीं हैं।");
                });
            }

            @Override
            public void onDistrictsError(String error) {
                if (!isAdded()) return;
                androidx.fragment.app.FragmentActivity activity = getActivity();
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    if (!isAdded() || getView() == null || !state.equals(extractEnglishStateName(selectedLocation))) return;
                    availableDistricts = mandiPriceService.getDistrictsByState(state);
                    setDistrictAdapter(availableDistricts);
                    districtSpinner.setText(DISTRICT_PLACEHOLDER, false);
                    updateSelectedInfo();
                    if (availableDistricts.isEmpty()) showPricesUnavailable(error);
                });
            }
        });
    }

    private void setDistrictAdapter(List<String> districts) {
        List<String> options = new ArrayList<>();
        options.add(DISTRICT_PLACEHOLDER);
        options.addAll(districts);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, options);
        adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
        districtSpinner.setAdapter(adapter);
        districtSpinner.setEnabled(true);
    }
    
    private void setupMandiService() {
        mandiPriceService = new MandiPriceService(requireContext());
    }
    
    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(requireActivity(), 
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 
                LOCATION_PERMISSION_REQUEST);
    }
    
    private void getCurrentLocationAndPrices() {
        try {
            LocationManager locationManager = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);
            
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) 
                        == PackageManager.PERMISSION_GRANTED) {
                    
                    currentLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    
                    if (currentLocation != null) {
                        Log.d(TAG, "Location obtained: " + currentLocation.getLatitude() + ", " + currentLocation.getLongitude());
                        fetchMandiPrices();
                    } else {
                        currentLocation = createDefaultLocation();
                        Log.d(TAG, "Using default location: Patna, Bihar");
                        fetchMandiPrices();
                    }
                }
            } else {
                currentLocation = createDefaultLocation();
                Log.d(TAG, "GPS not available, using default location: Patna, Bihar");
                fetchMandiPrices();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting location: " + e.getMessage(), e);
            currentLocation = createDefaultLocation();
            fetchMandiPrices();
        }
    }
    
    private Location createDefaultLocation() {
        Location location = new Location("default");
        location.setLatitude(25.5941); // Patna, Bihar coordinates
        location.setLongitude(85.1376);
        return location;
    }
    
    private void updateSelectedInfo() {
        String info = String.format("फसल: %s | स्थान: %s | जिला: %s", selectedCrop, selectedLocation, selectedDistrict);
        if (tvSelectedInfo != null) {
            tvSelectedInfo.setText(info);
        }
        
        Log.d(TAG, "Selected info updated: " + info);
    }
    
    private void fetchMandiPrices() {
        if (selectedDistrict == null || selectedDistrict.trim().isEmpty()) {
            showPricesUnavailable("फसल और राज्य के बाद सूची से जिला चुनें।");
            return;
        }
        if (mandiPriceService != null) {
            final int requestId = ++activePriceRequest;
            String englishStateName = extractEnglishStateName(selectedLocation);
            Log.d(TAG, "Fetching mandi prices for: " + selectedCrop + " in " + selectedLocation + " - " + selectedDistrict + " (State: " + englishStateName + ")");
            showPricesLoading();
            
            // Use district-based prices instead of location-based
            mandiPriceService.getMandiPricesByDistrict(selectedCrop, englishStateName, selectedDistrict, new MandiPriceService.PriceCallback() {
                @Override
                public void onPricesReceived(List<MandiPriceService.MandiPrice> prices) {
                    if (!isAdded()) return;
                    androidx.fragment.app.FragmentActivity activity = getActivity();
                    if (activity == null) return;
                    activity.runOnUiThread(() -> {
                        if (!isAdded() || getView() == null || requestId != activePriceRequest) return;
                        displayMandiPrices(prices);
                        updateLastUpdated();
                    });
                }
                
                @Override
                public void onPriceError(String error) {
                    if (!isAdded()) return;
                    androidx.fragment.app.FragmentActivity activity = getActivity();
                    if (activity == null) return;
                    activity.runOnUiThread(() -> {
                        if (!isAdded() || getView() == null || requestId != activePriceRequest) return;
                        showPricesUnavailable(error);
                    });
                }
            });
            
        } else {
            Log.e(TAG, "MandiPriceService is null");
            showPricesUnavailable();
        }
    }

    private void showPricesLoading() {
        if (pricesContainer == null) return;
        pricesContainer.removeAllViews();
        TextView message = new TextView(requireContext());
        message.setText("सरकारी मंडी भाव लोड हो रहे हैं…");
        message.setTextSize(16);
        message.setPadding(24, 32, 24, 32);
        pricesContainer.addView(message);
    }
    
    private void displayMandiPrices(List<MandiPriceService.MandiPrice> prices) {
        if (pricesContainer == null) return;
        
        pricesContainer.removeAllViews();
        
        if (prices.isEmpty()) {
            TextView noPricesText = new TextView(requireContext());
            noPricesText.setText("इस फसल के लिए मंडी भाव उपलब्ध नहीं हैं");
            noPricesText.setTextSize(16);
            noPricesText.setTextColor(getResources().getColor(R.color.text_secondary, null));
            noPricesText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            noPricesText.setPadding(16, 16, 16, 16);
            pricesContainer.addView(noPricesText);
            return;
        }
        
        for (MandiPriceService.MandiPrice price : prices) {
            addPriceCard(price);
        }
        
        Log.d(TAG, "Displayed " + prices.size() + " mandi prices");
    }
    
    private void addPriceCard(MandiPriceService.MandiPrice price) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 12);
        card.setLayoutParams(cardParams);
        card.setRadius(8);
        card.setCardElevation(2);
        card.setCardBackgroundColor(getResources().getColor(R.color.surface, null));

        LinearLayout cardContent = new LinearLayout(requireContext());
        cardContent.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        cardContent.setOrientation(LinearLayout.HORIZONTAL);
        cardContent.setPadding(16, 16, 16, 16);
        cardContent.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Left side - Mandi and Crop info
        LinearLayout leftContent = new LinearLayout(requireContext());
        leftContent.setLayoutParams(new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        ));
        leftContent.setOrientation(LinearLayout.VERTICAL);

        TextView mandiName = new TextView(requireContext());
        String mandiLabel = price.mandiName;
        if (price.mandiNameHindi != null && !price.mandiNameHindi.isEmpty()
                && !price.mandiNameHindi.equals(price.mandiName)) {
            mandiLabel = price.mandiNameHindi + " (" + price.mandiName + ")";
        }
        mandiName.setText(mandiLabel);
        mandiName.setTextSize(16);
        mandiName.setTextColor(getResources().getColor(R.color.text_primary, null));
        mandiName.setTypeface(null, android.graphics.Typeface.BOLD);
        mandiName.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        leftContent.addView(mandiName);

        TextView cropInfo = new TextView(requireContext());
        String cropDetails = price.cropNameHindi + " (" + price.quality + ")";
        if (price.feedDate != null && !price.feedDate.trim().isEmpty()) cropDetails += " · भाव की तारीख: " + price.feedDate;
        cropInfo.setText(cropDetails);
        cropInfo.setTextSize(14);
        cropInfo.setTextColor(getResources().getColor(R.color.text_secondary, null));
        cropInfo.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        leftContent.addView(cropInfo);

        // Right side - Price and change info
        LinearLayout rightContent = new LinearLayout(requireContext());
        rightContent.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        rightContent.setOrientation(LinearLayout.VERTICAL);
        rightContent.setGravity(android.view.Gravity.END);

        TextView priceText = new TextView(requireContext());
        priceText.setText(String.format(Locale.getDefault(), "₹%.2f/%s", price.price, price.unit));
        priceText.setTextSize(18);
        priceText.setTextColor(getResources().getColor(R.color.text_primary, null));
        priceText.setTypeface(null, android.graphics.Typeface.BOLD);
        priceText.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        rightContent.addView(priceText);

        // Price change indicator
        if (price.priceChange != 0) {
            TextView changeText = new TextView(requireContext());
            String changeSymbol = price.priceChange > 0 ? "↗️" : "↘️";
            String changeType = price.priceChange > 0 ? "बढ़ा" : "घटा";
            int changeColor = price.priceChange > 0 ? R.color.success : R.color.error;
            
            changeText.setText(String.format(Locale.getDefault(), "%s ₹%.2f (%s)", 
                changeSymbol, Math.abs(price.priceChange), changeType));
            changeText.setTextSize(12);
            changeText.setTextColor(getResources().getColor(changeColor, null));
            changeText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            rightContent.addView(changeText);
        }

        cardContent.addView(leftContent);
        cardContent.addView(rightContent);
        card.addView(cardContent);
        pricesContainer.addView(card);
    }
    
    private void displayPriceTrends(MandiPriceService.PriceTrend trend) {
        if (trendsContainer == null) return;
        
        trendsContainer.removeAllViews();
        trendsContainer.setVisibility(View.VISIBLE);
        
        // Create trend card
        MaterialCardView trendCard = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 16);
        trendCard.setLayoutParams(cardParams);
        trendCard.setRadius(12);
        trendCard.setCardElevation(4);
        trendCard.setCardBackgroundColor(getResources().getColor(R.color.info, null));

        LinearLayout cardContent = new LinearLayout(requireContext());
        cardContent.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(16, 16, 16, 16);

        // Title
        TextView titleView = new TextView(requireContext());
        titleView.setText("📈 मूल्य प्रवृत्ति");
        titleView.setTextSize(18);
        titleView.setTextColor(getResources().getColor(R.color.white, null));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        cardContent.addView(titleView);

        // Trend info
        TextView trendView = new TextView(requireContext());
        trendView.setText("प्रवृत्ति: " + trend.trendHindi);
        trendView.setTextSize(16);
        trendView.setTextColor(getResources().getColor(R.color.white, null));
        trendView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        trendView.setPadding(0, 8, 0, 8);
        cardContent.addView(trendView);

        TextView avgPriceView = new TextView(requireContext());
        avgPriceView.setText(String.format(Locale.getDefault(), "औसत मूल्य: ₹%.2f", trend.averagePrice));
        avgPriceView.setTextSize(14);
        avgPriceView.setTextColor(getResources().getColor(R.color.white, null));
        avgPriceView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        avgPriceView.setPadding(0, 4, 0, 8);
        cardContent.addView(avgPriceView);

        TextView recommendationView = new TextView(requireContext());
        recommendationView.setText("सलाह: " + trend.recommendationHindi);
        recommendationView.setTextSize(14);
        recommendationView.setTextColor(getResources().getColor(R.color.white, null));
        recommendationView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        cardContent.addView(recommendationView);

        trendCard.addView(cardContent);
        trendsContainer.addView(trendCard);
    }
    
    private void updateLastUpdated() {
        if (tvLastUpdated != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            String currentTime = sdf.format(new Date());
            tvLastUpdated.setText("अंतिम अपडेट: " + currentTime);
        }
    }
    
    private void showPricesUnavailable() {
        showPricesUnavailable("आज सरकारी फीड में इस फसल का भाव दर्ज नहीं है। दूसरे दिन या फसल चुनकर देखें।");
    }

    private void showPricesUnavailable(String reason) {
        if (pricesContainer != null) {
            pricesContainer.removeAllViews();
            TextView message = new TextView(requireContext());
            message.setText(reason);
            message.setTextSize(16);
            message.setPadding(24, 32, 24, 32);
            pricesContainer.addView(message);
        }
    }

    @Override
    public void onDestroyView() {
        activePriceRequest++;
        if (mandiPriceService != null) {
            mandiPriceService.shutdown();
            mandiPriceService = null;
        }
        super.onDestroyView();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocationAndPrices();
            } else {
                Toast.makeText(requireContext(), "स्थान की अनुमति आवश्यक है मंडी भाव के लिए", Toast.LENGTH_LONG).show();
                showPricesUnavailable();
            }
        }
    }
}
