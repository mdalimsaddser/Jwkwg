package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserDao {
    // Bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmarkByUrl(url: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url LIMIT 1)")
    fun matchesBookmarkFlow(url: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url LIMIT 1)")
    suspend fun isBookmarked(url: String): Boolean

    // History
    @Query("SELECT * FROM history_items ORDER BY timestamp DESC LIMIT 400")
    fun getAllHistory(): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(historyItem: HistoryItem)

    @Query("DELETE FROM history_items WHERE id = :id")
    suspend fun deleteHistoryById(id: Int)

    @Query("DELETE FROM history_items")
    suspend fun clearAllHistory()

    // Tabs
    @Query("SELECT * FROM tab_items ORDER BY timestamp ASC")
    fun getAllTabs(): Flow<List<TabItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tabItem: TabItem): Long

    @Update
    suspend fun updateTab(tabItem: TabItem)

    @Query("DELETE FROM tab_items WHERE id = :id")
    suspend fun deleteTabById(id: Int)

    @Query("DELETE FROM tab_items")
    suspend fun deleteAllTabs()
}

@Database(entities = [Bookmark::class, HistoryItem::class, TabItem::class], version = 1, exportSchema = false)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun browserDao(): BrowserDao

    companion object {
        @Volatile
        private var INSTANCE: BrowserDatabase? = null

        fun getDatabase(context: Context): BrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "sabbir_browser_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
