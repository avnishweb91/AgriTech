package com.example.smarthub.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smarthub.R;
import com.example.smarthub.services.MarketplaceService;
import com.example.smarthub.api.APIService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;

import android.app.DatePickerDialog;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

public class MarketplaceFragment extends Fragment {
    
    private static final String TAG = "MarketplaceFragment";
    
    // UI Components
    private TabLayout tabLayout;
    private LinearLayout listingsContainer, buyRequestsContainer, marketStatsContainer;
    private AutoCompleteTextView spinnerState, spinnerDistrict, spinnerCrop;
    private MaterialButton btnListCrop, btnCreateBuyRequest, btnRefresh;
    
    // Services
    private MarketplaceService marketplaceService;
    private APIService apiService;
    private ExecutorService chatExecutor;
    private Handler chatHandler;
    
    // Data
    private List<MarketplaceService.CropListing> currentListings = new ArrayList<>();
    private List<MarketplaceService.BuyRequest> currentBuyRequests = new ArrayList<>();
    private MarketplaceService.MarketStats currentMarketStats;
    
    // Filter options
    private String selectedState = "बिहार (Bihar)";
    private String selectedDistrict = "Patna";
    private String selectedCrop = "सभी फसलें";
    
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
    
    private String[] crops = {
        "सभी फसलें",
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
        "जूट (Jute)"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_marketplace, container, false);
        
        initializeViews(view);
        setupServices();
        setupSpinners();
        setupTabLayout();
        setupClickListeners();
        loadMarketplaceData();
        
        return view;
    }
    
    private void initializeViews(View view) {
        tabLayout = view.findViewById(R.id.tab_layout);
        listingsContainer = view.findViewById(R.id.listings_container);
        buyRequestsContainer = view.findViewById(R.id.buy_requests_container);
        marketStatsContainer = view.findViewById(R.id.market_stats_container);
        
        spinnerState = view.findViewById(R.id.spinner_state);
        spinnerDistrict = view.findViewById(R.id.spinner_district);
        spinnerCrop = view.findViewById(R.id.spinner_crop);
        
        btnListCrop = view.findViewById(R.id.btn_list_crop);
        btnCreateBuyRequest = view.findViewById(R.id.btn_create_buy_request);
        btnRefresh = view.findViewById(R.id.btn_refresh);
    }
    
    private void setupServices() {
        marketplaceService = new MarketplaceService(requireContext());
        apiService = new APIService(requireContext());
        chatExecutor = Executors.newSingleThreadExecutor();
        chatHandler = new Handler(Looper.getMainLooper());
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
        
        // Crop spinner
        ArrayAdapter<String> cropAdapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            crops
        );
        spinnerCrop.setAdapter(cropAdapter);
        spinnerCrop.setText(crops[0], false);
        
        // Set up change listeners
        spinnerState.setOnItemClickListener((parent, view1, position, id) -> {
            selectedState = states[position];
            String englishStateName = extractEnglishStateName(selectedState);
            updateDistrictOptions(englishStateName);
            loadMarketplaceData();
        });
        
        spinnerDistrict.setOnItemClickListener((parent, view1, position, id) -> {
            selectedDistrict = parent.getItemAtPosition(position).toString();
            loadMarketplaceData();
        });
        
        spinnerCrop.setOnItemClickListener((parent, view1, position, id) -> {
            selectedCrop = crops[position];
            loadMarketplaceData();
        });
        
        // Focus change listeners
        spinnerState.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) spinnerState.showDropDown();
        });
        
        spinnerDistrict.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) spinnerDistrict.showDropDown();
        });
        
        spinnerCrop.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) spinnerCrop.showDropDown();
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
        try {
            List<String> districts = new ArrayList<>();
            
            switch (state.toLowerCase()) {
                case "bihar":
                    districts = Arrays.asList("Patna", "Ara", "Bhagalpur", "Muzaffarpur", "Gaya", "Darbhanga", 
                        "Chapra", "Sasaram", "Motihari", "Bettiah", "Siwan", "Gopalganj");
                    break;
                case "up":
                    districts = Arrays.asList("Lucknow", "Kanpur", "Agra", "Varanasi", "Prayagraj", "Gorakhpur",
                        "Bareilly", "Aligarh", "Moradabad", "Saharanpur", "Meerut", "Ghaziabad");
                    break;
                case "mp":
                    districts = Arrays.asList("Bhopal", "Indore", "Jabalpur", "Gwalior", "Ujjain", "Sagar",
                        "Rewa", "Satna", "Chhatarpur", "Panna", "Damoh", "Tikamgarh");
                    break;
                default:
                    districts = Arrays.asList("Capital", "Major City", "Industrial Hub", "Agricultural Center");
                    break;
            }
            
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
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating district options: " + e.getMessage(), e);
        }
    }
    
    private void setupTabLayout() {
        tabLayout.addTab(tabLayout.newTab().setText("📊 फसल सूची"));
        tabLayout.addTab(tabLayout.newTab().setText("🛒 खरीद अनुरोध"));
        tabLayout.addTab(tabLayout.newTab().setText("📈 बाजार आंकड़े"));
        
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showTabContent(tab.getPosition());
            }
            
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
        
        // Show first tab by default
        showTabContent(0);
    }
    
    private void showTabContent(int tabPosition) {
        listingsContainer.setVisibility(tabPosition == 0 ? View.VISIBLE : View.GONE);
        buyRequestsContainer.setVisibility(tabPosition == 1 ? View.VISIBLE : View.GONE);
        marketStatsContainer.setVisibility(tabPosition == 2 ? View.VISIBLE : View.GONE);
    }
    
    private void loadMarketplaceData() {
        String englishStateName = extractEnglishStateName(selectedState);
        
        // Load crop listings using getCropListings method
        marketplaceService.getCropListings(selectedCrop, selectedState, selectedDistrict, 
            new MarketplaceService.MarketplaceCallback() {
                @Override
                public void onListingsReceived(List<MarketplaceService.CropListing> listings) {
                    currentListings = listings;
                    requireActivity().runOnUiThread(() -> displayCropListings(listings));
                }
                
                @Override
                public void onBuyRequestsReceived(List<MarketplaceService.BuyRequest> buyRequests) {
                    currentBuyRequests = buyRequests;
                    requireActivity().runOnUiThread(() -> displayBuyRequests(buyRequests));
                }
                
                @Override
                public void onMarketStatsReceived(MarketplaceService.MarketStats stats) {
                    currentMarketStats = stats;
                    requireActivity().runOnUiThread(() -> displayMarketStats(stats));
                }
                
                @Override
                public void onError(String error) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                        displayCropListings(new ArrayList<>());
                        displayBuyRequests(new ArrayList<>());
                    });
                }
            });
        
        // Load buy requests
        marketplaceService.getBuyRequests(englishStateName, selectedCrop, 
            new MarketplaceService.MarketplaceCallback() {
                @Override
                public void onListingsReceived(List<MarketplaceService.CropListing> listings) {}
                
                @Override
                public void onBuyRequestsReceived(List<MarketplaceService.BuyRequest> buyRequests) {
                    currentBuyRequests = buyRequests;
                    requireActivity().runOnUiThread(() -> displayBuyRequests(buyRequests));
                }
                
                @Override
                public void onMarketStatsReceived(MarketplaceService.MarketStats stats) {}
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error loading buy requests: " + error);
                }
            });
        
        // Load market stats
        marketplaceService.getMarketStats(englishStateName, 
            new MarketplaceService.MarketplaceCallback() {
                @Override
                public void onListingsReceived(List<MarketplaceService.CropListing> listings) {}
                
                @Override
                public void onBuyRequestsReceived(List<MarketplaceService.BuyRequest> buyRequests) {}
                
                @Override
                public void onMarketStatsReceived(MarketplaceService.MarketStats stats) {
                    currentMarketStats = stats;
                    requireActivity().runOnUiThread(() -> displayMarketStats(stats));
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error loading market stats: " + error);
                }
            });
    }
    
    private void displayCropListings(List<MarketplaceService.CropListing> listings) {
        if (listingsContainer == null) return;
        
        listingsContainer.removeAllViews();
        
        if (listings.isEmpty()) {
            TextView noListingsText = new TextView(requireContext());
            noListingsText.setText("इस क्षेत्र में कोई फसल सूची उपलब्ध नहीं है");
            noListingsText.setTextSize(16);
            noListingsText.setTextColor(getResources().getColor(R.color.text_secondary, null));
            noListingsText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            noListingsText.setPadding(16, 16, 16, 16);
            listingsContainer.addView(noListingsText);
            return;
        }
        
        for (MarketplaceService.CropListing listing : listings) {
            addListingCard(listing);
        }
    }
    
    private void addListingCard(MarketplaceService.CropListing listing) {
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
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(16, 16, 16, 16);

        // Header with crop and price
        LinearLayout headerLayout = new LinearLayout(requireContext());
        headerLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView cropName = new TextView(requireContext());
        cropName.setText(listing.cropNameHindi + " — " + listing.quantity + " " + listing.unit);
        cropName.setTextSize(16);
        cropName.setTextColor(getResources().getColor(R.color.text_primary, null));
        cropName.setTypeface(null, android.graphics.Typeface.BOLD);
        cropName.setLayoutParams(new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        ));

        TextView priceText = new TextView(requireContext());
        priceText.setText(String.format(Locale.getDefault(), "₹%.0f/%s", listing.pricePerUnit, listing.unit));
        priceText.setTextSize(16);
        priceText.setTextColor(getResources().getColor(R.color.success, null));
        priceText.setTypeface(null, android.graphics.Typeface.BOLD);

        headerLayout.addView(cropName);
        headerLayout.addView(priceText);
        cardContent.addView(headerLayout);

        // Location and quality
        TextView locationText = new TextView(requireContext());
        locationText.setText(listing.location + " · " + listing.quality + " गुणवत्ता");
        locationText.setTextSize(14);
        locationText.setTextColor(getResources().getColor(R.color.text_secondary, null));
        locationText.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        locationText.setPadding(0, 8, 0, 8);
        cardContent.addView(locationText);

        // Seller info
        TextView sellerText = new TextView(requireContext());
        sellerText.setText("विक्रेता: " + listing.farmerNameHindi);
        sellerText.setTextSize(12);
        sellerText.setTextColor(getResources().getColor(R.color.info, null));
        sellerText.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        cardContent.addView(sellerText);

        // Action buttons
        LinearLayout buttonLayout = new LinearLayout(requireContext());
        buttonLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setPadding(0, 12, 0, 0);

        MaterialButton chatButton = new MaterialButton(requireContext(), null, com.google.android.material.R.style.Widget_Material3_Button_OutlinedButton);
        chatButton.setText("💬 बात करें");
        chatButton.setTextSize(12);
        chatButton.setLayoutParams(new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        ));
        chatButton.setOnClickListener(v -> showChatDialog(listing.farmerId, listing.listingId, listing.farmerNameHindi, listing.cropNameHindi,
            listing.quantity + " " + listing.unit, String.format(Locale.getDefault(), "₹%.0f/%s", listing.pricePerUnit, listing.unit)));

        MaterialButton buyButton = new MaterialButton(requireContext());
        buyButton.setText("🛒 खरीदें");
        buyButton.setTextSize(12);
        buyButton.setLayoutParams(new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        ));
        buyButton.setLayoutParams(new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        ));
        buyButton.setOnClickListener(v -> initiatePurchase(listing));

        buttonLayout.addView(chatButton);
        buttonLayout.addView(buyButton);
        cardContent.addView(buttonLayout);

        card.addView(cardContent);
        listingsContainer.addView(card);
    }
    
    private void displayBuyRequests(List<MarketplaceService.BuyRequest> buyRequests) {
        if (buyRequestsContainer == null) return;
        
        buyRequestsContainer.removeAllViews();
        
        if (buyRequests.isEmpty()) {
            TextView noRequestsText = new TextView(requireContext());
            noRequestsText.setText("इस क्षेत्र में कोई खरीद अनुरोध उपलब्ध नहीं है");
            noRequestsText.setTextSize(16);
            noRequestsText.setTextColor(getResources().getColor(R.color.text_secondary, null));
            noRequestsText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            noRequestsText.setPadding(16, 16, 16, 16);
            buyRequestsContainer.addView(noRequestsText);
            return;
        }
        
        for (MarketplaceService.BuyRequest request : buyRequests) {
            addBuyRequestCard(request);
        }
    }
    
    private void addBuyRequestCard(MarketplaceService.BuyRequest request) {
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
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(16, 16, 16, 16);

        // Header with crop and quantity
        LinearLayout headerLayout = new LinearLayout(requireContext());
        headerLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView cropName = new TextView(requireContext());
        cropName.setText(request.cropNameHindi + " — " + request.requiredQuantity + " " + request.unit);
        cropName.setTextSize(16);
        cropName.setTextColor(getResources().getColor(R.color.text_primary, null));
        cropName.setTypeface(null, android.graphics.Typeface.BOLD);
        cropName.setLayoutParams(new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        ));

        TextView maxPriceText = new TextView(requireContext());
        maxPriceText.setText(String.format(Locale.getDefault(), "₹%.0f/%s तक", request.maxPricePerUnit, request.unit));
        maxPriceText.setTextSize(14);
        maxPriceText.setTextColor(getResources().getColor(R.color.warning, null));

        headerLayout.addView(cropName);
        headerLayout.addView(maxPriceText);
        cardContent.addView(headerLayout);

        // Location and urgency
        TextView locationText = new TextView(requireContext());
        locationText.setText(request.location + " · " + getUrgencyText(request.urgency));
        locationText.setTextSize(14);
        locationText.setTextColor(getResources().getColor(R.color.text_secondary, null));
        locationText.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        locationText.setPadding(0, 8, 0, 8);
        cardContent.addView(locationText);

        // Buyer info
        TextView buyerText = new TextView(requireContext());
        buyerText.setText("खरीददार: " + request.buyerNameHindi);
        buyerText.setTextSize(12);
        buyerText.setTextColor(getResources().getColor(R.color.info, null));
        cardContent.addView(buyerText);

        // Action buttons
        LinearLayout buttonLayout = new LinearLayout(requireContext());
        buttonLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setPadding(0, 12, 0, 0);

        MaterialButton chatButton = new MaterialButton(requireContext(), null, com.google.android.material.R.style.Widget_Material3_Button_OutlinedButton);
        chatButton.setText("💬 बात करें");
        chatButton.setTextSize(12);
        chatButton.setLayoutParams(new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        ));
        chatButton.setOnClickListener(v -> showChatDialog(request.buyerId, null, request.buyerNameHindi, request.cropNameHindi,
            request.requiredQuantity + " " + request.unit, String.format(Locale.getDefault(), "₹%.0f/%s तक", request.maxPricePerUnit, request.unit)));

        MaterialButton sellButton = new MaterialButton(requireContext());
        sellButton.setText("💰 बेचें");
        sellButton.setTextSize(12);
        sellButton.setLayoutParams(new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        ));
        sellButton.setOnClickListener(v -> showSellDialog(request));

        buttonLayout.addView(chatButton);
        buttonLayout.addView(sellButton);
        cardContent.addView(buttonLayout);

        card.addView(cardContent);
        buyRequestsContainer.addView(card);
    }
    
    private String getUrgencyText(String urgency) {
        switch (urgency) {
            case "high": return "🔴 तत्काल";
            case "medium": return "🟡 मध्यम";
            case "low": return "🟢 कम";
            default: return urgency;
        }
    }
    
    private void displayMarketStats(MarketplaceService.MarketStats stats) {
        if (marketStatsContainer == null) return;
        
        marketStatsContainer.removeAllViews();
        
        // Market overview card
        MaterialCardView overviewCard = new MaterialCardView(requireContext());
        overviewCard.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        overviewCard.setRadius(12);
        overviewCard.setCardElevation(4);
        overviewCard.setCardBackgroundColor(getResources().getColor(R.color.info, null));

        LinearLayout overviewContent = new LinearLayout(requireContext());
        overviewContent.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        overviewContent.setOrientation(LinearLayout.VERTICAL);
        overviewContent.setPadding(16, 16, 16, 16);

        TextView overviewTitle = new TextView(requireContext());
        overviewTitle.setText("📊 बाजार अवलोकन");
        overviewTitle.setTextSize(18);
        overviewTitle.setTextColor(getResources().getColor(R.color.white, null));
        overviewTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        overviewContent.addView(overviewTitle);

        TextView totalListings = new TextView(requireContext());
        totalListings.setText("कुल फसल सूची: " + stats.totalListings);
        totalListings.setTextSize(16);
        totalListings.setTextColor(getResources().getColor(R.color.white, null));
        totalListings.setPadding(0, 8, 0, 4);
        overviewContent.addView(totalListings);

        TextView totalRequests = new TextView(requireContext());
        totalRequests.setText("कुल खरीद अनुरोध: " + stats.totalBuyRequests);
        totalRequests.setTextSize(16);
        totalRequests.setTextColor(getResources().getColor(R.color.white, null));
        totalRequests.setPadding(0, 4, 0, 4);
        overviewContent.addView(totalRequests);

        TextView avgPrice = new TextView(requireContext());
        avgPrice.setText(String.format(Locale.getDefault(), "औसत मूल्य: ₹%.0f/क्विंटल", stats.averagePrice));
        avgPrice.setTextSize(16);
        avgPrice.setTextColor(getResources().getColor(R.color.white, null));
        avgPrice.setPadding(0, 4, 0, 8);
        overviewContent.addView(avgPrice);

        overviewCard.addView(overviewContent);
        marketStatsContainer.addView(overviewCard);

        // Top crops card
        MaterialCardView topCropsCard = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams topCropsParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        topCropsParams.setMargins(0, 16, 0, 0);
        topCropsCard.setLayoutParams(topCropsParams);
        topCropsCard.setRadius(12);
        topCropsCard.setCardElevation(4);
        topCropsCard.setCardBackgroundColor(getResources().getColor(R.color.surface, null));

        LinearLayout topCropsContent = new LinearLayout(requireContext());
        topCropsContent.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        topCropsContent.setOrientation(LinearLayout.VERTICAL);
        topCropsContent.setPadding(16, 16, 16, 16);

        TextView topCropsTitle = new TextView(requireContext());
        topCropsTitle.setText("🌾 शीर्ष फसलें");
        topCropsTitle.setTextSize(16);
        topCropsTitle.setTextColor(getResources().getColor(R.color.text_primary, null));
        topCropsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        topCropsContent.addView(topCropsTitle);

        // Show most demanded crop
        TextView mostDemandedCrop = new TextView(requireContext());
        mostDemandedCrop.setText("• " + stats.mostDemandedCropHindi);
        mostDemandedCrop.setTextSize(14);
        mostDemandedCrop.setTextColor(getResources().getColor(R.color.text_secondary, null));
        mostDemandedCrop.setPadding(0, 4, 0, 4);
        topCropsContent.addView(mostDemandedCrop);

        topCropsCard.addView(topCropsContent);
        marketStatsContainer.addView(topCropsCard);
    }
    
    private void showChatDialog(String otherUserId, String listingId, String personName, String cropDetails, String quantity, String price) {
        String token = requireContext().getSharedPreferences("auth_prefs", 0).getString("auth_token", null);
        String ownId = requireContext().getSharedPreferences("auth_prefs", 0).getString("user_id", null);
        if (token == null || ownId == null) { Toast.makeText(requireContext(), "चैट के लिए पहले लॉगिन करें", Toast.LENGTH_SHORT).show(); return; }
        if (ownId.equals(otherUserId)) { Toast.makeText(requireContext(), "यह आपकी अपनी पोस्ट है", Toast.LENGTH_SHORT).show(); return; }
        LinearLayout content = new LinearLayout(requireContext()); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(24, 8, 24, 8);
        TextView context = new TextView(requireContext()); context.setText(personName + " • " + cropDetails + " • " + quantity + " • " + price); content.addView(context);
        ScrollView scroll = new ScrollView(requireContext()); LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1); scrollParams.height = (int)(requireContext().getResources().getDisplayMetrics().density * 250); scroll.setLayoutParams(scrollParams);
        TextView transcript = new TextView(requireContext()); transcript.setPadding(8, 16, 8, 16); scroll.addView(transcript); content.addView(scroll);
        EditText composer = new EditText(requireContext()); composer.setHint("संदेश लिखें"); composer.setMaxLines(4); content.addView(composer);
        MaterialButton send = new MaterialButton(requireContext()); send.setText("संदेश भेजें"); content.addView(send);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle("💬 " + personName).setView(content).setNegativeButton("बंद करें", null).create();
        Runnable refresh = new Runnable() { @Override public void run() { loadChat(token, ownId, otherUserId, listingId, transcript, scroll); if (dialog.isShowing()) chatHandler.postDelayed(this, 5000); } };
        dialog.setOnShowListener(d -> { loadChat(token, ownId, otherUserId, listingId, transcript, scroll); chatHandler.postDelayed(refresh, 5000); });
        dialog.setOnDismissListener(d -> chatHandler.removeCallbacks(refresh));
        send.setOnClickListener(v -> { String body = composer.getText().toString().trim(); if (body.isEmpty()) return; send.setEnabled(false);
            chatExecutor.execute(() -> { try { retrofit2.Response<APIService.ChatMessage> response = apiService.backend().sendChatMessage("Bearer " + token, new APIService.ChatMessageRequest(otherUserId, listingId, body)).execute();
                requireActivity().runOnUiThread(() -> { send.setEnabled(true); if (response.isSuccessful()) { composer.setText(""); loadChat(token, ownId, otherUserId, listingId, transcript, scroll); } else Toast.makeText(requireContext(), "संदेश नहीं भेजा जा सका", Toast.LENGTH_SHORT).show(); });
            } catch (Exception e) { if (isAdded()) requireActivity().runOnUiThread(() -> { send.setEnabled(true); Toast.makeText(requireContext(), "इंटरनेट कनेक्शन जाँचें", Toast.LENGTH_SHORT).show(); }); } });
        });
        dialog.show();
    }

    private void loadChat(String token, String ownId, String otherId, String listingId, TextView transcript, ScrollView scroll) {
        if (chatExecutor == null || chatExecutor.isShutdown()) return;
        chatExecutor.execute(() -> { try { retrofit2.Response<List<APIService.ChatMessage>> response = apiService.backend().getChatMessages("Bearer " + token, otherId, listingId).execute();
            if (response.isSuccessful() && response.body() != null && isAdded()) requireActivity().runOnUiThread(() -> { StringBuilder text = new StringBuilder(); for (APIService.ChatMessage m : response.body()) text.append(m.senderId.equals(ownId) ? "आप: " : "" + "सामने वाला: ").append(m.body).append("\n\n"); transcript.setText(text.length() == 0 ? "अभी कोई संदेश नहीं। बातचीत शुरू करें।" : text.toString()); scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN)); });
        } catch (Exception e) { Log.w(TAG, "Chat refresh failed", e); } });
    }
    
    private void initiatePurchase(MarketplaceService.CropListing listing) {
        EditText quantity = new EditText(requireContext());
        quantity.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        quantity.setHint("खरीदने की मात्रा (अधिकतम " + listing.quantity + " " + listing.unit + ")");
        new AlertDialog.Builder(requireContext()).setTitle("🛒 खरीद की मात्रा")
                .setMessage(listing.cropNameHindi + " • ₹" + String.format(Locale.getDefault(), "%.2f", listing.pricePerUnit) + "/" + listing.unit + "\nविक्रेता: " + listing.farmerNameHindi)
                .setView(quantity).setPositiveButton("आगे बढ़ें", (dialog, which) -> {
                    String entered = quantity.getText().toString().trim();
                    try {
                        double selected = Double.parseDouble(entered);
                        if (selected <= 0 || selected > listing.quantity) throw new NumberFormatException();
                        startOnlineCheckout(listing, selected);
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "उपलब्ध मात्रा के भीतर सही मात्रा लिखें", Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("रद्द करें", null).show();
    }
    
    private void startOnlineCheckout(MarketplaceService.CropListing listing, double quantity) {
        String token = requireContext().getSharedPreferences("auth_prefs", 0).getString("auth_token", null);
        if (token == null) { Toast.makeText(requireContext(), "भुगतान के लिए लॉगिन करें", Toast.LENGTH_SHORT).show(); return; }
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> { try {
            retrofit2.Response<APIService.PaymentOrder> response = apiService.backend().createPaymentOrder("Bearer " + token,
                    new APIService.PaymentOrderRequest(listing.listingId, quantity)).execute();
            if (!response.isSuccessful() || response.body() == null || response.body().orderId == null) {
                if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), response.code() == 503 ? "ऑनलाइन भुगतान अभी सेटअप नहीं है। विक्रेता से सीधे संपर्क करें।" : "भुगतान शुरू नहीं हो सका", Toast.LENGTH_LONG).show());
                return;
            }
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> openGatewayCheckout(response.body(), listing, quantity));
        } catch (Exception e) { if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "इंटरनेट कनेक्शन जाँचें", Toast.LENGTH_SHORT).show()); }
        finally { executor.shutdown(); } });
    }

    private void openGatewayCheckout(APIService.PaymentOrder order, MarketplaceService.CropListing listing, double quantity) {
        try {
            requireContext().getSharedPreferences("payment_state", 0).edit()
                    .putString("pending_payment_order", order.orderId).putString("pending_payment_token",
                            requireContext().getSharedPreferences("auth_prefs", 0).getString("auth_token", "")).apply();
            com.razorpay.Checkout checkout = new com.razorpay.Checkout(); checkout.setKeyID(order.keyId);
            org.json.JSONObject options = new org.json.JSONObject();
            options.put("name", "AgriTech Marketplace"); options.put("description", listing.cropNameHindi + " • " + quantity + " " + listing.unit);
            options.put("order_id", order.orderId); options.put("amount", order.amount); options.put("currency", order.currency);
            options.put("theme.color", "#2E7D32");
            String phone = requireContext().getSharedPreferences("auth_prefs", 0).getString("phone_number", "");
            if (!phone.isEmpty()) { org.json.JSONObject prefill = new org.json.JSONObject(); prefill.put("contact", phone); options.put("prefill", prefill); }
            checkout.open(requireActivity(), options);
        } catch (Exception e) { Log.e(TAG, "Unable to open payment checkout", e); Toast.makeText(requireContext(), "भुगतान विंडो नहीं खुल सकी", Toast.LENGTH_SHORT).show(); }
    }
    
    private void showSellDialog(MarketplaceService.BuyRequest request) {
        String message = String.format("💰 बिक्री शुरू करें\n\n" +
                "फसल: %s\n" +
                "आवश्यक मात्रा: %s %s\n" +
                "अधिकतम मूल्य: ₹%.0f/%s\n" +
                "खरीददार: %s\n\n" +
                "क्या आप इस अनुरोध को पूरा कर सकते हैं?", 
                request.cropNameHindi, request.requiredQuantity, request.unit,
                request.maxPricePerUnit, request.unit, request.buyerNameHindi);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("💰 बिक्री")
               .setMessage(message)
               .setPositiveButton("हाँ, बेच सकता हूँ", (dialog, which) -> {
                   showSellForm(request);
                   dialog.dismiss();
               })
               .setNegativeButton("नहीं", (dialog, which) -> dialog.dismiss())
               .show();
    }
    
    private void showSellForm(MarketplaceService.BuyRequest request) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("💰 बिक्री फॉर्म");

        // Create custom view for sell form
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView instructionText = new TextView(requireContext());
        instructionText.setText("बिक्री के लिए अपनी जानकारी दर्ज करें:");
        instructionText.setTextSize(16);
        instructionText.setPadding(0, 0, 0, 16);
        layout.addView(instructionText);

        // Quantity input
        TextView quantityLabel = new TextView(requireContext());
        quantityLabel.setText("उपलब्ध मात्रा (" + request.unit + " में):");
        quantityLabel.setTextSize(14);
        quantityLabel.setPadding(0, 8, 0, 4);
        layout.addView(quantityLabel);

        EditText quantityInput = new EditText(requireContext());
        quantityInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        quantityInput.setHint("मात्रा दर्ज करें");
        quantityInput.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        layout.addView(quantityInput);

        // Price input
        TextView priceLabel = new TextView(requireContext());
        priceLabel.setText("मूल्य (" + request.unit + " में):");
        priceLabel.setTextSize(14);
        priceLabel.setPadding(0, 16, 0, 4);
        layout.addView(priceLabel);

        EditText priceInput = new EditText(requireContext());
        priceInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        priceInput.setHint("मूल्य दर्ज करें");
        priceInput.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        layout.addView(priceInput);

        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setPositiveButton("बिक्री पेश करें", (dialog, which) -> {
            String quantity = quantityInput.getText().toString();
            String price = priceInput.getText().toString();
            
            if (quantity.isEmpty() || price.isEmpty()) {
                Toast.makeText(requireContext(), "कृपया सभी फील्ड भरें", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Toast.makeText(requireContext(), "बिक्री पेश की गई! खरीददार से संपर्क करें", Toast.LENGTH_LONG).show();
        });

        builder.setNegativeButton("रद्द करें", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
    
    private void setupClickListeners() {
        btnListCrop.setOnClickListener(v -> showListCropDialog());
        btnCreateBuyRequest.setOnClickListener(v -> showCreateBuyRequestDialog());
        btnRefresh.setOnClickListener(v -> loadMarketplaceData());
    }
    
    private void showListCropDialog() {
        showAddCropListingDialog();
    }
    
    private void showCreateBuyRequestDialog() {
        showCreateBuyRequestDialogInternal();
    }
    
    private void showAddCropListingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("🌾 फसल सूचीबद्ध करें");
        
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_crop_listing, null);
        builder.setView(dialogView);
        
        // Initialize views
        AutoCompleteTextView spinnerCrop = dialogView.findViewById(R.id.spinner_crop);
        EditText etQuantity = dialogView.findViewById(R.id.et_quantity);
        AutoCompleteTextView spinnerQuality = dialogView.findViewById(R.id.spinner_quality);
        EditText etPrice = dialogView.findViewById(R.id.et_price);
        EditText etLocation = dialogView.findViewById(R.id.et_location);
        EditText etDescription = dialogView.findViewById(R.id.et_description);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        MaterialButton btnAddListing = dialogView.findViewById(R.id.btn_add_listing);
        
        // Setup spinners
        setupCropSpinner(spinnerCrop);
        setupQualitySpinner(spinnerQuality);
        
        // Set default location
        etLocation.setText(selectedDistrict);
        
        AlertDialog dialog = builder.create();
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnAddListing.setOnClickListener(v -> {
            if (validateCropListingInput(spinnerCrop, etQuantity, spinnerQuality, etPrice, etLocation)) {
                addCropListing(spinnerCrop, etQuantity, spinnerQuality, etPrice, etLocation, etDescription);
                dialog.dismiss();
            }
        });
        
        dialog.show();
    }
    
    private void showCreateBuyRequestDialogInternal() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("🛒 खरीद अनुरोध बनाएं");
        
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_buy_request, null);
        builder.setView(dialogView);
        
        // Initialize views
        AutoCompleteTextView spinnerCrop = dialogView.findViewById(R.id.spinner_crop);
        EditText etQuantity = dialogView.findViewById(R.id.et_quantity);
        EditText etMaxPrice = dialogView.findViewById(R.id.et_max_price);
        EditText etLocation = dialogView.findViewById(R.id.et_location);
        AutoCompleteTextView spinnerUrgency = dialogView.findViewById(R.id.spinner_urgency);
        AutoCompleteTextView spinnerDelivery = dialogView.findViewById(R.id.spinner_delivery);
        EditText etRequiredDate = dialogView.findViewById(R.id.et_required_date);
        EditText etDescription = dialogView.findViewById(R.id.et_description);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        MaterialButton btnCreateRequest = dialogView.findViewById(R.id.btn_create_request);
        
        // Setup spinners
        setupCropSpinner(spinnerCrop);
        setupUrgencySpinner(spinnerUrgency);
        setupDeliverySpinner(spinnerDelivery);
        
        // Set default location
        etLocation.setText(selectedDistrict);
        
        // Setup date picker
        etRequiredDate.setOnClickListener(v -> showDatePicker(etRequiredDate));
        
        AlertDialog dialog = builder.create();
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnCreateRequest.setOnClickListener(v -> {
            if (validateBuyRequestInput(spinnerCrop, etQuantity, etMaxPrice, etLocation, spinnerUrgency, spinnerDelivery, etRequiredDate)) {
                createBuyRequest(spinnerCrop, etQuantity, etMaxPrice, etLocation, spinnerUrgency, spinnerDelivery, etRequiredDate, etDescription);
                dialog.dismiss();
            }
        });
        
        dialog.show();
    }
    
    private void setupCropSpinner(AutoCompleteTextView spinner) {
        String[] crops = {
            "गेहूँ (Wheat)", "धान (Rice)", "मक्का (Maize)", "आलू (Potato)", 
            "प्याज़ (Onion)", "टमाटर (Tomato)", "अंगूर (Grapes)", "दलहन (Pulses)",
            "तिलहन (Oilseeds)", "गन्ना (Sugarcane)", "कपास (Cotton)", "जूट (Jute)"
        };
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), 
            android.R.layout.simple_dropdown_item_1line, crops);
        spinner.setAdapter(adapter);
        spinner.setText(crops[0], false);
    }
    
    private void setupQualitySpinner(AutoCompleteTextView spinner) {
        String[] qualities = {"A Grade", "B Grade", "C Grade", "Premium", "Organic"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), 
            android.R.layout.simple_dropdown_item_1line, qualities);
        spinner.setAdapter(adapter);
        spinner.setText(qualities[0], false);
    }
    
    private void setupUrgencySpinner(AutoCompleteTextView spinner) {
        String[] urgencies = {"low", "medium", "high"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), 
            android.R.layout.simple_dropdown_item_1line, urgencies);
        spinner.setAdapter(adapter);
        spinner.setText(urgencies[1], false);
    }
    
    private void setupDeliverySpinner(AutoCompleteTextView spinner) {
        String[] deliveries = {"pickup", "delivery", "both"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), 
            android.R.layout.simple_dropdown_item_1line, deliveries);
        spinner.setAdapter(adapter);
        spinner.setText(deliveries[2], false);
    }
    
    private void showDatePicker(EditText dateEditText) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 7); // Default to 7 days from now
        
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            requireContext(),
            (view, year, month, dayOfMonth) -> {
                Calendar selectedDate = Calendar.getInstance();
                selectedDate.set(year, month, dayOfMonth);
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                dateEditText.setText(sdf.format(selectedDate.getTime()));
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
        datePickerDialog.show();
    }
    
    private boolean validateCropListingInput(AutoCompleteTextView cropSpinner, EditText quantity, 
                                           AutoCompleteTextView quality, EditText price, EditText location) {
        if (cropSpinner.getText().toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "कृपया फसल चुनें", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (quantity.getText().toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "कृपया मात्रा दर्ज करें", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (price.getText().toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "कृपया मूल्य दर्ज करें", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (location.getText().toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "कृपया स्थान दर्ज करें", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        return true;
    }
    
    private boolean validateBuyRequestInput(AutoCompleteTextView cropSpinner, EditText quantity, 
                                          EditText maxPrice, EditText location, AutoCompleteTextView urgency,
                                          AutoCompleteTextView delivery, EditText requiredDate) {
        if (cropSpinner.getText().toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "कृपया फसल चुनें", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (quantity.getText().toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "कृपया मात्रा दर्ज करें", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (maxPrice.getText().toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "कृपया अधिकतम मूल्य दर्ज करें", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (location.getText().toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "कृपया स्थान दर्ज करें", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (requiredDate.getText().toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "कृपया आवश्यक तिथि दर्ज करें", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        return true;
    }
    
    private void addCropListing(AutoCompleteTextView cropSpinner, EditText quantity, 
                               AutoCompleteTextView quality, EditText price, EditText location, 
                               EditText description) {
        try {
            String cropName = extractCropName(cropSpinner.getText().toString());
            double cropQuantity = Double.parseDouble(quantity.getText().toString());
            String cropQuality = quality.getText().toString();
            double cropPrice = Double.parseDouble(price.getText().toString());
            String cropLocation = location.getText().toString();
            String cropDescription = description.getText().toString();
            
            // Get current user info (in real app, this would come from authentication)
            String currentUserId = "user_" + System.currentTimeMillis();
            String currentUserName = "किसान जी";
            String currentUserPhone = "+919876543210";
            
            marketplaceService.createCropListing(
                currentUserId, cropName, cropQuantity, cropQuality, cropPrice,
                cropLocation, selectedState, 0.0, 0.0, cropDescription,
                new MarketplaceService.ListingCallback() {
                    @Override
                    public void onListingCreated(String listingId) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "फसल सफलतापूर्वक सूचीबद्ध की गई!", Toast.LENGTH_LONG).show();
                            loadMarketplaceData(); // Refresh the marketplace
                        });
                    }
                    
                    @Override
                    public void onListingError(String error) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "त्रुटि: " + error, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            );
            
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "कृपया सही संख्या दर्ज करें", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "त्रुटि: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void createBuyRequest(AutoCompleteTextView cropSpinner, EditText quantity, 
                                 EditText maxPrice, EditText location, AutoCompleteTextView urgency,
                                 AutoCompleteTextView delivery, EditText requiredDate, EditText description) {
        try {
            String cropName = extractCropName(cropSpinner.getText().toString());
            double cropQuantity = Double.parseDouble(quantity.getText().toString());
            double cropMaxPrice = Double.parseDouble(maxPrice.getText().toString());
            String cropLocation = location.getText().toString();
            String cropUrgency = urgency.getText().toString();
            String cropDelivery = delivery.getText().toString();
            String cropDescription = description.getText().toString();
            
            // Parse required date
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date date = sdf.parse(requiredDate.getText().toString());
            long requiredByDate = date.getTime();
            
            // Get current user info (in real app, this would come from authentication)
            String currentUserId = "user_" + System.currentTimeMillis();
            String currentUserName = "खरीददार जी";
            String currentUserPhone = "+919876543210";
            
            marketplaceService.createBuyRequest(
                currentUserId, cropName, cropQuantity, cropMaxPrice, cropLocation,
                selectedState, 0.0, 0.0, cropUrgency, cropDescription, cropDelivery, requiredByDate,
                new MarketplaceService.BuyRequestCallback() {
                    @Override
                    public void onBuyRequestCreated(String requestId) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "खरीद अनुरोध सफलतापूर्वक बनाया गया!", Toast.LENGTH_LONG).show();
                            loadMarketplaceData(); // Refresh the marketplace
                        });
                    }
                    
                    @Override
                    public void onBuyRequestError(String error) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "त्रुटि: " + error, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            );
            
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "कृपया सही संख्या दर्ज करें", Toast.LENGTH_SHORT).show();
        } catch (ParseException e) {
            Toast.makeText(requireContext(), "कृपया सही तिथि दर्ज करें", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "त्रुटि: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private String extractCropName(String cropFilter) {
        if (cropFilter.contains("(")) {
            return cropFilter.substring(cropFilter.indexOf("(") + 1, cropFilter.indexOf(")")).toLowerCase();
        }
        return cropFilter.toLowerCase();
    }
}
