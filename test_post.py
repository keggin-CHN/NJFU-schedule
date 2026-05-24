import requests
from bs4 import BeautifulSoup
import re
import execjs

s = requests.Session()
s.headers.update({'User-Agent': 'Mozilla/5.0'})
s.get('http://jwxt.njfu.edu.cn/sso.jsp')
uia_url = 'https://uia.njfu.edu.cn/authserver/login?service=http%3A%2F%2Fjwxt.njfu.edu.cn%2Fsso.jsp'
r2 = s.get(uia_url)

lt = re.search('name="lt" value="([^"]+)"', r2.text).group(1)
salt = re.search('id="pwdDefaultEncryptSalt" value="([^"]+)"', r2.text).group(1)
dllt = re.search('name="dllt" value="([^"]+)"', r2.text).group(1)

ctx = execjs.compile(open('encrypt.js', 'r', encoding='utf-8').read())
pwd = ctx.call('encryptAES', 'Zhouwenjie@790920', salt)

data = {
    'username': '2410403132',
    'password': pwd,
    'lt': lt,
    'dllt': dllt,
    'execution': 'e1s1',
    '_eventId': 'submit',
    'rmShown': '1'
}

r3 = s.post(uia_url, data=data)
print("r3 url:", r3.url)


r_home = s.get('https://jwxt.njfu.edu.cn/jsxsd/kbcx/kbxx_classroom')
kb_id = re.search('name="kbjcmsid" value="([^"]+)"', r_home.text).group(1)
data_post = {'xnxqh': '2025-2026-2', 'kbjcmsid': kb_id, 'jx0601': 'B07038B31DBA4C62B46DBAAEC03BB27C', 'jx0601_text': ''}
r_post = s.post('https://jwxt.njfu.edu.cn/jsxsd/kbcx/kbxx_classroom_ifr', data=data_post)
soup = BeautifulSoup(r_post.text, 'html.parser')
table = soup.select_one('table#timetable')
print('rows:', len(table.select('tr')) if table else 0)
print('table text:', table.text[0:100] if table else '')
