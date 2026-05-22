# 南林课程表 (NJFU Schedule)

南京林业大学专属课程表 Android 应用，基于 [WakeupSchedule_Kotlin](https://github.com/YZune/WakeupSchedule_Kotlin) 的数据模型重新构建。

## 功能

- **一键导入**：输入学号和统一认证密码，自动登录教务系统获取本学期课表
- **课表显示**：清晰的周视图课表展示
- **本地存储**：使用 Room 数据库本地保存，无需重复登录

## 技术栈

- Kotlin + Coroutines
- Android Jetpack (Room, ViewModel, ViewBinding)
- OkHttp (网络请求)
- Jsoup (HTML 解析)
- Material Design 3

## 构建

1. 使用 Android Studio 打开项目
2. Sync Gradle
3. Run on device/emulator

## 导入原理

1. 通过南林统一认证系统 (uia.njfu.edu.cn) 进行 CAS 登录
2. 密码使用 AES-CBC 加密（与学校系统一致）
3. 登录成功后访问教务系统课表页面
4. 解析 HTML 表格提取课程信息（课程名、教师、教室、周次、节次）
5. 将不连续周次拆分为多条记录存入数据库

## 数据模型

| 表 | 说明 |
|---|---|
| TableBean | 课表元信息（名称、开始日期、最大周数等） |
| CourseBaseBean | 课程基本信息（名称、颜色） |
| CourseDetailBean | 课程时间详情（星期、节次、周次范围、教室、教师） |

## License

MIT
