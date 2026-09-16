package com.example.smarthub.services;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import androidx.annotation.NonNull;
import com.example.smarthub.api.APIService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Random;

public class WeatherService {
    private static final String TAG = "WeatherService";
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    private final Context context;
    private final Random random;
    private final APIService apiService;

    public interface WeatherCallback {
        void onWeatherUpdate(WeatherData weatherData);
        void onWeatherError(String error);
    }

    public interface AlertCallback {
        void onAlertReceived(WeatherAlert alert);
    }

    public static class WeatherData {
        public final double temperature;
        public final int humidity;
        public final String condition;
        public final String conditionHindi;
        public final double windSpeed;
        public final String windDirection;
        public final int visibility;
        public final long timestamp;

        public WeatherData(double temperature, int humidity, String condition, String conditionHindi,
                         double windSpeed, String windDirection, int visibility) {
            this.temperature = temperature;
            this.humidity = humidity;
            this.condition = condition;
            this.conditionHindi = conditionHindi;
            this.windSpeed = windSpeed;
            this.windDirection = windDirection;
            this.visibility = visibility;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static class WeatherAlert {
        public final String type;
        public final String typeHindi;
        public final String message;
        public final String messageHindi;
        public final String severity;
        public final long validFrom;
        public final long validTo;

        public WeatherAlert(String type, String typeHindi, String message, String messageHindi,
                          String severity, long validFrom, long validTo) {
            this.type = type;
            this.typeHindi = typeHindi;
            this.message = message;
            this.messageHindi = messageHindi;
            this.severity = severity;
            this.validFrom = validFrom;
            this.validTo = validTo;
        }
    }

    public WeatherService(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        this.scheduledExecutor = Executors.newScheduledThreadPool(1);
        this.random = new Random();
        this.apiService = new APIService(context.getApplicationContext());
        
        // Start periodic weather updates
        startPeriodicUpdates();
    }

    public void getCurrentWeather(Location location, WeatherCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Fetching current weather for location: " + location.getLatitude() + ", " + location.getLongitude());
                
                retrofit2.Response<APIService.OpenMeteoResponse> response = apiService.openMeteo()
                        .current(location.getLatitude(), location.getLongitude(), 1).execute();
                if (!response.isSuccessful() || response.body() == null || response.body().current == null) {
                    callback.onWeatherError("मौसम सेवा अभी उपलब्ध नहीं है"); return;
                }
                APIService.CurrentWeather current = response.body().current;
                WeatherData weatherData = new WeatherData(current.temperature, current.humidity,
                        condition(current.weatherCode), conditionHindi(current.weatherCode),
                        current.windSpeed, direction(current.windDirection), 0);
                callback.onWeatherUpdate(weatherData);
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching weather: " + e.getMessage(), e);
                callback.onWeatherError("मौसम जानकारी नहीं मिल सकी। इंटरनेट कनेक्शन जाँचें।");
            }
        });
    }

    public void getWeatherForecast(Location location, int days, WeatherCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Fetching " + days + " day forecast for location: " + location.getLatitude() + ", " + location.getLongitude());
                
                retrofit2.Response<APIService.OpenMeteoResponse> response = apiService.openMeteo()
                        .current(location.getLatitude(), location.getLongitude(), Math.max(1, Math.min(days, 16))).execute();
                if (!response.isSuccessful() || response.body() == null || response.body().daily == null) {
                    callback.onWeatherError("पूर्वानुमान अभी उपलब्ध नहीं है"); return;
                }
                APIService.DailyWeather daily = response.body().daily;
                int count = Math.min(days, daily.time == null ? 0 : daily.time.size());
                for (int i = 0; i < count; i++) {
                    double max = daily.maxTemperature != null && i < daily.maxTemperature.size() ? daily.maxTemperature.get(i) : 0;
                    int code = daily.weatherCode != null && i < daily.weatherCode.size() ? daily.weatherCode.get(i) : 0;
                    callback.onWeatherUpdate(new WeatherData(max, 0, condition(code), conditionHindi(code), 0, "", 0));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching forecast: " + e.getMessage(), e);
                callback.onWeatherError("पूर्वानुमान नहीं मिल सका। इंटरनेट कनेक्शन जाँचें।");
            }
        });
    }

    public void getFarmingAlerts(Location location, AlertCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Checking farming alerts for location: " + location.getLatitude() + ", " + location.getLongitude());
                
                APIService.CurrentWeather current = fetchCurrent(location);
                WeatherAlert alert = alertFromWeather(current);
                if (alert != null) callback.onAlertReceived(alert);
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching alerts: " + e.getMessage(), e);
            }
        });
    }
    
    public void getWeatherAlerts(Location location, AlertCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Checking weather alerts for location: " + location.getLatitude() + ", " + location.getLongitude());
                
                APIService.CurrentWeather current = fetchCurrent(location);
                WeatherAlert alert = alertFromWeather(current);
                if (alert != null) callback.onAlertReceived(alert);
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching weather alerts: " + e.getMessage(), e);
            }
        });
    }

    private APIService.CurrentWeather fetchCurrent(Location location) throws Exception {
        retrofit2.Response<APIService.OpenMeteoResponse> response = apiService.openMeteo()
                .current(location.getLatitude(), location.getLongitude(), 1).execute();
        if (!response.isSuccessful() || response.body() == null || response.body().current == null) throw new Exception("Weather unavailable");
        return response.body().current;
    }
    private WeatherAlert alertFromWeather(APIService.CurrentWeather weather) {
        long now = System.currentTimeMillis();
        if (weather.temperature >= 38) return new WeatherAlert("Heat", "गर्मी", "High temperature", "तापमान अधिक है; खेत में काम करते समय सावधानी रखें और सिंचाई की स्थिति देखें।", "Medium", now, now + 6 * 60 * 60 * 1000L);
        if (weather.weatherCode >= 95) return new WeatherAlert("Thunderstorm", "आंधी", "Thunderstorm conditions", "गरज-चमक की स्थिति है; खुले खेत और बिजली के उपकरणों से दूर रहें।", "High", now, now + 3 * 60 * 60 * 1000L);
        if (weather.weatherCode >= 51 && weather.weatherCode <= 82) return new WeatherAlert("Precipitation", "वर्षा", "Precipitation conditions", "वर्षा की स्थिति है; कटाई और खुले में भंडारण की योजना सावधानी से करें।", "Low", now, now + 3 * 60 * 60 * 1000L);
        return null;
    }
    private String condition(int code) { if (code == 0) return "Clear"; if (code <= 3) return "Cloudy"; if (code <= 48) return "Fog"; if (code <= 67) return "Rain"; if (code <= 77) return "Snow"; if (code <= 82) return "Showers"; if (code >= 95) return "Thunderstorm"; return "Unknown"; }
    private String conditionHindi(int code) { if (code == 0) return "साफ़ आसमान"; if (code <= 3) return "बादल"; if (code <= 48) return "कोहरा"; if (code <= 67) return "बारिश"; if (code <= 77) return "बर्फ़"; if (code <= 82) return "बौछारें"; if (code >= 95) return "आंधी-तूफ़ान"; return "स्थिति अज्ञात"; }
    private String direction(int degrees) { String[] values = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"}; return values[(int) Math.round(degrees / 45.0) % 8]; }

    private WeatherData generateWeatherData(Location location) {
        // Generate realistic weather based on location and time
        double baseTemp = 25.0; // Base temperature in Celsius
        
        // Adjust temperature based on time of day (simplified)
        long currentHour = (System.currentTimeMillis() / (1000 * 60 * 60)) % 24;
        if (currentHour >= 6 && currentHour <= 18) {
            baseTemp += random.nextDouble() * 10; // Daytime: warmer
        } else {
            baseTemp -= random.nextDouble() * 8; // Nighttime: cooler
        }
        
        // Adjust for location (latitude effect)
        baseTemp -= Math.abs(location.getLatitude()) * 0.5;
        
        // Generate other weather parameters
        int humidity = 40 + random.nextInt(40); // 40-80%
        String[] conditions = {"Sunny", "Partly Cloudy", "Cloudy", "Light Rain", "Overcast"};
        String[] conditionsHindi = {"धूप", "आंशिक बादल", "बादल", "हल्की बारिश", "मेघाच्छादित"};
        int conditionIndex = random.nextInt(conditions.length);
        
        double windSpeed = 5 + random.nextDouble() * 15; // 5-20 km/h
        String[] directions = {"North", "South", "East", "West", "NE", "NW", "SE", "SW"};
        String windDirection = directions[random.nextInt(directions.length)];
        int visibility = 5 + random.nextInt(6); // 5-10 km
        
        return new WeatherData(baseTemp, humidity, conditions[conditionIndex], 
                             conditionsHindi[conditionIndex], windSpeed, windDirection, visibility);
    }

    private WeatherData generateForecastData(Location location, int dayOffset) {
        // Generate forecast data for specific day
        double baseTemp = 25.0 + (dayOffset * 2) - random.nextDouble() * 4;
        int humidity = 45 + random.nextInt(30);
        String[] conditions = {"Sunny", "Partly Cloudy", "Cloudy", "Light Rain"};
        String[] conditionsHindi = {"धूप", "आंशिक बादल", "बादल", "हल्की बारिश"};
        
        return new WeatherData(baseTemp, humidity, conditions[random.nextInt(conditions.length)],
                             conditionsHindi[random.nextInt(conditionsHindi.length)],
                             8 + random.nextDouble() * 12, "SE", 7 + random.nextInt(4));
    }

    private WeatherAlert generateFarmingAlert(Location location) {
        // Generate farming-specific weather alerts
        double temp = 20 + random.nextDouble() * 20;
        int humidity = 40 + random.nextInt(40);
        
        if (temp > 35 && humidity > 70) {
            return new WeatherAlert(
                "Heat Stress Alert",
                "ताप तनाव अलर्ट",
                "High temperature and humidity may cause heat stress in crops. Ensure proper irrigation.",
                "उच्च तापमान और आर्द्रता फसलों में ताप तनाव पैदा कर सकती है। उचित सिंचाई सुनिश्चित करें।",
                "Medium",
                System.currentTimeMillis(),
                System.currentTimeMillis() + (24 * 60 * 60 * 1000)
            );
        } else if (temp < 5) {
            return new WeatherAlert(
                "Frost Alert",
                "पाला अलर्ट",
                "Low temperature may cause frost damage. Protect sensitive crops.",
                "कम तापमान पाला क्षति का कारण बन सकता है। संवेदनशील फसलों की सुरक्षा करें।",
                "High",
                System.currentTimeMillis(),
                System.currentTimeMillis() + (12 * 60 * 60 * 1000)
            );
        } else if (humidity > 80) {
            return new WeatherAlert(
                "Disease Risk Alert",
                "रोग जोखिम अलर्ट",
                "High humidity increases disease risk. Monitor crops for early symptoms.",
                "उच्च आर्द्रता रोग जोखिम बढ़ाती है। फसलों की निगरानी करें।",
                "Medium",
                System.currentTimeMillis(),
                System.currentTimeMillis() + (24 * 60 * 60 * 1000)
            );
        }
        
        return null; // No alert needed
    }

    private void startPeriodicUpdates() {
        // Update weather every 30 minutes
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                Log.d(TAG, "Performing periodic weather update...");
                // This could trigger background updates or notifications
            } catch (Exception e) {
                Log.e(TAG, "Error in periodic update: " + e.getMessage(), e);
            }
        }, 30, 30, TimeUnit.MINUTES);
    }

    public void shutdown() {
        executorService.shutdown();
        scheduledExecutor.shutdown();
    }
}
