package com.example.smarthub.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.annotation.NonNull;

import com.example.smarthub.database.dao.CropDao;
import com.example.smarthub.database.dao.UserDao;
import com.example.smarthub.database.entities.CropEntity;
import com.example.smarthub.database.entities.UserEntity;
import com.example.smarthub.database.entities.WeatherCacheEntity;
import com.example.smarthub.database.entities.ColdStorageLotEntity;
import com.example.smarthub.database.entities.SupplyChainEventEntity;

@Database(
    entities = {
        UserEntity.class,
        CropEntity.class,
        WeatherCacheEntity.class,
        ColdStorageLotEntity.class,
        SupplyChainEventEntity.class
    },
    version = 3,
    exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "agri_tech_db";
    private static volatile AppDatabase INSTANCE;
    
    // Database migrations
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull androidx.sqlite.db.SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS cold_storage_lots (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ownerId TEXT, cropName TEXT, cropNameHindi TEXT, quantity REAL NOT NULL, unit TEXT, storageName TEXT, chamberCode TEXT, temperatureCelsius REAL NOT NULL, status TEXT, storedAt INTEGER NOT NULL, expectedDispatchAt INTEGER NOT NULL, syncState TEXT, updatedAt INTEGER NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS supply_chain_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, lotId TEXT, eventType TEXT, note TEXT, latitude REAL NOT NULL, longitude REAL NOT NULL, occurredAt INTEGER NOT NULL, syncState TEXT)");
        }
    };
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(@NonNull androidx.sqlite.db.SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE cold_storage_lots ADD COLUMN remoteId TEXT");
        }
    };
    
    public abstract UserDao userDao();
    public abstract CropDao cropDao();
    public abstract com.example.smarthub.database.dao.SupplyChainDao supplyChainDao();
    
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        DATABASE_NAME
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
