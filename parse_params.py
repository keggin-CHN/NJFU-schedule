import bs4
import json
import glob

results = {}
for html_file in glob.glob("*.html"):
    if not html_file.startswith("kbxx_"):
        continue
    with open(html_file, 'r', encoding='utf-8') as f:
        soup = bs4.BeautifulSoup(f, 'html.parser')
    
    # find all selects
    params = {}
    for select in soup.find_all('select'):
        name = select.get('name')
        if not name: continue
        options = []
        for opt in select.find_all('option'):
            options.append({"value": opt.get('value', ''), "text": opt.text.strip()})
        params[name] = {"type": "select", "options": options}
        
    for inp in soup.find_all('input'):
        name = inp.get('name')
        if not name: continue
        params[name] = {"type": inp.get('type', 'text'), "value": inp.get('value', '')}
        
    results[html_file] = params

print(json.dumps(results, indent=2, ensure_ascii=False))
