package com.expeysi.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE deleted = 0 AND type = :type ORDER BY name")
    fun observeActive(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deleted = 0 AND type = :type ORDER BY name")
    suspend fun listActive(type: String): List<CategoryEntity>

    @Query("SELECT * FROM categories")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE dirty = 1")
    suspend fun listDirty(): List<CategoryEntity>

    /** Сбрасывает dirty только если updatedAt строки ещё равен отправленной версии. */
    @Query("UPDATE categories SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long): Int

    @Query("UPDATE categories SET dirty = 1 WHERE id = :id")
    suspend fun markDirty(id: String)

    @Query("DELETE FROM categories")
    suspend fun clear()
}
