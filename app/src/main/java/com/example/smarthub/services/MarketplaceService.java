package com.example.smarthub.services;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class MarketplaceService {
    private static final String TAG = "MarketplaceService";
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    private final Context context;
    private final Random random;
    
    // Real-time data storage
    private final Map<String, List<CropListing>> listingsCache;
    private final Map<String, List<BuyRequest>> buyRequestsCache;
    private final Map<String, List<MarketStats>> marketStatsCache;
    private final Map<String, UserProfile> userProfiles;
    private final Map<String, List<Transaction>> transactions;
    
    // Real-time update listeners
    private final List<MarketplaceUpdateListener> updateListeners;
    private final android.content.SharedPreferences localStore;
    private final Gson gson = new Gson();
    
    // Singleton instance
    private static MarketplaceService instance;
    
    public static synchronized MarketplaceService getInstance(Context context) {
        if (instance == null) {
            instance = new MarketplaceService(context);
        }
        return instance;
    }

    public interface MarketplaceCallback {
        void onListingsReceived(List<CropListing> listings);
        void onBuyRequestsReceived(List<BuyRequest> buyRequests);
        void onMarketStatsReceived(MarketStats stats);
        void onError(String error);
    }

    public interface ListingCallback {
        void onListingCreated(String listingId);
        void onListingError(String error);
    }
    
    public interface BuyRequestCallback {
        void onBuyRequestCreated(String requestId);
        void onBuyRequestError(String error);
    }
    
    public interface TransactionCallback {
        void onTransactionSuccess(String transactionId);
        void onTransactionError(String error);
    }
    
    public interface MarketplaceUpdateListener {
        void onNewListing(CropListing listing);
        void onNewBuyRequest(BuyRequest request);
        void onListingUpdated(CropListing listing);
        void onBuyRequestUpdated(BuyRequest request);
        void onTransactionCompleted(Transaction transaction);
    }

    public static class CropListing {
        public final String listingId;
        public final String farmerId;
        public final String farmerName;
        public final String farmerNameHindi;
        public final String farmerPhone;
        public final String cropName;
        public final String cropNameHindi;
        public final double quantity;
        public final String unit;
        public final String quality;
        public final double pricePerUnit;
        public final String location;
        public final String state;
        public final double latitude;
        public final double longitude;
        public final long listingDate;
        public final String status; // "active", "sold", "expired"
        public final String description;
        public final List<String> images;
        public final int viewCount;
        public final int contactCount;

        public CropListing(String farmerId, String farmerName, String farmerNameHindi, String farmerPhone,
                          String cropName, String cropNameHindi, double quantity, String unit, 
                          String quality, double pricePerUnit, String location, String state, 
                          double latitude, double longitude, String description) {
            this.listingId = generateListingId();
            this.farmerId = farmerId;
            this.farmerName = farmerName;
            this.farmerNameHindi = farmerNameHindi;
            this.farmerPhone = farmerPhone;
            this.cropName = cropName;
            this.cropNameHindi = cropNameHindi;
            this.quantity = quantity;
            this.unit = unit;
            this.quality = quality;
            this.pricePerUnit = pricePerUnit;
            this.location = location;
            this.state = state;
            this.latitude = latitude;
            this.longitude = longitude;
            this.listingDate = System.currentTimeMillis();
            this.status = "active";
            this.description = description;
            this.images = new ArrayList<>();
            this.viewCount = 0;
            this.contactCount = 0;
        }

        private String generateListingId() {
            return "LIST_" + System.currentTimeMillis() + "_" + (new Random()).nextInt(1000);
        }
    }

    public static class BuyRequest {
        public final String requestId;
        public final String buyerId;
        public final String buyerName;
        public final String buyerNameHindi;
        public final String buyerPhone;
        public final String cropName;
        public final String cropNameHindi;
        public final double requiredQuantity;
        public final String unit;
        public final double maxPricePerUnit;
        public final String location;
        public final String state;
        public final double latitude;
        public final double longitude;
        public final long requestDate;
        public final String urgency; // "low", "medium", "high"
        public final String status; // "open", "matched", "closed"
        public final String description;
        public final String deliveryPreference; // "pickup", "delivery", "both"
        public final long requiredByDate;

        public BuyRequest(String buyerId, String buyerName, String buyerNameHindi, String buyerPhone,
                         String cropName, String cropNameHindi, double requiredQuantity, String unit, 
                         double maxPricePerUnit, String location, String state, double latitude, 
                         double longitude, String urgency, String description, String deliveryPreference, 
                         long requiredByDate) {
            this.requestId = generateRequestId();
            this.buyerId = buyerId;
            this.buyerName = buyerName;
            this.buyerNameHindi = buyerNameHindi;
            this.buyerPhone = buyerPhone;
            this.cropName = cropName;
            this.cropNameHindi = cropNameHindi;
            this.requiredQuantity = requiredQuantity;
            this.unit = unit;
            this.maxPricePerUnit = maxPricePerUnit;
            this.location = location;
            this.state = state;
            this.latitude = latitude;
            this.longitude = longitude;
            this.requestDate = System.currentTimeMillis();
            this.urgency = urgency;
            this.status = "open";
            this.description = description;
            this.deliveryPreference = deliveryPreference;
            this.requiredByDate = requiredByDate;
        }

        private String generateRequestId() {
            return "REQ_" + System.currentTimeMillis() + "_" + (new Random()).nextInt(1000);
        }
    }
    
    public static class UserProfile {
        public final String userId;
        public final String name;
        public final String nameHindi;
        public final String phone;
        public final String location;
        public final String state;
        public final double latitude;
        public final double longitude;
        public final String userType; // "farmer", "buyer", "both"
        public final List<String> crops;
        public final double rating;
        public final int totalTransactions;
        public final long joinDate;
        public final boolean isVerified;
        public final String upiId;

        public UserProfile(String userId, String name, String nameHindi, String phone, 
                          String location, String state, double latitude, double longitude, 
                          String userType, List<String> crops, String upiId) {
            this.userId = userId;
            this.name = name;
            this.nameHindi = nameHindi;
            this.phone = phone;
            this.location = location;
            this.state = state;
            this.latitude = latitude;
            this.longitude = longitude;
            this.userType = userType;
            this.crops = crops;
            this.rating = 5.0;
            this.totalTransactions = 0;
            this.joinDate = System.currentTimeMillis();
            this.isVerified = false;
            this.upiId = upiId;
        }
    }
    
    public static class Transaction {
        public final String transactionId;
        public final String listingId;
        public final String buyerId;
        public final String sellerId;
        public final String cropName;
        public final double quantity;
        public final double totalAmount;
        public final String status; // "pending", "confirmed", "completed", "cancelled"
        public final long transactionDate;
        public final String paymentMethod; // "upi", "cash", "bank_transfer"
        public final String upiTransactionId;
        public final String deliveryAddress;
        public final String deliveryStatus; // "pending", "in_transit", "delivered"

        public Transaction(String listingId, String buyerId, String sellerId, String cropName,
                         double quantity, double totalAmount, String paymentMethod, 
                         String deliveryAddress) {
            this.transactionId = generateTransactionId();
            this.listingId = listingId;
            this.buyerId = buyerId;
            this.sellerId = sellerId;
            this.cropName = cropName;
            this.quantity = quantity;
            this.totalAmount = totalAmount;
            this.status = "pending";
            this.transactionDate = System.currentTimeMillis();
            this.paymentMethod = paymentMethod;
            this.upiTransactionId = null;
            this.deliveryAddress = deliveryAddress;
            this.deliveryStatus = "pending";
        }

        private String generateTransactionId() {
            return "TXN_" + System.currentTimeMillis() + "_" + (new Random()).nextInt(1000);
        }
    }

    public static class MarketStats {
        public final String state;
        public final String stateHindi;
        public final int totalListings;
        public final int totalBuyRequests;
        public final double averagePrice;
        public final String mostDemandedCrop;
        public final String mostDemandedCropHindi;
        public final String marketTrend; // "bullish", "bearish", "stable"
        public final long lastUpdated;
        public final Map<String, Double> cropPrices;
        public final int activeUsers;
        public final int totalTransactions;

        public MarketStats(String state, String stateHindi, int totalListings, int totalBuyRequests,
                          double averagePrice, String mostDemandedCrop, String mostDemandedCropHindi, 
                          String marketTrend, Map<String, Double> cropPrices, int activeUsers, 
                          int totalTransactions) {
            this.state = state;
            this.stateHindi = stateHindi;
            this.totalListings = totalListings;
            this.totalBuyRequests = totalBuyRequests;
            this.averagePrice = averagePrice;
            this.mostDemandedCrop = mostDemandedCrop;
            this.mostDemandedCropHindi = mostDemandedCropHindi;
            this.marketTrend = marketTrend;
            this.lastUpdated = System.currentTimeMillis();
            this.cropPrices = cropPrices;
            this.activeUsers = activeUsers;
            this.totalTransactions = totalTransactions;
        }
    }

    public MarketplaceService(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        this.scheduledExecutor = Executors.newScheduledThreadPool(1);
        this.random = new Random();
        
        // Initialize real-time data storage
        this.listingsCache = new ConcurrentHashMap<>();
        this.buyRequestsCache = new ConcurrentHashMap<>();
        this.marketStatsCache = new ConcurrentHashMap<>();
        this.userProfiles = new ConcurrentHashMap<>();
        this.transactions = new ConcurrentHashMap<>();
        this.updateListeners = new ArrayList<>();
        this.localStore = context.getApplicationContext().getSharedPreferences("marketplace_local", Context.MODE_PRIVATE);
        
        // Seed demo data only on first run; local records survive restarts.
        if (localStore.contains("listings")) restorePersistedData();
        else initializeMarketplaceData();
        
        // Start periodic updates
        startPeriodicUpdates();
    }
    
    // Real-time update methods
    public void addUpdateListener(MarketplaceUpdateListener listener) {
        if (!updateListeners.contains(listener)) {
            updateListeners.add(listener);
        }
    }
    
    public void removeUpdateListener(MarketplaceUpdateListener listener) {
        updateListeners.remove(listener);
    }
    
    private void notifyNewListing(CropListing listing) {
        for (MarketplaceUpdateListener listener : updateListeners) {
            try {
                listener.onNewListing(listing);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener: " + e.getMessage(), e);
            }
        }
    }
    
    private void notifyNewBuyRequest(BuyRequest request) {
        for (MarketplaceUpdateListener listener : updateListeners) {
            try {
                listener.onNewBuyRequest(request);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener: " + e.getMessage(), e);
            }
        }
    }
    
    private void notifyTransactionCompleted(Transaction transaction) {
        for (MarketplaceUpdateListener listener : updateListeners) {
            try {
                listener.onTransactionCompleted(transaction);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener: " + e.getMessage(), e);
            }
        }
    }

    private void restorePersistedData() {
        java.lang.reflect.Type listingType = new TypeToken<List<CropListing>>() {}.getType();
        java.lang.reflect.Type requestType = new TypeToken<List<BuyRequest>>() {}.getType();
        List<CropListing> listings = gson.fromJson(localStore.getString("listings", "[]"), listingType);
        List<BuyRequest> requests = gson.fromJson(localStore.getString("buy_requests", "[]"), requestType);
        if (listings != null) for (CropListing item : listings) listingsCache.computeIfAbsent(item.state.toLowerCase(), k -> new ArrayList<>()).add(item);
        if (requests != null) for (BuyRequest item : requests) buyRequestsCache.computeIfAbsent(item.state.toLowerCase(), k -> new ArrayList<>()).add(item);
    }

    private void persistMarketplaceData() {
        List<CropListing> listings = new ArrayList<>();
        for (List<CropListing> items : listingsCache.values()) listings.addAll(items);
        List<BuyRequest> requests = new ArrayList<>();
        for (List<BuyRequest> items : buyRequestsCache.values()) requests.addAll(items);
        localStore.edit().putString("listings", gson.toJson(listings)).putString("buy_requests", gson.toJson(requests)).apply();
    }

    // User management methods
    public void createUserProfile(String userId, String name, String nameHindi, String phone,
                                 String location, String state, double latitude, double longitude,
                                 String userType, List<String> crops, String upiId) {
        UserProfile profile = new UserProfile(userId, name, nameHindi, phone, location, state,
                                            latitude, longitude, userType, crops, upiId);
        userProfiles.put(userId, profile);
        Log.d(TAG, "User profile created: " + userId);
    }
    
    public UserProfile getUserProfile(String userId) {
        return userProfiles.get(userId);
    }
    
    public void updateUserProfile(String userId, String location, String state, 
                                 double latitude, double longitude, List<String> crops, String upiId) {
        UserProfile existing = userProfiles.get(userId);
        if (existing != null) {
            UserProfile updated = new UserProfile(userId, existing.name, existing.nameHindi, 
                                                existing.phone, location, state, latitude, longitude,
                                                existing.userType, crops, upiId);
            userProfiles.put(userId, updated);
            Log.d(TAG, "User profile updated: " + userId);
        }
    }

    // Enhanced listing methods
    public void createCropListing(String farmerId, String cropName, double quantity, String quality,
                                 double pricePerUnit, String location, String state, 
                                 double latitude, double longitude, String description, ListingCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Creating crop listing for farmer: " + farmerId + " in " + state);
                
                UserProfile farmer = userProfiles.get(farmerId);
                if (farmer == null) {
                    callback.onListingError("User profile not found");
                    return;
                }
                
                // Simulate network delay
                Thread.sleep(1000);
                
                // Create new listing
                CropListing listing = new CropListing(
                    farmerId, farmer.name, farmer.nameHindi, farmer.phone,
                    cropName, getCropNameHindi(cropName),
                    quantity, "quintal", quality, pricePerUnit,
                    location, state, latitude, longitude, description
                );
                
                // Add to cache
                String stateKey = state.toLowerCase();
                if (!listingsCache.containsKey(stateKey)) {
                    listingsCache.put(stateKey, new ArrayList<>());
                }
                listingsCache.get(stateKey).add(listing);
                persistMarketplaceData();
                
                // Notify real-time listeners
                notifyNewListing(listing);
                
                callback.onListingCreated(listing.listingId);
                
            } catch (Exception e) {
                Log.e(TAG, "Error creating listing: " + e.getMessage(), e);
                callback.onListingError("लिस्टिंग बनाने में त्रुटि: " + e.getMessage());
            }
        });
    }
    
    public void createBuyRequest(String buyerId, String cropName, double requiredQuantity,
                                double maxPricePerUnit, String location, String state,
                                double latitude, double longitude, String urgency, 
                                String description, String deliveryPreference, long requiredByDate,
                                BuyRequestCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Creating buy request for buyer: " + buyerId + " in " + state);
                
                UserProfile buyer = userProfiles.get(buyerId);
                if (buyer == null) {
                    callback.onBuyRequestError("User profile not found");
                    return;
                }
                
                // Simulate network delay
                Thread.sleep(800);
                
                // Create new buy request
                BuyRequest request = new BuyRequest(
                    buyerId, buyer.name, buyer.nameHindi, buyer.phone,
                    cropName, getCropNameHindi(cropName),
                    requiredQuantity, "quintal", maxPricePerUnit,
                    location, state, latitude, longitude, urgency,
                    description, deliveryPreference, requiredByDate
                );
                
                // Add to cache
                String stateKey = state.toLowerCase();
                if (!buyRequestsCache.containsKey(stateKey)) {
                    buyRequestsCache.put(stateKey, new ArrayList<>());
                }
                buyRequestsCache.get(stateKey).add(request);
                persistMarketplaceData();
                
                // Notify real-time listeners
                notifyNewBuyRequest(request);
                
                callback.onBuyRequestCreated(request.requestId);
                
            } catch (Exception e) {
                Log.e(TAG, "Error creating buy request: " + e.getMessage(), e);
                callback.onBuyRequestError("खरीद अनुरोध बनाने में त्रुटि: " + e.getMessage());
            }
        });
    }
    
    public void createTransaction(String listingId, String buyerId, String paymentMethod,
                                String deliveryAddress, TransactionCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Creating transaction for listing: " + listingId);
                
                // Find the listing
                CropListing listing = findListingById(listingId);
                if (listing == null) {
                    callback.onTransactionError("Listing not found");
                    return;
                }
                
                // Calculate total amount
                double totalAmount = listing.quantity * listing.pricePerUnit;
                
                // Create transaction
                Transaction transaction = new Transaction(
                    listingId, buyerId, listing.farmerId, listing.cropName,
                    listing.quantity, totalAmount, paymentMethod, deliveryAddress
                );
                
                // Store transaction
                if (!transactions.containsKey(buyerId)) {
                    transactions.put(buyerId, new ArrayList<>());
                }
                transactions.get(buyerId).add(transaction);
                
                // Update listing status
                updateListingStatus(listingId, "sold");
                
                // Notify real-time listeners
                notifyTransactionCompleted(transaction);
                
                callback.onTransactionSuccess(transaction.transactionId);
                
            } catch (Exception e) {
                Log.e(TAG, "Error creating transaction: " + e.getMessage(), e);
                callback.onTransactionError("लेनदेन बनाने में त्रुटि: " + e.getMessage());
            }
        });
    }
    
    private CropListing findListingById(String listingId) {
        for (List<CropListing> listings : listingsCache.values()) {
            for (CropListing listing : listings) {
                if (listing.listingId.equals(listingId)) {
                    return listing;
                }
            }
        }
        return null;
    }
    
    private void updateListingStatus(String listingId, String status) {
        for (List<CropListing> listings : listingsCache.values()) {
            for (CropListing listing : listings) {
                if (listing.listingId.equals(listingId)) {
                    // Create updated listing with new status
                    CropListing updatedListing = new CropListing(
                        listing.farmerId, listing.farmerName, listing.farmerNameHindi, listing.farmerPhone,
                        listing.cropName, listing.cropNameHindi, listing.quantity, listing.unit,
                        listing.quality, listing.pricePerUnit, listing.location, listing.state,
                        listing.latitude, listing.longitude, listing.description
                    );
                    
                    // Replace in list
                    int index = listings.indexOf(listing);
                    if (index != -1) {
                        listings.set(index, updatedListing);
                    }
                    break;
                }
            }
        }
    }

    // Enhanced data retrieval methods
    public void getCropListings(String cropFilter, String stateFilter, String districtFilter, MarketplaceCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Fetching crop listings with filters: " + cropFilter + ", " + stateFilter + ", " + districtFilter);
                
                // Simulate network delay
                Thread.sleep(600);
                
                List<CropListing> allListings = new ArrayList<>();
                
                // Get listings for the state
                if (stateFilter != null && !stateFilter.isEmpty()) {
                    String stateKey = stateFilter.toLowerCase();
                    List<CropListing> stateListings = listingsCache.get(stateKey);
                    if (stateListings != null) {
                        allListings.addAll(stateListings);
                    }
                } else {
                    // Get all listings
                    for (List<CropListing> listings : listingsCache.values()) {
                        allListings.addAll(listings);
                    }
                }
                
                // Filter by crop if specified
                if (cropFilter != null && !cropFilter.isEmpty() && !cropFilter.equals("सभी फसलें")) {
                    String cropName = extractCropName(cropFilter);
                    allListings = allListings.stream()
                        .filter(listing -> listing.cropName.equals(cropName))
                        .collect(java.util.stream.Collectors.toList());
                }
                
                // Filter by district if specified
                if (districtFilter != null && !districtFilter.isEmpty()) {
                    allListings = allListings.stream()
                        .filter(listing -> listing.location.equals(districtFilter))
                        .collect(java.util.stream.Collectors.toList());
                }
                
                // Filter only active listings
                allListings = allListings.stream()
                    .filter(listing -> "active".equals(listing.status))
                    .collect(java.util.stream.Collectors.toList());
                
                callback.onListingsReceived(allListings);
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching crop listings: " + e.getMessage(), e);
                callback.onError("फसल सूची प्राप्त करने में त्रुटि: " + e.getMessage());
            }
        });
    }
    
    public void getBuyRequests(String stateFilter, String cropFilter, MarketplaceCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Fetching buy requests with filters: " + stateFilter + ", " + cropFilter);
                
                // Simulate network delay
                Thread.sleep(500);
                
                List<BuyRequest> allRequests = new ArrayList<>();
                
                // Get requests for the state
                if (stateFilter != null && !stateFilter.isEmpty()) {
                    String stateKey = stateFilter.toLowerCase();
                    List<BuyRequest> stateRequests = buyRequestsCache.get(stateKey);
                    if (stateRequests != null) {
                        allRequests.addAll(stateRequests);
                    }
                } else {
                    // Get all requests
                    for (List<BuyRequest> requests : buyRequestsCache.values()) {
                        allRequests.addAll(requests);
                    }
                }
                
                // Filter by crop if specified
                if (cropFilter != null && !cropFilter.isEmpty() && !cropFilter.equals("सभी फसलें")) {
                    String cropName = extractCropName(cropFilter);
                    allRequests = allRequests.stream()
                        .filter(request -> request.cropName.equals(cropName))
                        .collect(java.util.stream.Collectors.toList());
                }
                
                // Filter only open requests
                allRequests = allRequests.stream()
                    .filter(request -> "open".equals(request.status))
                    .collect(java.util.stream.Collectors.toList());
                
                callback.onBuyRequestsReceived(allRequests);
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching buy requests: " + e.getMessage(), e);
                callback.onError("खरीद अनुरोध प्राप्त करने में त्रुटि: " + e.getMessage());
            }
        });
    }
    
    public void getMarketStats(String state, MarketplaceCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Fetching market stats for state: " + state);
                
                // Simulate processing time
                Thread.sleep(400);
                
                // Get market stats for the state
                List<MarketStats> statsList = marketStatsCache.get(state.toLowerCase());
                MarketStats stats = null;
                if (statsList == null || statsList.isEmpty()) {
                    stats = generateMarketStats(state);
                    List<MarketStats> newStatsList = new ArrayList<>();
                    newStatsList.add(stats);
                    marketStatsCache.put(state.toLowerCase(), newStatsList);
                } else {
                    stats = statsList.get(0);
                }
                
                callback.onMarketStatsReceived(stats);
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching market stats: " + e.getMessage(), e);
                callback.onError("बाजार आंकड़े प्राप्त करने में त्रुटि: " + e.getMessage());
            }
        });
    }
    
    public void getUserListings(String userId, MarketplaceCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Fetching user listings for: " + userId);
                
                List<CropListing> userListings = new ArrayList<>();
                
                // Search through all listings
                for (List<CropListing> listings : listingsCache.values()) {
                    for (CropListing listing : listings) {
                        if (listing.farmerId.equals(userId)) {
                            userListings.add(listing);
                        }
                    }
                }
                
                callback.onListingsReceived(userListings);
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching user listings: " + e.getMessage(), e);
                callback.onError("उपयोगकर्ता सूची प्राप्त करने में त्रुटि: " + e.getMessage());
            }
        });
    }
    
    public void getUserBuyRequests(String userId, MarketplaceCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Fetching user buy requests for: " + userId);
                
                List<BuyRequest> userRequests = new ArrayList<>();
                
                // Search through all buy requests
                for (List<BuyRequest> requests : buyRequestsCache.values()) {
                    for (BuyRequest request : requests) {
                        if (request.buyerId.equals(userId)) {
                            userRequests.add(request);
                        }
                    }
                }
                
                callback.onBuyRequestsReceived(userRequests);
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching user buy requests: " + e.getMessage(), e);
                callback.onError("उपयोगकर्ता खरीद अनुरोध प्राप्त करने में त्रुटि: " + e.getMessage());
            }
        });
    }
    
    public void getUserTransactions(String userId, MarketplaceCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Fetching user transactions for: " + userId);
                
                List<Transaction> userTransactions = transactions.get(userId);
                if (userTransactions == null) {
                    userTransactions = new ArrayList<>();
                }
                
                // Convert transactions to a format that can be displayed
                // For now, we'll just return empty lists for the callback
                callback.onListingsReceived(new ArrayList<>());
                callback.onBuyRequestsReceived(new ArrayList<>());
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching user transactions: " + e.getMessage(), e);
                callback.onError("उपयोगकर्ता लेनदेन प्राप्त करने में त्रुटि: " + e.getMessage());
            }
        });
    }

    // Helper methods
    private String extractCropName(String cropFilter) {
        if (cropFilter.contains("(")) {
            return cropFilter.substring(cropFilter.indexOf("(") + 1, cropFilter.indexOf(")")).toLowerCase();
        }
        return cropFilter.toLowerCase();
    }
    
    private String getCropNameHindi(String cropName) {
        Map<String, String> cropNames = new HashMap<>();
        cropNames.put("wheat", "गेहूँ");
        cropNames.put("rice", "धान");
        cropNames.put("maize", "मक्का");
        cropNames.put("potato", "आलू");
        cropNames.put("onion", "प्याज़");
        cropNames.put("tomato", "टमाटर");
        cropNames.put("grapes", "अंगूर");
        cropNames.put("pulses", "दलहन");
        cropNames.put("oilseeds", "तिलहन");
        cropNames.put("sugarcane", "गन्ना");
        cropNames.put("cotton", "कपास");
        cropNames.put("jute", "जूट");
        
        return cropNames.getOrDefault(cropName.toLowerCase(), cropName);
    }
    
    private String getFarmerNameHindi(String farmerName) {
        // Simple conversion - in real app, this would come from user profile
        return farmerName;
    }
    
    private MarketStats generateMarketStats(String state) {
        String stateHindi = getStateNameHindi(state);
        
        // Generate realistic stats based on state
        int totalListings = 25 + random.nextInt(50);
        int totalBuyRequests = 15 + random.nextInt(30);
        double averagePrice = 1500 + random.nextInt(1000);
        
        String[] crops = {"wheat", "rice", "maize", "potato", "onion"};
        String mostDemandedCrop = crops[random.nextInt(crops.length)];
        String mostDemandedCropHindi = getCropNameHindi(mostDemandedCrop);
        
        String[] trends = {"bullish", "bearish", "stable"};
        String marketTrend = trends[random.nextInt(trends.length)];
        
        Map<String, Double> cropPrices = new HashMap<>();
        cropPrices.put("wheat", 1900.0 + random.nextInt(200));
        cropPrices.put("rice", 2100.0 + random.nextInt(300));
        cropPrices.put("maize", 1800.0 + random.nextInt(200));
        cropPrices.put("potato", 1200.0 + random.nextInt(300));
        cropPrices.put("onion", 2500.0 + random.nextInt(500));
        
        int activeUsers = 100 + random.nextInt(200);
        int totalTransactions = 50 + random.nextInt(100);
        
        return new MarketStats(state, stateHindi, totalListings, totalBuyRequests, averagePrice,
                              mostDemandedCrop, mostDemandedCropHindi, marketTrend, cropPrices,
                              activeUsers, totalTransactions);
    }
    
    private String getStateNameHindi(String state) {
        Map<String, String> stateNames = new HashMap<>();
        stateNames.put("bihar", "बिहार");
        stateNames.put("up", "उत्तर प्रदेश");
        stateNames.put("mp", "मध्य प्रदेश");
        stateNames.put("jharkhand", "झारखंड");
        stateNames.put("west bengal", "पश्चिम बंगाल");
        stateNames.put("rajasthan", "राजस्थान");
        stateNames.put("maharashtra", "महाराष्ट्र");
        stateNames.put("karnataka", "कर्नाटक");
        stateNames.put("tamil nadu", "तमिलनाडु");
        stateNames.put("andhra pradesh", "आंध्र प्रदेश");
        
        return stateNames.getOrDefault(state.toLowerCase(), state);
    }

    private void initializeMarketplaceData() {
        // Initialize with sample data for major states
        initializeBiharData();
        initializeUPData();
        initializeMPData();
    }
    
    private void initializeBiharData() {
        List<CropListing> biharListings = new ArrayList<>();
        List<BuyRequest> biharRequests = new ArrayList<>();
        
        // Sample listings
        biharListings.add(new CropListing(
            "farmer_001", "राजेश कुमार", "राजेश कुमार", "+919876543210",
            "maize", "मक्का", 50.0, "quintal", "A Grade", 1840.0,
            "Patna", "Bihar", 25.5941, 85.1376, "उच्च गुणवत्ता वाला मक्का"
        ));
        
        biharListings.add(new CropListing(
            "farmer_002", "सुनील सिंह", "सुनील सिंह", "+919876543211",
            "potato", "आलू", 80.0, "quintal", "A Grade", 1200.0,
            "Ara", "Bihar", 25.5569, 84.6634, "ताज़ा आलू"
        ));
        
        biharListings.add(new CropListing(
            "farmer_003", "अमित कुमार", "अमित कुमार", "+919876543212",
            "wheat", "गेहूँ", 100.0, "quintal", "Premium", 1950.0,
            "Gaya", "Bihar", 24.7914, 85.0002, "प्रीमियम गुणवत्ता गेहूँ"
        ));
        
        // Sample buy requests
        biharRequests.add(new BuyRequest(
            "buyer_001", "राहुल शर्मा", "राहुल शर्मा", "+919876543213",
            "wheat", "गेहूँ", 100.0, "quintal", 1950.0,
            "Patna", "Bihar", 25.5941, 85.1376, "high",
            "तत्काल आवश्यकता", "delivery", System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000
        ));
        
        biharRequests.add(new BuyRequest(
            "buyer_002", "प्रवीण यादव", "प्रवीण यादव", "+919876543214",
            "maize", "मक्का", 50.0, "quintal", 1900.0,
            "Bhagalpur", "Bihar", 25.2445, 87.0052, "medium",
            "मध्यम आवश्यकता", "pickup", System.currentTimeMillis() + 14 * 24 * 60 * 60 * 1000
        ));
        
        listingsCache.put("bihar", biharListings);
        buyRequestsCache.put("bihar", biharRequests);
        
        // Initialize user profiles
        createUserProfile("farmer_001", "राजेश कुमार", "राजेश कुमार", "+919876543210",
                         "Patna", "Bihar", 25.5941, 85.1376, "farmer", 
                         Arrays.asList("maize", "wheat"), "rajesh@upi");
        
        createUserProfile("farmer_002", "सुनील सिंह", "सुनील सिंह", "+919876543211",
                         "Ara", "Bihar", 25.5569, 84.6634, "farmer",
                         Arrays.asList("potato", "onion"), "sunil@upi");
        
        createUserProfile("buyer_001", "राहुल शर्मा", "राहुल शर्मा", "+919876543213",
                         "Patna", "Bihar", 25.5941, 85.1376, "buyer",
                         Arrays.asList("wheat", "rice"), "rahul@upi");
    }
    
    private void initializeUPData() {
        List<CropListing> upListings = new ArrayList<>();
        List<BuyRequest> upRequests = new ArrayList<>();
        
        upListings.add(new CropListing(
            "farmer_004", "विकास वर्मा", "विकास वर्मा", "+919876543215",
            "wheat", "गेहूँ", 120.0, "quintal", "A Grade", 1920.0,
            "Lucknow", "UP", 26.8467, 80.9462, "उच्च गुणवत्ता गेहूँ"
        ));
        
        upRequests.add(new BuyRequest(
            "buyer_003", "अजय सिंह", "अजय सिंह", "+919876543216",
            "rice", "धान", 80.0, "quintal", 2200.0,
            "Kanpur", "UP", 26.4499, 80.3319, "high",
            "तत्काल आवश्यकता", "delivery", System.currentTimeMillis() + 5 * 24 * 60 * 60 * 1000
        ));
        
        listingsCache.put("up", upListings);
        buyRequestsCache.put("up", upRequests);
    }
    
    private void initializeMPData() {
        List<CropListing> mpListings = new ArrayList<>();
        List<BuyRequest> mpRequests = new ArrayList<>();
        
        mpListings.add(new CropListing(
            "farmer_005", "राजेश पटेल", "राजेश पटेल", "+919876543217",
            "soybean", "सोयाबीन", 90.0, "quintal", "A Grade", 2800.0,
            "Bhopal", "MP", 23.2599, 77.4126, "उच्च गुणवत्ता सोयाबीन"
        ));
        
        mpRequests.add(new BuyRequest(
            "buyer_004", "संजय गुप्ता", "संजय गुप्ता", "+919876543218",
            "soybean", "सोयाबीन", 60.0, "quintal", 2900.0,
            "Indore", "MP", 22.7196, 75.8577, "medium",
            "मध्यम आवश्यकता", "pickup", System.currentTimeMillis() + 10 * 24 * 60 * 60 * 1000
        ));
        
        listingsCache.put("mp", mpListings);
        buyRequestsCache.put("mp", mpRequests);
    }

    private void startPeriodicUpdates() {
        // Update market stats every 30 minutes
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                Log.d(TAG, "Updating market stats...");
                updateMarketStats();
            } catch (Exception e) {
                Log.e(TAG, "Error updating market stats: " + e.getMessage(), e);
            }
        }, 30, 30, TimeUnit.MINUTES);
        
        // Clean up expired listings every hour
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                Log.d(TAG, "Cleaning up expired listings...");
                cleanupExpiredListings();
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up expired listings: " + e.getMessage(), e);
            }
        }, 60, 60, TimeUnit.MINUTES);
    }
    
    private void updateMarketStats() {
        // Update market stats for all states
        for (String state : listingsCache.keySet()) {
            MarketStats newStats = generateMarketStats(state);
            List<MarketStats> statsList = new ArrayList<>();
            statsList.add(newStats);
            marketStatsCache.put(state, statsList);
        }
    }
    
    private void cleanupExpiredListings() {
        long currentTime = System.currentTimeMillis();
        long expiryTime = 30 * 24 * 60 * 60 * 1000L; // 30 days
        
        for (List<CropListing> listings : listingsCache.values()) {
            listings.removeIf(listing -> 
                (currentTime - listing.listingDate) > expiryTime && "active".equals(listing.status)
            );
        }
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
            scheduledExecutor.shutdown();
        }
    }
}
