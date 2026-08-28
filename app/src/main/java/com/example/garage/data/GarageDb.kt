package com.example.garage.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Машина («папка» в гараже)
@Entity
data class Car(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String,
    val model: String,
    val vin: String,
    val photoPath: String? = null
)

// Блок ТО (ТО-1, ТО-2…)
@Entity
data class Maintenance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val carId: Long,
    val name: String,
    val mileage: Int,
    val date: Long = System.currentTimeMillis()
)

// Что делалось в ТО
@Entity
data class WorkItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val maintenanceId: Long,
    val title: String,
    val partNumber: String = "",
    val price: Int = 0
)

@Dao
interface GarageDao {
    @Query("SELECT * FROM Car ORDER BY id")
    fun cars(): Flow<List<Car>>
    @Insert
    suspend fun addCar(car: Car): Long
    @Query("UPDATE Car SET photoPath = :path WHERE id = :id")
    suspend fun setCarPhoto(id: Long, path: String)

    @Query("SELECT * FROM Maintenance WHERE carId = :carId ORDER BY date")
    fun maintenances(carId: Long): Flow<List<Maintenance>>
    @Insert
    suspend fun addMaintenance(m: Maintenance)
    @Query("SELECT * FROM Maintenance WHERE id = :id")
    suspend fun maintenance(id: Long): Maintenance?

    @Query("SELECT * FROM WorkItem WHERE maintenanceId = :mid ORDER BY id")
    fun items(mid: Long): Flow<List<WorkItem>>
    @Insert
    suspend fun addItem(item: WorkItem)
}

@Database(entities = [Car::class, Maintenance::class, WorkItem::class], version = 1)
abstract class GarageDb : RoomDatabase() {
    abstract fun dao(): GarageDao
    companion object {
        @Volatile private var INSTANCE: GarageDb? = null
        fun get(context: Context): GarageDb =
            INSTANCE ?: Room.databaseBuilder(context, GarageDb::class.java, "garage.db")
                .build().also { INSTANCE = it }
    }
}
