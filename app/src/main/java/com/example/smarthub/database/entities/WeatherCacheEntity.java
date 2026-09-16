package com.example.smarthub.database.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "weather_cache")
public class WeatherCacheEntity {
    @PrimaryKey
    @NonNull
    public String locationKey; // "latitude_longitude"
    
    public double latitude;
    public double longitude;
    public String weatherData; // JSON string of weather data
    public long timestamp;
    public long expiresAt;
    
    public WeatherCacheEntity() {}
    
    public WeatherCacheEntity(@NonNull String locationKey, double latitude, double longitude, 
                            String weatherData, long timestamp, long expiresAt) {
        this.locationKey = locationKey;
        this.latitude = latitude;
        this.longitude = longitude;
        this.weatherData = weatherData;
        this.timestamp = timestamp;
        this.expiresAt = expiresAt;
    }
}
