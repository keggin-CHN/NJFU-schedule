with open('app/src/main/java/com/njfu/schedule/njfu/NjfuImporter.kt', 'r', encoding='utf-8') as f:
    c = f.read()
c = c.replace('private fun parseNewGlobalSchedule    private fun parseNewGlobalSchedule', 'private fun parseNewGlobalSchedule')
with open('app/src/main/java/com/njfu/schedule/njfu/NjfuImporter.kt', 'w', encoding='utf-8') as f:
    f.write(c)
