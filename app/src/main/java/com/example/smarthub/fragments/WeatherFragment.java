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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.smarthub.R;
import com.example.smarthub.services.WeatherService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WeatherFragment extends Fragment {
    
    private static final String TAG = "WeatherFragment";
    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    
    private TextView tvCurrentTemp, tvCurrentCondition, tvHumidity, tvWindSpeed;
    private TextView tvForecast1, tvForecast2, tvForecast3, tvForecast4, tvForecast5;
    private TextView tvFarmingAdvice, tvWeatherAlert;
    private View weatherAlertCard;
    
    private WeatherService weatherService;
    private Location currentLocation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_weather, container, false);
        
        initializeViews(view);
        setupWeatherService();
        
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Check location permission and get weather
        if (checkLocationPermission()) {
            getCurrentLocationAndWeather();
        } else {
            requestLocationPermission();
        }
    }
    
    private void initializeViews(View view) {
        tvCurrentTemp = view.findViewById(R.id.tv_current_temp);
        tvCurrentCondition = view.findViewById(R.id.tv_current_condition);
        tvHumidity = view.findViewById(R.id.tv_humidity);
        tvWindSpeed = view.findViewById(R.id.tv_wind_speed);
        
        tvForecast1 = view.findViewById(R.id.tv_forecast_1);
        tvForecast2 = view.findViewById(R.id.tv_forecast_2);
        tvForecast3 = view.findViewById(R.id.tv_forecast_3);
        tvForecast4 = view.findViewById(R.id.tv_forecast_4);
        tvForecast5 = view.findViewById(R.id.tv_forecast_5);
        
        tvFarmingAdvice = view.findViewById(R.id.tv_farming_advice);
        tvWeatherAlert = view.findViewById(R.id.tv_weather_alert);
        weatherAlertCard = view.findViewById(R.id.weather_alert_card);
        
        // Initially hide weather alert card
        if (weatherAlertCard != null) {
            weatherAlertCard.setVisibility(View.GONE);
        }
    }
    
    private void setupWeatherService() {
        weatherService = new WeatherService(requireContext());
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
    
    private void getCurrentLocationAndWeather() {
        try {
            LocationManager locationManager = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);
            
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) 
                        == PackageManager.PERMISSION_GRANTED) {
                    
                    // Get last known location
                    currentLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    
                    if (currentLocation != null) {
                        Log.d(TAG, "Location obtained: " + currentLocation.getLatitude() + ", " + currentLocation.getLongitude());
                        fetchWeatherData();
                    } else {
                        // Use default location (Patna, Bihar)
                        currentLocation = createDefaultLocation();
                        Log.d(TAG, "Using default location: Patna, Bihar");
                        fetchWeatherData();
                    }
                }
            } else {
                // Use default location if GPS is not available
                currentLocation = createDefaultLocation();
                Log.d(TAG, "GPS not available, using default location: Patna, Bihar");
                fetchWeatherData();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting location: " + e.getMessage(), e);
            // Use default location
            currentLocation = createDefaultLocation();
            fetchWeatherData();
        }
    }
    
    private Location createDefaultLocation() {
        Location location = new Location("default");
        location.setLatitude(25.5941); // Patna, Bihar coordinates
        location.setLongitude(85.1376);
        return location;
    }
    
    private void fetchWeatherData() {
        if (weatherService != null && currentLocation != null) {
            Log.d(TAG, "Fetching weather data...");
            
            weatherService.getCurrentWeather(currentLocation, new WeatherService.WeatherCallback() {
                @Override
                public void onWeatherUpdate(WeatherService.WeatherData weatherData) {
                    requireActivity().runOnUiThread(() -> {
                        displayCurrentWeather(weatherData);
                        generateFarmingAdvice(weatherData);
                    });
                }
                
                @Override
                public void onWeatherError(String error) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                        showDemoWeatherData();
                    });
                }
            });
            
            // Get weather alerts
            weatherService.getWeatherAlerts(currentLocation, new WeatherService.AlertCallback() {
                @Override
                public void onAlertReceived(WeatherService.WeatherAlert alert) {
                    requireActivity().runOnUiThread(() -> {
                        displayWeatherAlert(alert);
                    });
                }
            });
            
            // Get 5-day forecast
            weatherService.getWeatherForecast(currentLocation, 5, new WeatherService.WeatherCallback() {
                @Override
                public void onWeatherUpdate(WeatherService.WeatherData weatherData) {
                    // This will be called for each day in the forecast
                }
                
                @Override
                public void onWeatherError(String error) {
                    Log.e(TAG, "Forecast error: " + error);
                }
            });
            
        } else {
            Log.e(TAG, "WeatherService or location is null");
            showDemoWeatherData();
        }
    }
    
    private void displayCurrentWeather(WeatherService.WeatherData weatherData) {
        if (tvCurrentTemp != null) {
            tvCurrentTemp.setText(String.format(Locale.getDefault(), "%.1f°C", weatherData.temperature));
        }
        
        if (tvCurrentCondition != null) {
            tvCurrentCondition.setText(weatherData.conditionHindi);
        }
        
        if (tvHumidity != null) {
            tvHumidity.setText(String.format(Locale.getDefault(), "%d%%", weatherData.humidity));
        }
        
        if (tvWindSpeed != null) {
            tvWindSpeed.setText(String.format(Locale.getDefault(), "%.1f km/h", weatherData.windSpeed));
        }
        
        Log.d(TAG, "Current weather displayed: " + weatherData.temperature + "°C, " + weatherData.conditionHindi);
    }
    
    private void generateFarmingAdvice(WeatherService.WeatherData weatherData) {
        StringBuilder advice = new StringBuilder();
        
        if (weatherData.temperature > 35) {
            advice.append("🌡️ उच्च तापमान: सुबह या शाम को सिंचाई करें\n");
        } else if (weatherData.temperature < 15) {
            advice.append("❄️ कम तापमान: फसलों को ठंड से बचाएं\n");
        }
        
        if (weatherData.humidity > 80) {
            advice.append("💧 उच्च आर्द्रता: फंगल रोगों से सावधान रहें\n");
        } else if (weatherData.humidity < 40) {
            advice.append("🌵 कम आर्द्रता: नियमित सिंचाई आवश्यक\n");
        }
        
        if (weatherData.windSpeed > 20) {
            advice.append("💨 तेज हवा: फसलों को हवा से बचाएं\n");
        }
        
        if (weatherData.condition.toLowerCase().contains("rain")) {
            advice.append("🌧️ बारिश: कटाई टालें, भंडारण ढकें\n");
        } else if (weatherData.condition.toLowerCase().contains("sunny")) {
            advice.append("☀️ धूप: सिंचाई का समय\n");
        }
        
        if (advice.length() == 0) {
            advice.append("✅ मौसम अनुकूल है, सामान्य कृषि कार्य जारी रखें\n");
        }
        
        if (tvFarmingAdvice != null) {
            tvFarmingAdvice.setText(advice.toString());
        }
        
        Log.d(TAG, "Farming advice generated: " + advice.toString());
    }
    
    private void displayWeatherAlert(WeatherService.WeatherAlert alert) {
        if (weatherAlertCard != null && tvWeatherAlert != null) {
            weatherAlertCard.setVisibility(View.VISIBLE);
            tvWeatherAlert.setText(alert.messageHindi);
            Log.d(TAG, "Weather alert displayed: " + alert.messageHindi);
        }
    }
    
    private void showDemoWeatherData() {
        Log.d(TAG, "Showing demo weather data");
        
        // Show demo current weather
        if (tvCurrentTemp != null) tvCurrentTemp.setText("32°C");
        if (tvCurrentCondition != null) tvCurrentCondition.setText("मेघ-आवृत");
        if (tvHumidity != null) tvHumidity.setText("75%");
        if (tvWindSpeed != null) tvWindSpeed.setText("12 km/h");
        
        // Show demo forecast
        if (tvForecast1 != null) tvForecast1.setText("कल: हल्की बारिश · 31°C");
        if (tvForecast2 != null) tvForecast2.setText("परसों: बादल · 33°C");
        if (tvForecast3 != null) tvForecast3.setText("मंगल: धूप · 35°C");
        if (tvForecast4 != null) tvForecast4.setText("बुध: बादल · 34°C");
        if (tvForecast5 != null) tvForecast5.setText("गुरु: हल्की बारिश · 30°C");
        
        // Show demo farming advice
        if (tvFarmingAdvice != null) {
            tvFarmingAdvice.setText("🌧️ बारिश की संभावना: कटाई टालें\n" +
                                   "💧 उच्च आर्द्रता: फंगल रोगों से सावधान\n" +
                                   "✅ सिंचाई का समय उपयुक्त है");
        }
        
        // Show demo weather alert
        if (weatherAlertCard != null && tvWeatherAlert != null) {
            weatherAlertCard.setVisibility(View.VISIBLE);
            tvWeatherAlert.setText("अगले 24 घंटे में मध्यम वर्षा की संभावना। कटाई टालें, भंडारण ढकें।");
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocationAndWeather();
            } else {
                Toast.makeText(requireContext(), "स्थान की अनुमति आवश्यक है मौसम जानकारी के लिए", Toast.LENGTH_LONG).show();
                showDemoWeatherData();
            }
        }
    }
}
