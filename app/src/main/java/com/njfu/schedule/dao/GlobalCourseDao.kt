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

    @Query("SELECT * FROM global_courses WHERE type = :type AND (courseName LIKE '%' || :keyword || '%' OR teacher LIKE '%' || :keyword || '%' OR room LIKE '%' || :keyword || '%' OR className LIKE '%' || :keyword || '%' OR collegeName LIKE '%' || :keyword || '%' OR typeLabel LIKE '%' || :keyword || '%' OR entityName LIKE '%' || :keyword || '%' OR term LIKE '%' || :keyword || '%' OR rawText LIKE '%' || :keyword || '%' OR rawHtml LIKE '%' || :keyword || '%' OR rawLinesJson LIKE '%' || :keyword || '%' OR sectionNumbers LIKE '%' || :keyword || '%')")
    fun search(type: String, keyword: String): Flow<List<GlobalCourseEntity>>

    @Query("SELECT * FROM global_courses WHERE type = :type AND (courseName LIKE '%' || :keyword || '%' OR teacher LIKE '%' || :keyword || '%' OR room LIKE '%' || :keyword || '%' OR className LIKE '%' || :keyword || '%' OR collegeName LIKE '%' || :keyword || '%' OR typeLabel LIKE '%' || :keyword || '%' OR entityName LIKE '%' || :keyword || '%' OR term LIKE '%' || :keyword || '%' OR rawText LIKE '%' || :keyword || '%' OR rawHtml LIKE '%' || :keyword || '%' OR rawLinesJson LIKE '%' || :keyword || '%' OR sectionNumbers LIKE '%' || :keyword || '%') AND day = :day")
    fun searchWithDay(type: String, keyword: String, day: Int): Flow<List<GlobalCourseEntity>>

    @Query("SELECT COUNT(*) FROM global_courses WHERE type = :type")
    suspend fun countByType(type: String): Int

    @Query("SELECT COUNT(*) FROM global_courses")
    suspend fun countAll(): Int

    @Query("SELECT DISTINCT room FROM global_courses WHERE type = 'jx0601' AND room != '' ORDER BY room")
    suspend fun getAllRooms(): List<String>

    @Query("SELECT DISTINCT teacher FROM global_courses WHERE type = 'jg0101' AND teacher != '' ORDER BY teacher")
    suspend fun getAllTeachers(): List<String>

    @Query("SELECT DISTINCT className FROM global_courses WHERE type = 'bj0101' AND className != '' ORDER BY className")
    suspend fun getAllClasses(): List<String>

    @Query("SELECT DISTINCT courseName FROM global_courses WHERE type = 'kc0101' AND courseName != '' ORDER BY courseName")
    suspend fun getAllCourses(): List<String>

    @Query("SELECT * FROM global_courses WHERE type = :type")
    suspend fun getByTypeSync(type: String): List<GlobalCourseEntity>

    @Query("SELECT * FROM global_courses WHERE type = 'jx0601' AND room = :room")
    suspend fun getCoursesByRoom(room: String): List<GlobalCourseEntity>
}
