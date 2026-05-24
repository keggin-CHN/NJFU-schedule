import re
with open('app/src/main/java/com/njfu/schedule/njfu/NjfuImporter.kt', 'r', encoding='utf-8') as f:
    content = f.read()
with open('old_importer.kt', 'r', encoding='utf-8') as f:
    old_content = f.read()

match_old_fetch = re.search(r'(    fun fetchGlobalSchedule.*?    private fun parseNewGlobalSchedule)', old_content, re.DOTALL)
old_fetch = match_old_fetch.group(1)

match_old_parse = re.search(r'(    private fun parseNewGlobalSchedule.*?    private fun parseRemarks)', old_content, re.DOTALL)
old_parse = match_old_parse.group(1)

fix = '''                        "kc0101" -> { 
                            val cName = entityName
                            val dataLines = if (lines.getOrNull(0) == cName) lines.drop(1) else lines
                            courseName = cName
                            className = dataLines.getOrNull(0) ?: ""
                            teacher = dataLines.getOrNull(1) ?: ""
                            weeksStr = dataLines.getOrNull(2) ?: ""
                            room = dataLines.getOrNull(3) ?: ""
                        }'''
old_parse = re.sub(r'                        "kc0101".*?\}', fix, old_parse, flags=re.DOTALL)

match_content = re.search(r'(    fun fetchGlobalSchedule.*    private fun parseRemarks)', content, re.DOTALL)
content = content.replace(match_content.group(1), old_fetch + old_parse)

with open('app/src/main/java/com/njfu/schedule/njfu/NjfuImporter.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print('patched successfully')
