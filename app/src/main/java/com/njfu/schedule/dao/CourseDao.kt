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

    @Update
    suspend fun updateTable(bean: TableBean)

    @Query("SELECT * FROM CourseBaseBean WHERE tableId = :tableId")
    fun getCourseBaseByTable(tableId: Int): Flow<List<CourseBaseBean>>

    @Query("SELECT * FROM CourseDetailBean WHERE tableId = :tableId")
    fun getCourseDetailByTable(tableId: Int): Flow<List<CourseDetailBean>>

    @Query("SELECT * FROM TableBean WHERE id = :id")
    suspend fun getTableById(id: Int): TableBean?

    @Query("SELECT * FROM TableBean LIMIT 1")
    suspend fun getFirstTable(): TableBean?

    @Query("DELETE FROM CourseBaseBean WHERE tableId = :tableId")
    suspend fun deleteCoursesByTable(tableId: Int)

    @Query("DELETE FROM CourseDetailBean WHERE tableId = :tableId")
    suspend fun deleteDetailsByTable(tableId: Int)

    @Query("DELETE FROM CourseDetailBean WHERE id = :courseId AND tableId = :tableId")
    suspend fun deleteDetailsByCourseId(courseId: Int, tableId: Int)

    @Query("DELETE FROM CourseBaseBean WHERE id = :courseId AND tableId = :tableId")
    suspend fun deleteCourseBase(courseId: Int, tableId: Int)

    @Query("SELECT * FROM CourseBaseBean WHERE id = :courseId AND tableId = :tableId")
    suspend fun getCourseBaseById(courseId: Int, tableId: Int): CourseBaseBean?

    @Query("SELECT * FROM CourseDetailBean WHERE id = :courseId AND tableId = :tableId")
    suspend fun getCourseDetailsById(courseId: Int, tableId: Int): List<CourseDetailBean>
}

    @Query("SELECT MAX(id) FROM CourseBaseBean WHERE tableId = :tableId")
    suspend fun getMaxCourseId(tableId: Int): Int?

    @Query("SELECT * FROM CourseDetailBean WHERE tableId = :tableId")
    fun getCourseDetailsById_sync(tableId: Int): List<CourseDetailBean>

    @Query("SELECT * FROM CourseBaseBean WHERE tableId = :tableId")
    fun getCourseBaseById_sync(tableId: Int): List<CourseBaseBean>
