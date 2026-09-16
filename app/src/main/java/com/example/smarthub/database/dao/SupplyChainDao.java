package com.example.smarthub.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.smarthub.database.entities.ColdStorageLotEntity;
import com.example.smarthub.database.entities.SupplyChainEventEntity;
import java.util.List;

@Dao
public interface SupplyChainDao {
    @Insert long insertLot(ColdStorageLotEntity lot);
    @Insert long insertEvent(SupplyChainEventEntity event);
    @Query("SELECT * FROM cold_storage_lots WHERE ownerId = :ownerId ORDER BY updatedAt DESC")
    LiveData<List<ColdStorageLotEntity>> observeLots(String ownerId);
    @Query("SELECT * FROM cold_storage_lots WHERE syncState = 'pending' ORDER BY updatedAt ASC")
    List<ColdStorageLotEntity> getPendingLots();
    @Query("SELECT * FROM supply_chain_events WHERE syncState = 'pending' ORDER BY occurredAt ASC")
    List<SupplyChainEventEntity> getPendingEvents();
    @Query("UPDATE cold_storage_lots SET syncState = :state WHERE id = :id")
    void updateLotSyncState(long id, String state);
    @Query("UPDATE cold_storage_lots SET remoteId = :remoteId, syncState = :state WHERE id = :id")
    void updateLotRemoteId(long id, String remoteId, String state);
    @Query("UPDATE supply_chain_events SET syncState = :state WHERE id = :id")
    void updateEventSyncState(long id, String state);
}
