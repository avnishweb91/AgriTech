package com.example.smarthub.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "crops")
public class CropEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String userId;
    public String cropName;
    public String cropNameHindi;
    public String variety;
    public double quantity;
    public String unit;
    public String quality;
    public double pricePerUnit;
    public String location;
    public String state;
    public long sowingDate;
    public long expectedHarvestDate;
    public long actualHarvestDate;
    public String status; // "growing", "ready", "harvested", "sold"
    public long createdAt;
    public long lastUpdated;
    
    public CropEntity() {}
    
    public CropEntity(String userId, String cropName, String cropNameHindi, String variety,
                     double quantity, String unit, String quality, double pricePerUnit,
                     String location, String state, long sowingDate, long expectedHarvestDate,
                     long actualHarvestDate, String status, long createdAt, long lastUpdated) {
        this.userId = userId;
        this.cropName = cropName;
        this.cropNameHindi = cropNameHindi;
        this.variety = variety;
        this.quantity = quantity;
        this.unit = unit;
        this.quality = quality;
        this.pricePerUnit = pricePerUnit;
        this.location = location;
        this.state = state;
        this.sowingDate = sowingDate;
        this.expectedHarvestDate = expectedHarvestDate;
        this.actualHarvestDate = actualHarvestDate;
        this.status = status;
        this.createdAt = createdAt;
        this.lastUpdated = lastUpdated;
    }
}
