package com.example.smarthub.api;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.Header;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.ArrayList;

public class APIService {
    private static final String TAG = "APIService";
    private static final String WEATHER_BASE_URL = "https://api.openweathermap.org/data/2.5/";
    private static final String MANDI_BASE_URL = "https://api.data.gov.in/resource/";
    
    private final WeatherAPI weatherAPI;
    private final MandiAPI mandiAPI;
    private final BackendAPI backendAPI;

    public interface BackendAPI {
        @POST("auth/request-otp")
        Call<BasicResponse> requestOtp(@Body OtpRequest request);

        @POST("auth/verify-otp")
        Call<AuthResponse> verifyOtp(@Body OtpVerifyRequest request);

        @GET("listings")
        Call<List<BackendListing>> getListings(@Query("state") String state, @Query("crop") String crop);

        @POST("listings")
        Call<BackendListing> createListing(@Header("Authorization") String authorization, @Body ListingRequest request);

        @GET("buy-requests")
        Call<List<BackendBuyRequest>> getBuyRequests(@Header("Authorization") String authorization);

        @POST("buy-requests")
        Call<BackendBuyRequest> createBuyRequest(@Header("Authorization") String authorization, @Body BuyRequest request);
    }

    public static class OtpRequest {
        public final String phone;
        public OtpRequest(String phone) { this.phone = phone; }
    }

    public static class OtpVerifyRequest {
        public final String phone;
        public final String code;
        public OtpVerifyRequest(String phone, String code) { this.phone = phone; this.code = code; }
    }

    public static class BasicResponse { public boolean ok; }

    public static class ListingRequest {
        public String cropName; public double quantity; public String unit; public String quality;
        public double pricePerUnit; public String location; public String state; public String description;
        public ListingRequest(String cropName, double quantity, String unit, String quality, double pricePerUnit,
                              String location, String state, String description) {
            this.cropName = cropName; this.quantity = quantity; this.unit = unit; this.quality = quality;
            this.pricePerUnit = pricePerUnit; this.location = location; this.state = state; this.description = description;
        }
    }

    public static class BuyRequest {
        public String cropName; public double quantity; public String unit; public double maxPricePerUnit;
        public String location; public String state;
        public BuyRequest(String cropName, double quantity, String unit, double maxPricePerUnit, String location, String state) {
            this.cropName = cropName; this.quantity = quantity; this.unit = unit; this.maxPricePerUnit = maxPricePerUnit;
            this.location = location; this.state = state;
        }
    }

    public static class BackendListing {
        public String id; @SerializedName("farmer_id") public String farmerId; @SerializedName("crop_name") public String cropName;
        public double quantity; public String unit; public String quality; @SerializedName("price_per_unit") public double pricePerUnit;
        public String location; public String state; public String description; public String status;
        @SerializedName("created_at") public String createdAt;
    }

    public static class BackendBuyRequest {
        public String id; @SerializedName("buyer_id") public String buyerId; @SerializedName("crop_name") public String cropName;
        public double quantity; public String unit; @SerializedName("max_price_per_unit") public double maxPricePerUnit;
        public String location; public String state; public String status; @SerializedName("created_at") public String createdAt;
    }

    public static class AuthResponse {
        public String token;
        public User user;
    }

    public static class User {
        public String id;
        public String phone;
        public String role;
    }
    
    public interface WeatherAPI {
        @GET("weather")
        Call<WeatherResponse> getCurrentWeather(
            @Query("lat") double lat,
            @Query("lon") double lon,
            @Query("appid") String apiKey,
            @Query("units") String units
        );
        
        @GET("forecast")
        Call<ForecastResponse> getWeatherForecast(
            @Query("lat") double lat,
            @Query("lon") double lon,
            @Query("appid") String apiKey,
            @Query("units") String units
        );
    }
    
    public interface MandiAPI {
        @GET("9ef84268-d588-465a-a308-a864a43d0070")
        Call<MandiResponse> getMandiPrices(
            @Query("api-key") String apiKey,
            @Query("format") String format,
            @Query("filters") String filters
        );
    }
    
    public APIService(Context context) {
        // Initialize Weather API
        Retrofit weatherRetrofit = new Retrofit.Builder()
            .baseUrl(WEATHER_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
        weatherAPI = weatherRetrofit.create(WeatherAPI.class);
        
        // Initialize Mandi API
        Retrofit mandiRetrofit = new Retrofit.Builder()
            .baseUrl(MANDI_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
        mandiAPI = mandiRetrofit.create(MandiAPI.class);

        Retrofit backendRetrofit = new Retrofit.Builder()
            .baseUrl(com.example.smarthub.BuildConfig.AGRI_API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
        backendAPI = backendRetrofit.create(BackendAPI.class);
    }

    public BackendAPI backend() { return backendAPI; }
    
    public void getRealWeatherData(double lat, double lon, String apiKey, WeatherCallback callback) {
        // Use BuildConfig for API key if not provided
        String key = apiKey != null ? apiKey : com.example.smarthub.BuildConfig.WEATHER_API_KEY;
        weatherAPI.getCurrentWeather(lat, lon, key, "metric")
            .enqueue(new Callback<WeatherResponse>() {
                @Override
                public void onResponse(@NonNull Call<WeatherResponse> call, @NonNull Response<WeatherResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        callback.onWeatherReceived(response.body());
                    } else {
                        callback.onWeatherError("Weather data not available");
                    }
                }
                
                @Override
                public void onFailure(@NonNull Call<WeatherResponse> call, @NonNull Throwable t) {
                    callback.onWeatherError("Network error: " + t.getMessage());
                }
            });
    }
    
    public void getRealMandiPrices(String apiKey, String cropName, MandiCallback callback) {
        // Use BuildConfig for API key if not provided
        String key = apiKey != null ? apiKey : com.example.smarthub.BuildConfig.MANDI_API_KEY;
        String filters = "commodity=" + cropName;
        mandiAPI.getMandiPrices(key, "json", filters)
            .enqueue(new Callback<MandiResponse>() {
                @Override
                public void onResponse(@NonNull Call<MandiResponse> call, @NonNull Response<MandiResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        callback.onMandiDataReceived(response.body());
                    } else {
                        callback.onMandiError("Mandi data not available");
                    }
                }
                
                @Override
                public void onFailure(@NonNull Call<MandiResponse> call, @NonNull Throwable t) {
                    callback.onMandiError("Network error: " + t.getMessage());
                }
            });
    }
    
    public interface WeatherCallback {
        void onWeatherReceived(WeatherResponse weather);
        void onWeatherError(String error);
    }
    
    public interface MandiCallback {
        void onMandiDataReceived(MandiResponse mandiData);
        void onMandiError(String error);
    }
    
    // Response classes
    public static class WeatherResponse {
        public Main main;
        public Weather[] weather;
        public Wind wind;
        public String name;
    }
    
    public static class Main {
        public double temp;
        public int humidity;
        public double pressure;
    }
    
    public static class Weather {
        public String main;
        public String description;
        public String icon;
    }
    
    public static class Wind {
        public double speed;
        public int deg;
    }
    
    public static class ForecastResponse {
        public ForecastItem[] list;
        
        public static class ForecastItem {
            public long dt;
            public Main main;
            public Weather[] weather;
        }
    }
    
    public static class MandiResponse {
        public Record[] records;
        
        public static class Record {
            public String state;
            public String district;
            public String market;
            public String commodity;
            public String variety;
            public String min_price;
            public String max_price;
            public String modal_price;
        }
    }
}
