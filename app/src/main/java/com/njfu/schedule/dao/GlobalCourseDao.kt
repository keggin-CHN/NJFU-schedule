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

    /**
     * 原子替换：删除指定类型后插入新数据，全部在事务中完成。
     * 比分别调用 deleteByType + insertAll 更安全、更快。
     */
    @Transaction
    suspend fun replaceByType(type: String, courses: List<GlobalCourseEntity>) {
        deleteByType(type)
        if (courses.isNotEmpty()) insertAll(courses)
    }

    /**
     * 按学期清理旧数据，保留最近 [keepTermCount] 个学期。
     * 删除不在 keepTerms 列表中的所有数据。
     */
    @Query("DELETE FROM global_courses WHERE term NOT IN (:keepTerms)")
    suspend fun deleteOldTerms(keepTerms: List<String>)

    /**
     * 获取数据库中所有不同的学期，按降序排列。
     */
    @Query("SELECT DISTINCT term FROM global_courses WHERE term != '' ORDER BY term DESC")
    suspend fun getAllTerms(): List<String>

    // ==================== 常规查询 ====================

    @Query("SELECT * FROM global_courses WHERE type = :type")
    fun getByType(type: String): Flow<List<GlobalCourseEntity>>

    @Query("SELECT * FROM global_courses WHERE type = :type")
    suspend fun getByTypeSync(type: String): List<GlobalCourseEntity>

    @Query("SELECT * FROM global_courses WHERE type = 'jx0601' AND room = :room")
    suspend fun getCoursesByRoom(room: String): List<GlobalCourseEntity>

    // ==================== LIKE 搜索（兼容旧代码）====================

    @Query("SELECT * FROM global_courses WHERE type = :type AND (courseName LIKE '%' || :keyword || '%' OR teacher LIKE '%' || :keyword || '%' OR room LIKE '%' || :keyword || '%' OR className LIKE '%' || :keyword || '%' OR collegeName LIKE '%' || :keyword || '%' OR typeLabel LIKE '%' || :keyword || '%' OR entityName LIKE '%' || :keyword || '%' OR term LIKE '%' || :keyword || '%' OR rawText LIKE '%' || :keyword || '%' OR rawHtml LIKE '%' || :keyword || '%' OR rawLinesJson LIKE '%' || :keyword || '%' OR sectionNumbers LIKE '%' || :keyword || '%')")
    fun search(type: String, keyword: String): Flow<List<GlobalCourseEntity>>

    @Query("SELECT * FROM global_courses WHERE type = :type AND (courseName LIKE '%' || :keyword || '%' OR teacher LIKE '%' || :keyword || '%' OR room LIKE '%' || :keyword || '%' OR className LIKE '%' || :keyword || '%' OR collegeName LIKE '%' || :keyword || '%' OR typeLabel LIKE '%' || :keyword || '%' OR entityName LIKE '%' || :keyword || '%' OR term LIKE '%' || :keyword || '%' OR rawText LIKE '%' || :keyword || '%' OR rawHtml LIKE '%' || :keyword || '%' OR rawLinesJson LIKE '%' || :keyword || '%' OR sectionNumbers LIKE '%' || :keyword || '%') AND day = :day")
    fun searchWithDay(type: String, keyword: String, day: Int): Flow<List<GlobalCourseEntity>>

    // ==================== FTS 全文搜索 ====================

    /**
     * 使用 FTS4 全文搜索，比 LIKE 更快。
     * 注意：FTS4 默认分词器对中文按整词匹配，适合搜索完整课程名/教师名/教室名。
     * 拼音首字母搜索由 Activity 层内存过滤实现。
     */
    @Query(
        "SELECT gc.* FROM global_courses gc " +
        "INNER JOIN global_courses_fts fts ON gc.uid = fts.rowid " +
        "WHERE gc.type = :type AND global_courses_fts MATCH :query"
    )
    fun ftsSearch(type: String, query: String): Flow<List<GlobalCourseEntity>>

    @Query(
        "SELECT gc.* FROM global_courses gc " +
        "INNER JOIN global_courses_fts fts ON gc.uid = fts.rowid " +
        "WHERE gc.type = :type AND global_courses_fts MATCH :query AND gc.day = :day"
    )
    fun ftsSearchWithDay(type: String, query: String, day: Int): Flow<List<GlobalCourseEntity>>

    @Query(
        "SELECT gc.* FROM global_courses gc " +
        "INNER JOIN global_courses_fts fts ON gc.uid = fts.rowid " +
        "WHERE global_courses_fts MATCH :query"
    )
    suspend fun ftsSearchAll(query: String): List<GlobalCourseEntity>

    // ==================== 聚合查询 ====================

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

    // ==================== 跨类型关联查询 ====================

    /**
     * 从教师课表(jg0101)中获取教师-课程-时间映射。
     * 用于在课程课表/班级课表/教室课表中补充教师信息。
     */
    @Query("SELECT DISTINCT teacher FROM global_courses WHERE type = 'jg0101' AND courseName = :courseName AND day = :day AND sectionNumbers = :sectionNumbers AND teacher != '' LIMIT 1")
    suspend fun findTeacherForCourse(courseName: String, day: Int, sectionNumbers: String): String?

    /**
     * 从教师课表(jg0101)中按课程名和班级查找教师。
     * 更精确的匹配：同时匹配课程名和班级名。
     */
    @Query("SELECT DISTINCT teacher FROM global_courses WHERE type = 'jg0101' AND courseName = :courseName AND className LIKE '%' || :className || '%' AND teacher != '' LIMIT 1")
    suspend fun findTeacherForCourseClass(courseName: String, className: String): String?

    /**
     * 从教室课表(jx0601)中获取指定时间段被占用的教室列表。
     * 用于空闲教室查询：找出在指定 (week, day, section) 有课的教室。
     */
    @Query("SELECT DISTINCT room FROM global_courses WHERE type = 'jx0601' AND day = :day AND sectionsStr LIKE '%' || :sectionStr || '%' AND weeksStr LIKE '%' || :weekStr || '%' AND room != ''")
    suspend fun getOccupiedRooms(day: Int, sectionStr: String, weekStr: String): List<String>

    /**
     * 获取所有不同的教室名（去重、排序），用于空闲教室查询的基础列表。
     */
    @Query("SELECT DISTINCT room FROM global_courses WHERE type = 'jx0601' AND room != '' ORDER BY room")
    suspend fun getAllRoomNames(): List<String>

    /**
     * 获取指定教室在指定时间段的课程。
     * 用于空闲教室查询的详情展示。
     */
    @Query("SELECT * FROM global_courses WHERE type = 'jx0601' AND room = :room AND day = :day AND sectionsStr LIKE '%' || :sectionStr || '%' AND weeksStr LIKE '%' || :weekStr || '%'")
    suspend fun getRoomScheduleAt(room: String, day: Int, sectionStr: String, weekStr: String): List<GlobalCourseEntity>
}
