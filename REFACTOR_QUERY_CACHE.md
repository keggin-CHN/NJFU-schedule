# 全校课表查询本地缓存重构 - 交接文档

## 背景与目标

当前查询页（教师/教室/班级/课程课表）的逻辑问题：
1. 每次查询都要去南林教务系统现拉数据，单次查询耗时长（HTML 大几十 KB，需要解析）
2. 筛选条件繁琐，多个 Spinner 联动，UI 体验混乱
3. 用户需要重复输入关键词、等待网络往返

**目标**：把全校课表预先缓存到本地数据库，查询时只走本地搜索，秒出结果。每月后台自动刷新一次，用户也可手动同步（带进度条）。

## 已完成的部分

下面这些代码我已经写好提交了：

### 1. Room Entity 与 DAO

- [app/src/main/java/com/njfu/schedule/bean/GlobalCourseEntity.kt](app/src/main/java/com/njfu/schedule/bean/GlobalCourseEntity.kt) — 全校课表的 Room 实体（已建好）
- [app/src/main/java/com/njfu/schedule/dao/GlobalCourseDao.kt](app/src/main/java/com/njfu/schedule/dao/GlobalCourseDao.kt) — 增删查 DAO，含按 type 搜索（已建好）
- [app/src/main/java/com/njfu/schedule/AppDatabase.kt](app/src/main/java/com/njfu/schedule/AppDatabase.kt) — 数据库版本升到 4，加了 MIGRATION_3_4（已改）

### 2. WorkManager 后台任务

- [app/src/main/java/com/njfu/schedule/worker/GlobalCacheWorker.kt](app/src/main/java/com/njfu/schedule/worker/GlobalCacheWorker.kt) — `CoroutineWorker`，登录后依次抓 4 类全校课表写入数据库，通过 `setProgress` 报告进度（已建好）
- [app/src/main/java/com/njfu/schedule/worker/GlobalCacheScheduler.kt](app/src/main/java/com/njfu/schedule/worker/GlobalCacheScheduler.kt) — 调度器，提供 `scheduleOneShot()` 和 `schedulePeriodic()`（**周期已设为 30 天 / 1 个月**）（已建好）

### 3. 触发点

- [app/src/main/java/com/njfu/schedule/ui/import_/ImportActivity.kt](app/src/main/java/com/njfu/schedule/ui/import_/ImportActivity.kt) — 用户导入个人课表成功后，自动调 `GlobalCacheScheduler.scheduleOneShot()` 和 `schedulePeriodic()` 启动首次抓取和月度任务（已改）

### 4. Gradle 依赖

- [app/build.gradle.kts](app/build.gradle.kts) — 添加 `implementation("androidx.work:work-runtime-ktx:2.9.0")`（已加）

## 还没做的部分（需要你接着做）

### 任务 A：重写 GlobalScheduleActivity（最重要）

文件：[app/src/main/java/com/njfu/schedule/ui/schedule/GlobalScheduleActivity.kt](app/src/main/java/com/njfu/schedule/ui/schedule/GlobalScheduleActivity.kt)

把现在"现场抓网络"的逻辑改成"读本地数据库"。要点：

1. **去掉 `loginAndLoad()` 自动登录** — 进页面时不再发网络请求
2. **`performSearch(keyword)` 改为查本地数据库**：
   ```kotlin
   private fun performSearch(keyword: String) {
       lifecycleScope.launch {
           val dao = AppDatabase.getDatabase(this@GlobalScheduleActivity).globalCourseDao()
           val results = withContext(Dispatchers.IO) {
               if (keyword.isBlank()) {
                   dao.getByType(currentType).first()  // 用 kotlinx.coroutines.flow.first
               } else {
                   dao.search(currentType, keyword).first()
               }
           }
           val converted = results.map {
               GlobalCourseInfo(it.courseName, it.teacher, it.room, it.weeksStr, it.day, it.sectionsStr, it.className)
           }
           courseAdapter.submitList(converted)
           if (converted.isEmpty()) showError("本地没有数据，请先点右上角同步")
           else showResults()
       }
   }
   ```
3. **加一个手动同步按钮**（在 toolbar 加 menu，或 fragment_query.xml 顶部加一个按钮）：
   ```kotlin
   private fun manualSync() {
       GlobalCacheScheduler.scheduleOneShot(this)
       observeSyncProgress()
   }

   private fun observeSyncProgress() {
       WorkManager.getInstance(this)
           .getWorkInfosForUniqueWorkLiveData(GlobalCacheWorker.WORK_NAME_ONESHOT)
           .observe(this) { infos ->
               val info = infos.firstOrNull() ?: return@observe
               when (info.state) {
                   WorkInfo.State.RUNNING -> {
                       val msg = info.progress.getString(GlobalCacheWorker.KEY_PROGRESS_MSG) ?: "同步中..."
                       val idx = info.progress.getInt(GlobalCacheWorker.KEY_PROGRESS_INDEX, 0)
                       val total = info.progress.getInt(GlobalCacheWorker.KEY_PROGRESS_TOTAL, 4)
                       showSyncProgress(msg, idx, total)
                   }
                   WorkInfo.State.SUCCEEDED -> {
                       hideSyncProgress()
                       performSearch(binding.etFilter.text?.toString()?.trim() ?: "")
                       Toast.makeText(this, "同步完成", Toast.LENGTH_SHORT).show()
                   }
                   WorkInfo.State.FAILED -> {
                       hideSyncProgress()
                       Toast.makeText(this, "同步失败，请检查登录信息", Toast.LENGTH_SHORT).show()
                   }
                   else -> {}
               }
           }
   }
   ```
4. **删除 filter dialog 相关代码**（`showFilterDialog()`、`filterParams`）— 改用关键词模糊搜索更直接，或保留但只做本地内存过滤
5. **`doLoginGetError()` 整个方法删掉** — 不再现场登录

### 任务 B：进度条 UI

可以用现有的 [activity_global_schedule.xml](app/src/main/res/layout/activity_global_schedule.xml) 中的 `layout_loading` 容器，里面已经有 `tv_loading_text` 和 ProgressBar。把同步进度复用这个区域：

```kotlin
private fun showSyncProgress(msg: String, current: Int, total: Int) {
    binding.layoutLoading.visibility = View.VISIBLE
    binding.tvLoadingText.text = "$msg ($current/$total)"
}
```

或者新建一个底部 `LinearProgressIndicator` 横条，更不打断查看体验。

### 任务 C：EmptyRoomActivity 也建议改造（可选）

[EmptyRoomActivity.kt](app/src/main/java/com/njfu/schedule/ui/schedule/EmptyRoomActivity.kt) 当前也是现场拉。空教室数据维度多（学期×校区×周×星期×节次），缓存意义不大，**建议保留现状**。

### 任务 D：手动同步入口

在 [fragment_query.xml](app/src/main/res/layout/fragment_query.xml) 顶部加一个"立即同步全校课表"卡片，点击调 `GlobalCacheScheduler.scheduleOneShot(context)`，并跳转到一个简单的进度页（或对话框），观察 WorkInfo 进度。

## 数据流总览

```
[用户首次导入个人课表]
    ↓
ImportActivity.doImport() 成功
    ↓
GlobalCacheScheduler.scheduleOneShot(context)         ← 立即一次性
GlobalCacheScheduler.schedulePeriodic(context)        ← 每 30 天一次
    ↓
GlobalCacheWorker.doWork()
    ├─ NjfuImporter 登录
    ├─ for type in [jg0101, jx0601, bj0101, kc0101]:
    │     fetchGlobalSchedule(type, "", ...)
    │     dao.deleteByType(type)
    │     dao.insertAll(entities)
    │     setProgress(...)        ← UI 可观察
    └─ 写入 SharedPreferences "global_cache_last_sync"

[用户进入查询页]
    ↓
GlobalScheduleActivity 直接查 dao.search(type, keyword)
    ↓
立刻返回结果，无网络请求

[用户点手动同步]
    ↓
scheduleOneShot()
    ↓
观察 WorkInfo，显示进度条
```

## 关键约束 / 注意事项

1. **登录态依赖 SharedPreferences**：Worker 从 `njfu_login` 读 `student_id` / `password`。如果用户没在 ImportActivity 登录过一次，Worker 会 `Result.failure()`。这是设计如此，不要改。

2. **`NjfuImporter.fetchGlobalSchedule` 是耗时操作**：单类型可能要 10-30 秒，4 个类型加起来 1-2 分钟。Worker 跑在后台没问题，但手动同步时 UI 要明确告知用户耗时。

3. **数据库迁移**：新加的 `global_courses` 表通过 `MIGRATION_3_4` 创建，已经处理好。**不要**用 `fallbackToDestructiveMigration()`，会丢用户的个人课表。

4. **`GlobalCourseInfo` 与 `GlobalCourseEntity` 转换**：两者字段几乎一致，差一个 `type` 和自增 `uid`。Adapter 用的是 `GlobalCourseInfo`，所以从 DB 取出后要 map 一次。如果嫌麻烦可以让 Adapter 直接吃 Entity，改 Adapter 里的字段引用即可。

5. **首次同步可能失败**：网络/教务系统抽风、需要验证码等。Worker 已 `Result.retry()`，WorkManager 会自动重试（指数退避）。手动同步时如果 FAILED 要给用户明确提示。

6. **不要删除 `NjfuImporter.fetchGlobalSchedule`**：Worker 还在用。也不要删 `EmptyRoomActivity` 用的 `fetchEmptyRooms`。

## 文件清单（已动过的 + 待动的）

### 已修改/新建
- [app/build.gradle.kts](app/build.gradle.kts) ✅
- [app/src/main/java/com/njfu/schedule/AppDatabase.kt](app/src/main/java/com/njfu/schedule/AppDatabase.kt) ✅
- [app/src/main/java/com/njfu/schedule/bean/GlobalCourseEntity.kt](app/src/main/java/com/njfu/schedule/bean/GlobalCourseEntity.kt) ✅ 新建
- [app/src/main/java/com/njfu/schedule/dao/GlobalCourseDao.kt](app/src/main/java/com/njfu/schedule/dao/GlobalCourseDao.kt) ✅ 新建
- [app/src/main/java/com/njfu/schedule/worker/GlobalCacheWorker.kt](app/src/main/java/com/njfu/schedule/worker/GlobalCacheWorker.kt) ✅ 新建
- [app/src/main/java/com/njfu/schedule/worker/GlobalCacheScheduler.kt](app/src/main/java/com/njfu/schedule/worker/GlobalCacheScheduler.kt) ✅ 新建
- [app/src/main/java/com/njfu/schedule/ui/import_/ImportActivity.kt](app/src/main/java/com/njfu/schedule/ui/import_/ImportActivity.kt) ✅

### 待修改
- [app/src/main/java/com/njfu/schedule/ui/schedule/GlobalScheduleActivity.kt](app/src/main/java/com/njfu/schedule/ui/schedule/GlobalScheduleActivity.kt) ⬜ 重点
- [app/src/main/res/layout/fragment_query.xml](app/src/main/res/layout/fragment_query.xml) ⬜ 加同步按钮
- [app/src/main/res/layout/activity_global_schedule.xml](app/src/main/res/layout/activity_global_schedule.xml) ⬜ 顶部加同步图标按钮（可选）

## 验收标准

1. 安装新版本 → 导入个人课表 → 等约 1-2 分钟（后台跑，用户感觉不到）→ 进入查询页 → 点教师课表 → 输入"张三"回车 → **立刻**出结果
2. 杀掉 App 再开 → 查询页查询依然秒出（数据已落盘）
3. 查询页有"立即同步"按钮，点击后显示进度（如 "正在抓取教师课表... 1/4"）
4. 30 天后再打开 App，WorkManager 自动跑了一次同步（看不到 UI，但 SharedPreferences 中 `global_cache_last_sync` 时间戳更新）

## 推送

改完之后 push 到 https://github.com/keggin-CHN/NJFU-schedule.git
