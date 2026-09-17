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
import java.util.Arrays;
import java.util.Locale;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import com.example.smarthub.api.APIService;

public class MandiPriceService {
    private static final String TAG = "MandiPriceService";
    private static final long PRICE_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final Map<String, CachedPrices> OFFICIAL_PRICE_CACHE = new ConcurrentHashMap<>();
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    private final Context context;
    private final Random random;
    private final Map<String, List<MandiPrice>> priceCache;
    private final Map<String, PriceTrend> trendCache;
    private final APIService apiService;

    private static class CachedPrices {
        final long savedAt;
        final List<MandiPrice> prices;
        CachedPrices(List<MandiPrice> prices) { this.savedAt = System.currentTimeMillis(); this.prices = new ArrayList<>(prices); }
    }

    public interface PriceCallback {
        void onPricesReceived(List<MandiPrice> prices);
        void onPriceError(String error);
    }

    public interface TrendCallback {
        void onTrendReceived(PriceTrend trend);
        void onTrendError(String error);
    }

    public interface DistrictsCallback {
        void onDistrictsReceived(List<String> districts);
        void onDistrictsError(String error);
    }

    public static class MandiPrice {
        public final String mandiName;
        public final String mandiNameHindi;
        public final String cropName;
        public final String cropNameHindi;
        public final double price;
        public final String unit;
        public final String quality;
        public final long timestamp;
        public final double previousPrice;
        public final double priceChange;
        public final String priceChangeType; // "up", "down", "stable"
        public final String state;
        public final String feedDate;

        public MandiPrice(String mandiName, String mandiNameHindi, String cropName, String cropNameHindi,
                         double price, String unit, String quality, double previousPrice) {
            this(mandiName, mandiNameHindi, cropName, cropNameHindi, price, unit, quality, previousPrice, null);
        }

        public MandiPrice(String mandiName, String mandiNameHindi, String cropName, String cropNameHindi,
                         double price, String unit, String quality, double previousPrice, String state) {
            this(mandiName, mandiNameHindi, cropName, cropNameHindi, price, unit, quality, previousPrice, state, null);
        }

        public MandiPrice(String mandiName, String mandiNameHindi, String cropName, String cropNameHindi,
                         double price, String unit, String quality, double previousPrice, String state, String feedDate) {
            this.mandiName = mandiName;
            this.mandiNameHindi = mandiNameHindi;
            this.cropName = cropName;
            this.cropNameHindi = cropNameHindi;
            this.price = price;
            this.unit = unit;
            this.quality = quality;
            this.timestamp = System.currentTimeMillis();
            this.previousPrice = previousPrice;
            this.priceChange = price - previousPrice;
            this.priceChangeType = price > previousPrice ? "up" : (price < previousPrice ? "down" : "stable");
            this.state = state;
            this.feedDate = feedDate;
        }
    }

    public static class PriceTrend {
        public final String cropName;
        public final String cropNameHindi;
        public final String trend; // "rising", "falling", "stable"
        public final String trendHindi;
        public final double averagePrice;
        public final double priceChange;
        public final String recommendation;
        public final String recommendationHindi;
        public final long lastUpdated;

        public PriceTrend(String cropName, String cropNameHindi, String trend, String trendHindi,
                         double averagePrice, double priceChange, String recommendation, String recommendationHindi) {
            this.cropName = cropName;
            this.cropNameHindi = cropNameHindi;
            this.trend = trend;
            this.trendHindi = trendHindi;
            this.averagePrice = averagePrice;
            this.priceChange = priceChange;
            this.recommendation = recommendation;
            this.recommendationHindi = recommendationHindi;
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    public MandiPriceService(Context context) {
        this.context = context;
        this.executorService = Executors.newFixedThreadPool(3);
        this.scheduledExecutor = Executors.newScheduledThreadPool(1);
        this.random = new Random();
        this.priceCache = new HashMap<>();
        this.trendCache = new HashMap<>();
        this.apiService = new APIService(context.getApplicationContext());
        
        // Initialize price data
        initializePriceData();
        
        // Start periodic price updates
        startPeriodicUpdates();
    }

    public void getMandiPrices(String cropName, Location userLocation, PriceCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Fetching mandi prices for crop: " + cropName);
                List<MandiPrice> prices = fetchOfficialPrices(cropName, null, null);
                if (prices.isEmpty()) callback.onPriceError("अभी इस फसल के लाइव मंडी भाव उपलब्ध नहीं हैं");
                else callback.onPricesReceived(prices);
            } catch (Exception e) {
                Log.e(TAG, "Error fetching mandi prices: " + e.getMessage(), e);
                callback.onPriceError("लाइव मंडी भाव नहीं मिल सके। इंटरनेट कनेक्शन जाँचें।");
            }
        });
    }

    public void getMandiPricesByDistrict(String cropName, String state, String district, PriceCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Fetching mandi prices for crop: " + cropName + " in district: " + district + ", state: " + state);
                String officialState = normalizeState(state);
                if (district == null || district.trim().isEmpty()) {
                    callback.onPriceError("कृपया पहले राज्य और जिला चुनें।");
                    return;
                }
                List<MandiPrice> prices = fetchOfficialPrices(cropName, officialState, district);
                if (prices.isEmpty()) callback.onPriceError("आज " + district + " में " + normalizeCrop(cropName) + " का सरकारी भाव दर्ज नहीं है। दूसरा crop/जिला चुनें या अगले दिन फिर देखें।");
                else callback.onPricesReceived(prices);
            } catch (Exception e) {
                Log.e(TAG, "Error fetching district mandi prices: " + e.getMessage(), e);
                callback.onPriceError("लाइव मंडी भाव नहीं मिल सके। इंटरनेट कनेक्शन जाँचें।");
            }
        });
    }

    private String normalizeCrop(String value) {
        if (value == null) return "";
        String text = value.toLowerCase(Locale.ROOT);
        if (text.contains("wheat") || text.contains("गेह")) return "Wheat";
        if (text.contains("rice") || text.contains("धान") || text.contains("paddy")) return "Rice";
        if (text.contains("maize") || text.contains("corn") || text.contains("मक्का")) return "Maize";
        if (text.contains("bhindi") || text.contains("ladies finger") || text.contains("okra") || text.contains("भिंडी")) return "Bhindi(Ladies Finger)";
        if (text.contains("bitter gourd") || text.contains("karela") || text.contains("करेला")) return "Bitter gourd";
        if (text.contains("bottle gourd") || text.contains("lauki") || text.contains("लौकी")) return "Bottle gourd";
        if (text.contains("brinjal") || text.contains("eggplant") || text.contains("बैंगन")) return "Brinjal";
        if (text.contains("cauliflower") || text.contains("फूलगोभी")) return "Cauliflower";
        if (text.contains("garlic") || text.contains("लहसुन")) return "Garlic";
        if (text.contains("pointed gourd") || text.contains("parval") || text.contains("परवल")) return "Pointed gourd(Parval)";
        if (text.contains("potato") || text.contains("आलू")) return "Potato";
        if (text.contains("onion") || text.contains("प्याज")) return "Onion";
        if (text.contains("tomato") || text.contains("टमाटर")) return "Tomato";
        if (text.contains("grape") || text.contains("अंगूर")) return "Grapes";
        if (text.contains("pulse") || text.contains("दलहन")) return "Pulses";
        if (text.contains("oilseed") || text.contains("तिलहन")) return "Oilseeds";
        if (text.contains("sugarcane") || text.contains("गन्ना")) return "Sugarcane";
        if (text.contains("cotton") || text.contains("कपास")) return "Cotton";
        if (text.contains("jute") || text.contains("जूट")) return "Jute";
        return value.trim();
    }
    private String normalizeState(String value) {
        if (value == null) return "";
        String text = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        if (text.contains("बिहार") || text.contains("bihar")) return "Bihar";
        if (text.equals("up") || text.contains("उत्तर प्रदेश") || text.contains("uttar pradesh")) return "Uttar Pradesh";
        if (text.equals("mp") || text.contains("मध्य प्रदेश") || text.contains("madhya pradesh")) return "Madhya Pradesh";
        if (text.contains("झारखंड") || text.contains("jharkhand")) return "Jharkhand";
        if (text.contains("पश्चिम बंगाल") || text.contains("west bengal")) return "West Bengal";
        if (text.contains("राजस्थान") || text.contains("rajasthan")) return "Rajasthan";
        if (text.contains("महाराष्ट्र") || text.contains("maharashtra")) return "Maharashtra";
        if (text.contains("कर्नाटक") || text.contains("karnataka")) return "Karnataka";
        if (text.contains("तमिलनाडु") || text.contains("tamil nadu")) return "Tamil Nadu";
        if (text.equals("ap") || text.contains("आंध्र प्रदेश") || text.contains("andhra pradesh")) return "Andhra Pradesh";
        return value;
    }
    private double parsePrice(String value) { try { return value == null ? 0 : Double.parseDouble(value.replace(",", "").trim()); } catch (NumberFormatException e) { return 0; } }

    private List<MandiPrice> fetchOfficialPrices(String cropName, String state, String district) throws Exception {
        String canonicalCrop = normalizeCrop(cropName);
        String cacheKey = canonicalCrop + "|" + state + "|" + district;
        CachedPrices cached = OFFICIAL_PRICE_CACHE.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.savedAt < PRICE_CACHE_TTL_MS) {
            return new ArrayList<>(cached.prices);
        }
        List<MandiPrice> prices = new ArrayList<>();
        for (String commodityFilter : commodityFilters(canonicalCrop)) {
            prices = fetchPricesForQuery(canonicalCrop, commodityFilter, state, district, district);
            if (prices.isEmpty() && district != null) {
                // Keep the same state and selected district, but tolerate official spelling variants.
                prices = fetchPricesForQuery(canonicalCrop, commodityFilter, state, null, district);
            }
            if (!prices.isEmpty()) break;
        }
        OFFICIAL_PRICE_CACHE.put(cacheKey, new CachedPrices(prices));
        return prices;
    }

    private List<MandiPrice> fetchPricesForQuery(String crop, String commodity, String state,
                                                  String apiDistrict, String selectedDistrict) throws Exception {
        retrofit2.Response<APIService.MandiBackendResponse> response = apiService.backend()
                .getMandiPrices(commodity, state, apiDistrict).execute();
        if (!response.isSuccessful()) throw new IOException("Mandi API returned HTTP " + response.code());
        if (response.body() == null) throw new IOException("Mandi API returned an empty response");
        List<MandiPrice> prices = new ArrayList<>();
        if (response.body().records != null) for (APIService.MandiRecord record : response.body().records) {
            if (state != null && !state.isEmpty()
                    && !normalizeState(state).equals(normalizeState(record.state))) continue;
            if (!matchesCrop(crop, record.commodity)) continue;
            if (selectedDistrict != null && !matchesDistrict(selectedDistrict, record.district)) continue;
            double modal = parsePrice(record.modalPrice);
            if (modal <= 0) continue;
            String mandi = record.market == null ? (record.district == null ? "मंडी" : record.district) : record.market;
            prices.add(new MandiPrice(mandi, mandi, record.commodity, getCropNameHindi(crop),
                    modal, "क्विंटल", record.variety == null ? "" : record.variety, modal, record.state, record.arrivalDate));
        }
        return prices;
    }

    private boolean matchesDistrict(String selected, String recordDistrict) {
        if (recordDistrict == null) return false;
        String wanted = districtKey(selected);
        String actual = districtKey(recordDistrict);
        return wanted.equals(actual);
    }

    private String districtKey(String district) {
        String key = district.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        key = key.replace("bangalore", "bengaluru").replace("chapra", "chhapra");
        if (key.contains("motihari") || key.contains("eastchamparan")) return "eastchamparanmotihari";
        return key;
    }

    private String[] commodityFilters(String crop) {
        switch (crop) {
            case "Rice": return new String[]{"Rice", "Paddy"};
            case "Sugarcane": return new String[]{"Sugarcane", "Sugar"};
            case "Grapes": return new String[]{"Grapes", "Grape"};
            case "Pulses":
            case "Oilseeds": return new String[]{null};
            default: return new String[]{crop};
        }
    }

    public void loadDistrictsByState(String state, DistrictsCallback callback) {
        executorService.execute(() -> {
            try {
                retrofit2.Response<APIService.MandiOptionsResponse> response = apiService.backend()
                        .getMandiOptions(normalizeState(state)).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onDistrictsError("इस राज्य के मंडी जिले अभी लोड नहीं हो सके।");
                    return;
                }
                List<String> districts = response.body().districts == null
                        ? new ArrayList<>() : new ArrayList<>(response.body().districts);
                callback.onDistrictsReceived(districts);
            } catch (Exception e) {
                Log.e(TAG, "Unable to load official mandi districts", e);
                callback.onDistrictsError("जिले लोड करने के लिए इंटरनेट कनेक्शन जाँचें।");
            }
        });
    }

    private boolean matchesCrop(String crop, String commodity) {
        if (crop == null || crop.isEmpty() || commodity == null) return true;
        String value = commodity.toLowerCase(Locale.ROOT);
        switch (crop) {
            case "Pulses": return value.matches(".*(gram|chana|lentil|masur|moong|mung|urad|arhar|tur|pea|pulses|dal).*");
            case "Oilseeds": return value.matches(".*(mustard|rapeseed|groundnut|sesame|til|soyabean|soybean|sunflower|safflower|linseed|castor|oilseed).*");
            case "Rice": return value.contains("rice") || value.contains("paddy");
            case "Maize": return value.contains("maize") || value.contains("corn");
            case "Grapes": return value.contains("grape");
            case "Sugarcane": return value.contains("sugar");
            default: return value.contains(crop.toLowerCase(Locale.ROOT));
        }
    }

    public List<String> getDistrictsByState(String state) {
        Log.d(TAG, "Getting districts for state: " + state);
        List<String> districts = new ArrayList<>();
        
        switch (state.toLowerCase()) {
            case "bihar":
                districts.addAll(Arrays.asList(
                    "Patna", "Ara", "Bhagalpur", "Muzaffarpur", "Gaya", "Darbhanga", 
                    "Chapra", "Sasaram", "Motihari", "Bettiah", "Siwan", "Gopalganj",
                    "Vaishali", "Samastipur", "Begusarai", "Khagaria", "Munger", "Jamui",
                    "Nawada", "Aurangabad", "Rohtas", "Buxar", "Bhabua", "Jehanabad",
                    "Arwal", "Sheikhpura", "Lakhisarai", "Sheohar", "Sitamarhi", "Madhubani",
                    "Supaul", "Saharsa", "Madhepura", "Araria", "Kishanganj", "Purnia",
                    "Katihar", "Banka", "Deoghar", "Godda", "Pakur", "Sahebganj"
                ));
                break;
            case "uttar pradesh":
                districts.addAll(Arrays.asList(
                    "Lucknow", "Kanpur", "Agra", "Varanasi", "Prayagraj", "Gorakhpur",
                    "Bareilly", "Aligarh", "Moradabad", "Saharanpur", "Meerut", "Ghaziabad",
                    "Noida", "Mathura", "Ayodhya", "Azamgarh", "Ballia", "Deoria",
                    "Kushinagar", "Maharajganj", "Siddharthnagar", "Basti", "Sant Kabir Nagar",
                    "Ambedkar Nagar", "Amethi", "Rae Bareli", "Sultanpur", "Pratapgarh",
                    "Kaushambi", "Fatehpur", "Banda", "Chitrakoot", "Hamirpur", "Jalaun",
                    "Jhansi", "Lalitpur", "Mahoba", "Banda", "Chitrakoot", "Hamirpur"
                ));
                break;
            case "madhya pradesh":
                districts.addAll(Arrays.asList(
                    "Bhopal", "Indore", "Jabalpur", "Gwalior", "Ujjain", "Sagar",
                    "Rewa", "Satna", "Chhatarpur", "Panna", "Damoh", "Tikamgarh",
                    "Chhindwara", "Hoshangabad", "Harda", "Betul", "Sehore", "Raisen",
                    "Vidisha", "Rajgarh", "Shajapur", "Dewas", "Dhar", "Jhabua",
                    "Alirajpur", "Barwani", "Khargone", "Khandwa", "Burhanpur", "Neemuch",
                    "Mandsaur", "Ratlam", "Ujjain", "Shajapur", "Dewas", "Dhar"
                ));
                break;
            case "jharkhand":
                districts.addAll(Arrays.asList(
                    "Ranchi", "Jamshedpur", "Dhanbad", "Bokaro", "Hazaribagh", "Giridih",
                    "Koderma", "Chatra", "Latehar", "Palamu", "Garhwa", "Deoghar",
                    "Godda", "Pakur", "Sahebganj", "Dumka", "Jamtara", "Sahibganj",
                    "East Singhbhum", "West Singhbhum", "Seraikela Kharsawan", "Khunti",
                    "Gumla", "Simdega", "Lohardaga", "Ramgarh", "Bokaro", "Dhanbad"
                ));
                break;
            case "west bengal":
                districts.addAll(Arrays.asList(
                    "Kolkata", "Howrah", "Hooghly", "Burdwan", "Birbhum", "Murshidabad",
                    "Nadia", "North 24 Parganas", "South 24 Parganas", "East Midnapore",
                    "West Midnapore", "Bankura", "Purulia", "Jalpaiguri", "Cooch Behar",
                    "Darjeeling", "North Dinajpur", "South Dinajpur", "Malda", "Murshidabad"
                ));
                break;
            case "rajasthan":
                districts.addAll(Arrays.asList(
                    "Jaipur", "Jodhpur", "Kota", "Bikaner", "Ajmer", "Udaipur",
                    "Sikar", "Jhunjhunu", "Alwar", "Bharatpur", "Dholpur", "Karauli",
                    "Sawai Madhopur", "Tonk", "Bundi", "Baran", "Jhalawar", "Chittorgarh",
                    "Bhilwara", "Rajsamand", "Pali", "Sirohi", "Jalore", "Barmer",
                    "Jaisalmer", "Bikaner", "Churu", "Jhunjhunu", "Sikar", "Alwar"
                ));
                break;
            case "maharashtra":
                districts.addAll(Arrays.asList(
                    "Mumbai", "Pune", "Nagpur", "Thane", "Nashik", "Aurangabad",
                    "Solapur", "Kolhapur", "Sangli", "Satara", "Ratnagiri", "Sindhudurg",
                    "Raigad", "Pune", "Ahmednagar", "Beed", "Latur", "Osmanabad",
                    "Nanded", "Parbhani", "Hingoli", "Washim", "Buldhana", "Akola",
                    "Amravati", "Yavatmal", "Wardha", "Gondia", "Bhandara", "Chandrapur"
                ));
                break;
            case "karnataka":
                districts.addAll(Arrays.asList(
                    "Bangalore", "Mysore", "Mangalore", "Hubli", "Belgaum", "Gulbarga",
                    "Bellary", "Bijapur", "Raichur", "Koppal", "Gadag", "Dharwad",
                    "Haveri", "Davangere", "Chitradurga", "Tumkur", "Kolar", "Bangalore Rural",
                    "Mandya", "Hassan", "Chikmagalur", "Shimoga", "Udupi", "Dakshina Kannada",
                    "Kodagu", "Chamrajnagar", "Mysore", "Mandya", "Hassan", "Chikmagalur"
                ));
                break;
            case "tamil nadu":
                districts.addAll(Arrays.asList(
                    "Chennai", "Coimbatore", "Madurai", "Salem", "Tiruchirappalli", "Vellore",
                    "Erode", "Tiruppur", "Namakkal", "Karur", "Dindigul", "Theni",
                    "Virudhunagar", "Ramanathapuram", "Sivaganga", "Madurai", "Theni",
                    "Dindigul", "Tiruchirappalli", "Karur", "Namakkal", "Salem", "Erode",
                    "Tiruppur", "Coimbatore", "Nilgiris", "Coimbatore", "Erode", "Salem"
                ));
                break;
            case "andhra pradesh":
                districts.addAll(Arrays.asList(
                    "Visakhapatnam", "Vijayawada", "Guntur", "Nellore", "Kurnool", "Anantapur",
                    "Chittoor", "Kadapa", "Prakasam", "East Godavari", "West Godavari",
                    "Krishna", "Guntur", "Prakasam", "Nellore", "Chittoor", "Kadapa",
                    "Anantapur", "Kurnool", "YSR Kadapa", "Anantapur", "Kurnool", "Prakasam"
                ));
                break;
            default:
                // Don't show made-up district names when no real list is configured.
                break;
        }
        
        Log.d(TAG, "Returning " + districts.size() + " districts for state: " + state);
        return districts;
    }

    private List<MandiPrice> getPricesForDistrict(String cropName, String state, String district) {
        List<MandiPrice> prices = new ArrayList<>();
        
        // Get district-specific mandis
        List<String> districtMandis = getDistrictMandis(state, district);
        
        // Base price for crop
        double basePrice = getBasePriceForCrop(cropName);
        
        for (String mandiName : districtMandis) {
            String mandiNameHindi = getMandiNameHindi(mandiName);
            
            // Generate price variation based on mandi location and demand
            double priceVariation = 0.8 + random.nextDouble() * 0.4; // ±20% variation
            double currentPrice = basePrice * priceVariation;
            
            // Generate previous price for trend calculation
            double previousPrice = currentPrice * (0.9 + random.nextDouble() * 0.2);
            
            // Quality grades
            String[] qualities = {"A Grade", "B Grade", "C Grade"};
            String quality = qualities[random.nextInt(qualities.length)];
            
            MandiPrice price = new MandiPrice(mandiName, mandiNameHindi, cropName, 
                getCropNameHindi(cropName), currentPrice, "per quintal", quality, previousPrice);
            
            prices.add(price);
        }
        
        return prices;
    }

    private List<String> getDistrictMandis(String state, String district) {
        List<String> mandis = new ArrayList<>();
        
        // Add the district capital as main mandi
        mandis.add(district);
        
        // Add some major mandis in the district
        switch (state.toLowerCase()) {
            case "bihar":
                if (district.equalsIgnoreCase("Patna")) {
                    mandis.addAll(Arrays.asList("Patna City", "Phulwari", "Bakhtiarpur", "Barh", "Fatuha"));
                } else if (district.equalsIgnoreCase("Ara")) {
                    mandis.addAll(Arrays.asList("Ara City", "Bikramganj", "Piro", "Sandesh", "Tarari"));
                } else if (district.equalsIgnoreCase("Bhagalpur")) {
                    mandis.addAll(Arrays.asList("Bhagalpur City", "Kahalgaon", "Naugachhia", "Sultanganj", "Bihpur"));
                } else if (district.equalsIgnoreCase("Muzaffarpur")) {
                    mandis.addAll(Arrays.asList("Muzaffarpur City", "Motihari", "Sitamarhi", "Sheohar", "Vaishali"));
                } else if (district.equalsIgnoreCase("Gaya")) {
                    mandis.addAll(Arrays.asList("Gaya City", "Bodh Gaya", "Sherghati", "Tekari", "Fatehpur"));
                } else {
                    mandis.addAll(Arrays.asList(district + " City", district + " Market", district + " Mandi"));
                }
                break;
            case "uttar pradesh":
                if (district.equalsIgnoreCase("Lucknow")) {
                    mandis.addAll(Arrays.asList("Lucknow City", "Gomti Nagar", "Hazratganj", "Aliganj", "Mahanagar"));
                } else if (district.equalsIgnoreCase("Kanpur")) {
                    mandis.addAll(Arrays.asList("Kanpur City", "Swaroop Nagar", "Kakadeo", "Govind Nagar", "Shyam Nagar"));
                } else {
                    mandis.addAll(Arrays.asList(district + " City", district + " Market", district + " Mandi"));
                }
                break;
            default:
                // Generic mandis for other states
                mandis.addAll(Arrays.asList(district + " City", district + " Market", district + " Mandi", 
                    district + " Industrial Area", district + " Agricultural Market"));
                break;
        }
        
        return mandis;
    }

    public void getPriceTrend(String cropName, TrendCallback callback) {
        callback.onTrendError("मूल्य का ऐतिहासिक रुझान अभी उपलब्ध नहीं है");
    }

    public void getNearbyMandis(Location userLocation, PriceCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Finding nearby mandis for location: " + userLocation.getLatitude() + ", " + userLocation.getLongitude());
                
                List<MandiPrice> nearbyPrices = fetchOfficialPrices("", null, null);
                if (nearbyPrices.isEmpty()) { callback.onPriceError("लाइव मंडी डेटा अभी उपलब्ध नहीं है"); return; }
                callback.onPricesReceived(nearbyPrices);
                
            } catch (Exception e) {
                Log.e(TAG, "Error finding nearby mandis: " + e.getMessage(), e);
                callback.onPriceError("निकटवर्ती मंडियां खोजने में त्रुटि: " + e.getMessage());
            }
        });
    }

    private void initializePriceData() {
        // Initialize with sample data for major crops
        String[] crops = {"wheat", "rice", "maize", "potato", "onion", "tomato"};
        String[] cropsHindi = {"गेहूँ", "धान", "मक्का", "आलू", "प्याज़", "टमाटर"};
        
        for (int i = 0; i < crops.length; i++) {
            String crop = crops[i];
            String cropHindi = cropsHindi[i];
            
            // Generate base prices for each crop
            double basePrice = 1500 + (i * 200) + random.nextDouble() * 500;
            
            // Store in cache
            List<MandiPrice> prices = new ArrayList<>();
            trendCache.put(crop, new PriceTrend(crop, cropHindi, "stable", "स्थिर", basePrice, 0, 
                "Prices are stable, good time to sell", "मूल्य स्थिर हैं, बेचने का अच्छा समय है"));
            
            priceCache.put(crop, prices);
        }
    }

    private List<MandiPrice> getPricesForCrop(String cropName, Location userLocation) {
        List<MandiPrice> prices = priceCache.get(cropName);
        if (prices == null || prices.isEmpty()) {
            prices = generateMandiPrices(cropName, userLocation);
            priceCache.put(cropName, prices);
        }
        return prices;
    }

    private PriceTrend getTrendForCrop(String cropName) {
        PriceTrend trend = trendCache.get(cropName);
        if (trend == null) {
            trend = generatePriceTrend(cropName);
            trendCache.put(cropName, trend);
        }
        return trend;
    }

    private List<MandiPrice> generateMandiPrices(String cropName, Location userLocation) {
        List<MandiPrice> prices = new ArrayList<>();
        
        // Major mandis in Bihar
        String[] mandiNames = {"Patna", "Ara", "Bhagalpur", "Muzaffarpur", "Gaya", "Darbhanga", "Chapra", "Sasaram"};
        String[] mandiNamesHindi = {"पटना", "आरा", "भागलपुर", "मुज़फ्फरपुर", "गया", "दरभंगा", "छपरा", "सासाराम"};
        
        // Base price for crop
        double basePrice = getBasePriceForCrop(cropName);
        
        for (int i = 0; i < mandiNames.length; i++) {
            String mandiName = mandiNames[i];
            String mandiNameHindi = mandiNamesHindi[i];
            
            // Generate price variation based on mandi location and demand
            double priceVariation = 0.8 + random.nextDouble() * 0.4; // ±20% variation
            double currentPrice = basePrice * priceVariation;
            
            // Generate previous price for trend calculation
            double previousPrice = currentPrice * (0.9 + random.nextDouble() * 0.2);
            
            // Quality grades
            String[] qualities = {"A Grade", "B Grade", "C Grade"};
            String quality = qualities[random.nextInt(qualities.length)];
            
            MandiPrice price = new MandiPrice(mandiName, mandiNameHindi, cropName, 
                getCropNameHindi(cropName), currentPrice, "per quintal", quality, previousPrice);
            
            prices.add(price);
        }
        
        return prices;
    }

    private List<MandiPrice> generateNearbyMandiPrices(Location userLocation) {
        List<MandiPrice> nearbyPrices = new ArrayList<>();
        
        // Generate prices for nearby mandis (within 50km radius)
        String[] nearbyMandis = {"Local Market", "Village Mandi", "Block Market"};
        String[] nearbyMandisHindi = {"स्थानीय बाजार", "गाँव मंडी", "ब्लॉक मंडी"};
        
        String[] crops = {"wheat", "rice", "potato"};
        
        for (String mandi : nearbyMandis) {
            for (String crop : crops) {
                double basePrice = getBasePriceForCrop(crop);
                double localPrice = basePrice * (0.85 + random.nextDouble() * 0.3); // Local prices are usually lower
                
                MandiPrice price = new MandiPrice(mandi, getMandiNameHindi(mandi), crop, 
                    getCropNameHindi(crop), localPrice, "per quintal", "Local Grade", localPrice * 0.95);
                
                nearbyPrices.add(price);
            }
        }
        
        return nearbyPrices;
    }

    private PriceTrend generatePriceTrend(String cropName) {
        double basePrice = getBasePriceForCrop(cropName);
        
        // Generate trend based on market conditions
        String[] trends = {"rising", "falling", "stable"};
        String[] trendsHindi = {"बढ़ रहा", "गिर रहा", "स्थिर"};
        String[] recommendations = {
            "Prices are rising, consider holding for better rates",
            "Prices are falling, consider selling soon",
            "Prices are stable, good time to sell"
        };
        String[] recommendationsHindi = {
            "मूल्य बढ़ रहे हैं, बेहतर दर के लिए रोकें",
            "मूल्य गिर रहे हैं, जल्द बेचने पर विचार करें",
            "मूल्य स्थिर हैं, बेचने का अच्छा समय है"
        };
        
        int trendIndex = random.nextInt(trends.length);
        double priceChange = (trendIndex == 0) ? basePrice * 0.1 : (trendIndex == 1) ? -basePrice * 0.08 : 0;
        
        return new PriceTrend(cropName, getCropNameHindi(cropName), trends[trendIndex], trendsHindi[trendIndex],
            basePrice, priceChange, recommendations[trendIndex], recommendationsHindi[trendIndex]);
    }

    private double getBasePriceForCrop(String cropName) {
        switch (cropName.toLowerCase()) {
            case "wheat": return 1900 + random.nextDouble() * 200;
            case "rice": return 2100 + random.nextDouble() * 300;
            case "maize": return 1800 + random.nextDouble() * 250;
            case "potato": return 1200 + random.nextDouble() * 400;
            case "onion": return 2500 + random.nextDouble() * 500;
            case "tomato": return 3000 + random.nextDouble() * 800;
            default: return 2000 + random.nextDouble() * 300;
        }
    }

    private String getCropNameHindi(String cropName) {
        switch (cropName.toLowerCase()) {
            case "wheat": return "गेहूँ";
            case "rice": return "धान";
            case "maize": return "मक्का";
            case "potato": return "आलू";
            case "onion": return "प्याज़";
            case "tomato": return "टमाटर";
            case "bhindi(ladies finger)": return "भिंडी";
            case "bitter gourd": return "करेला";
            case "bottle gourd": return "लौकी";
            case "brinjal": return "बैंगन";
            case "cauliflower": return "फूलगोभी";
            case "garlic": return "लहसुन";
            case "pointed gourd(parval)": return "परवल";
            case "grapes": return "अंगूर";
            case "pulses": return "दलहन";
            case "oilseeds": return "तिलहन";
            case "sugarcane": return "गन्ना";
            case "cotton": return "कपास";
            case "jute": return "जूट";
            default: return cropName;
        }
    }

    private String getMandiNameHindi(String mandiName) {
        switch (mandiName) {
            case "Local Market": return "स्थानीय बाजार";
            case "Village Mandi": return "गाँव मंडी";
            case "Block Market": return "ब्लॉक मंडी";
            default: return mandiName;
        }
    }

    private void startPeriodicUpdates() {
        // Update prices every 2 hours
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                Log.d(TAG, "Performing periodic price update...");
                updateAllPrices();
            } catch (Exception e) {
                Log.e(TAG, "Error in periodic price update: " + e.getMessage(), e);
            }
        }, 2, 2, TimeUnit.HOURS);
    }

    private void updateAllPrices() {
        // Update prices for all crops with small variations
        for (String crop : priceCache.keySet()) {
            List<MandiPrice> prices = priceCache.get(crop);
            if (prices != null) {
                for (MandiPrice price : prices) {
                    // Small price variation (±5%)
                    double variation = 0.95 + random.nextDouble() * 0.1;
                    // Update price in cache (this would normally update the database)
                }
            }
        }
    }

    public void shutdown() {
        executorService.shutdown();
        scheduledExecutor.shutdown();
    }
}
