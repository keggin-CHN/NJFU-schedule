package com.njfu.schedule.dao

import androidx.room.*
import com.njfu.schedule.bean.GlobalCourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GlobalCourseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<GlobalCourseEntity>)

    @Query("DELETE FROM global_courses WHERE type = :type")
    suspend fun deleteByType(type: String)

    @Query("DELETE FROM global_courses")
    suspend fun deleteAll()

    @Query("SELECT * FROM global_courses WHERE type = :type")
    fun getByType(type: String): Flow<List<GlobalCourseEntity>>

    @Query("SELECT * FROM global_courses WHERE type = :type AND (courseName LIKE '%' || :keyword || '%' OR teacher LIKE '%' || :keyword || '%' OR room LIKE '%' || :keyword || '%' OR className LIKE '%' || :keyword || '%')")
    fun search(type: String, keyword: String): Flow<List<GlobalCourseEntity>>

    @Query("SELECT * FROM global_courses WHERE type = :type AND (courseName LIKE '%' || :keyword || '%' OR teacher LIKE '%' || :keyword || '%' OR room LIKE '%' || :keyword || '%' OR className LIKE '%' || :keyword || '%') AND day = :day")
    fun searchWithDay(type: String, keyword: String, day: Int): Flow<List<GlobalCourseEntity>>

    @Query("SELECT COUNT(*) FROM global_courses WHERE type = :type")
    suspend fun countByType(type: String): Int

    @Query("SELECT COUNT(*) FROM global_courses")
    suspend fun countAll(): Int
}
