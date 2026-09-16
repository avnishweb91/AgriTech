package com.example.smarthub.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.smarthub.database.entities.UserEntity;

import java.util.List;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(UserEntity user);
    
    @Update
    void updateUser(UserEntity user);
    
    @Delete
    void deleteUser(UserEntity user);
    
    @Query("SELECT * FROM users WHERE userId = :userId")
    LiveData<UserEntity> getUserById(String userId);
    
    @Query("SELECT * FROM users WHERE phoneNumber = :phoneNumber")
    LiveData<UserEntity> getUserByPhone(String phoneNumber);
    
    @Query("SELECT * FROM users WHERE status = 'active'")
    LiveData<List<UserEntity>> getAllActiveUsers();
    
    @Query("DELETE FROM users WHERE userId = :userId")
    void deleteUserById(String userId);
    
    @Query("UPDATE users SET lastUpdated = :timestamp WHERE userId = :userId")
    void updateLastUpdated(String userId, long timestamp);
}
