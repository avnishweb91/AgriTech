package com.example.smarthub.database.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class UserEntity {
    @PrimaryKey
    @NonNull
    public String userId;
    
    public String phoneNumber;
    public String nameHindi;
    public String nameEnglish;
    public String state;
    public String district;
    public String village;
    public double landSize;
    public String primaryCrop;
    public long createdAt;
    public long lastUpdated;
    public String status;
    
    public UserEntity() {}
    
    public UserEntity(@NonNull String userId, String phoneNumber, String nameHindi, String nameEnglish,
                     String state, String district, String village, double landSize, 
                     String primaryCrop, long createdAt, long lastUpdated, String status) {
        this.userId = userId;
        this.phoneNumber = phoneNumber;
        this.nameHindi = nameHindi;
        this.nameEnglish = nameEnglish;
        this.state = state;
        this.district = district;
        this.village = village;
        this.landSize = landSize;
        this.primaryCrop = primaryCrop;
        this.createdAt = createdAt;
        this.lastUpdated = lastUpdated;
        this.status = status;
    }
}
