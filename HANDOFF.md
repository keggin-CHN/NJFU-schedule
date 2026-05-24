# NJFU Schedule Handoff

## Current State

The project now has two parts:
- a full Python crawler that can log in and dump the school timetable data
- an Android app that still needs the data model and UI fully upgraded

The crawler is working and was tested on the live NJFU system.

## What the crawler already captures

The crawler stores much more than the app currently uses:
- raw HTML pages
- form fields and select options
- full table/cell level HTML
- parsed course records
- SQLite database
- JSON, JSONL, CSV exports

Live test result:
- teacher schedule: 10916 rows
- classroom schedule: 8767 rows
- class schedule: 26203 rows
- course schedule: 8770 rows
- total parsed records: 54656

Test output folder:
- `data/njfu_crawl_test/20260524_230044/`

Main crawler file:
- [njfu_full_crawler.py](./njfu_full_crawler.py)

## What the app still lacks

The Android app currently stores only a small subset of the crawler data. Missing pieces include:
- source type metadata
- term
- entity name
- table / row / column position
- section slot index
- raw text
- raw HTML
- richer search fields
- original record viewer

## Work already started

These files were already updated or are in progress:
- [GlobalCourseInfo.kt](./app/src/main/java/com/njfu/schedule/bean/GlobalCourseInfo.kt)
- [GlobalCourseEntity.kt](./app/src/main/java/com/njfu/schedule/bean/GlobalCourseEntity.kt)
- [AppDatabase.kt](./app/src/main/java/com/njfu/schedule/AppDatabase.kt)
- [GlobalCourseDao.kt](./app/src/main/java/com/njfu/schedule/dao/GlobalCourseDao.kt)
- [NjfuImporter.kt](./app/src/main/java/com/njfu/schedule/njfu/NjfuImporter.kt)
- [GlobalCacheWorker.kt](./app/src/main/java/com/njfu/schedule/worker/GlobalCacheWorker.kt)
- [EntityScheduleActivity.kt](./app/src/main/java/com/njfu/schedule/ui/schedule/EntityScheduleActivity.kt)
- [dialog_global_course_detail.xml](./app/src/main/res/layout/dialog_global_course_detail.xml)

## Remaining tasks

### Must finish
- finish wiring new fields through `GlobalCacheWorker`
- make `EntityScheduleActivity` show the full record details
- make `GlobalScheduleActivity` search and filters wider
- make sure `GlobalCourseEntity` migration works with existing data
- propagate the new `GlobalCourseInfo` fields through all app call sites

### Should finish
- add a raw record viewer page
- add export for current filtered results
- add statistics by college / campus / term / entity type
- add a duplicate / anomaly record diagnostics page
- clean up the broken/garbled Chinese UI strings

### Nice to have
- import and analyze crawler-generated JSONL / SQLite directly
- add field dictionary / schema explanation pages
- add cross-term diff comparison for the same entity

## Notes for the next person

- Do not write credentials into the repo.
- The repo contains some old garbled text from prior work.
- I did not run a build in this pass.
- There are temporary files in the workspace:
  - `data/`
  - `__pycache__/`
  - `.pycache/`

