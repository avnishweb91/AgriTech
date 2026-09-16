package com.example.smarthub.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Offline copy of a lot held in cold storage. Sync status is intentionally explicit. */
@Entity(tableName = "cold_storage_lots")
public class ColdStorageLotEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    public String remoteId;
    public String ownerId;
    public String cropName;
    public String cropNameHindi;
    public double quantity;
    public String unit;
    public String storageName;
    public String chamberCode;
    public double temperatureCelsius;
    public String status; // stored, dispatched, spoiled
    public long storedAt;
    public long expectedDispatchAt;
    public String syncState; // pending, synced, failed
    public long updatedAt;

    public ColdStorageLotEntity() {}
}
