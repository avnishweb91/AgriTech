package com.example.smarthub.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "supply_chain_events")
public class SupplyChainEventEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    public String lotId;
    public String eventType; // listed, accepted, stored, dispatched, delivered
    public String note;
    public double latitude;
    public double longitude;
    public long occurredAt;
    public String syncState; // pending, synced, failed

    public SupplyChainEventEntity() {}
}
