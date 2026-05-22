package com.njfu.schedule.dao

import androidx.room.*
import com.njfu.schedule.bean.CourseBaseBean
import com.njfu.schedule.bean.CourseDetailBean
import com.njfu.schedule.bean.TableBean
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourseBase(vararg beans: CourseBaseBean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourseDetail(vararg beans: CourseDetailBean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTable(bean: TableBean): Long

    @Query("SELECT * FROM CourseBaseBean WHERE tableId = :tableId")
    fun getCourseBaseByTable(tableId: Int): Flow<List<CourseBaseBean>>

    @Query("SELECT * FROM CourseDetailBean WHERE tableId = :tableId")
    fun getCourseDetailByTable(tableId: Int): Flow<List<CourseDetailBean>>

    @Query("SELECT * FROM CourseDetailBean WHERE tableId = :tableId AND day = :day")
    fun getCourseDetailByDay(tableId: Int, day: Int): Flow<List<CourseDetailBean>>

    @Query("SELECT * FROM TableBean WHERE id = :id")
    suspend fun getTableById(id: Int): TableBean?

    @Query("SELECT * FROM TableBean LIMIT 1")
    suspend fun getFirstTable(): TableBean?

    @Query("DELETE FROM CourseBaseBean WHERE tableId = :tableId")
    suspend fun deleteCoursesByTable(tableId: Int)

    @Query("DELETE FROM CourseDetailBean WHERE tableId = :tableId")
    suspend fun deleteDetailsByTable(tableId: Int)
}
