import re
with open('app/src/main/java/com/njfu/schedule/njfu/NjfuImporter.kt', 'r', encoding='utf-8') as f:
    content = f.read()
with open('old_importer.kt', 'r', encoding='utf-8') as f:
    old_content = f.read()

# extract old fetchGlobalSchedule
match_old_fetch = re.search(r'(    fun fetchGlobalSchedule.*?    private fun parseNewGlobalSchedule)', old_content, re.DOTALL)
old_fetch = match_old_fetch.group(1) if match_old_fetch else None
print('Found old fetch:', bool(old_fetch))

# extract old parseNewGlobalSchedule
match_old_parse = re.search(r'(    private fun parseNewGlobalSchedule.*?    private fun parseRemarks)', old_content, re.DOTALL)
old_parse = match_old_parse.group(1) if match_old_parse else None
print('Found old parse:', bool(old_parse))

