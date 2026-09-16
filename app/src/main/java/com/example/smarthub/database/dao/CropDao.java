package com.example.smarthub.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.smarthub.database.entities.CropEntity;

import java.util.List;

@Dao
public interface CropDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCrop(CropEntity crop);
    
    @Update
    void updateCrop(CropEntity crop);
    
    @Delete
    void deleteCrop(CropEntity crop);
    
    @Query("SELECT * FROM crops WHERE userId = :userId ORDER BY createdAt DESC")
    LiveData<List<CropEntity>> getCropsByUserId(String userId);
    
    @Query("SELECT * FROM crops WHERE userId = :userId AND status = :status ORDER BY createdAt DESC")
    LiveData<List<CropEntity>> getCropsByUserIdAndStatus(String userId, String status);
    
    @Query("SELECT * FROM crops WHERE id = :cropId")
    LiveData<CropEntity> getCropById(int cropId);
    
    @Query("SELECT * FROM crops WHERE userId = :userId AND cropName = :cropName ORDER BY createdAt DESC")
    LiveData<List<CropEntity>> getCropsByUserIdAndCropName(String userId, String cropName);
    
    @Query("SELECT * FROM crops WHERE userId = :userId AND status = 'ready' ORDER BY expectedHarvestDate ASC")
    LiveData<List<CropEntity>> getReadyCropsByUserId(String userId);
    
    @Query("DELETE FROM crops WHERE userId = :userId")
    void deleteCropsByUserId(String userId);
    
    @Query("UPDATE crops SET status = :status, lastUpdated = :timestamp WHERE id = :cropId")
    void updateCropStatus(int cropId, String status, long timestamp);
}
