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


print(s.get('https://jwxt.njfu.edu.cn/jsxsd/kbcx/kbxx_classroom').text[0:1000])
kb_id='933E103D1CA84D64A71CE6FC60BFE57B'
data_post = {'xnxqh': '2025-2026-2', 'kbjcmsid': kb_id, 'jx0601': '3EF0AFCDDDB74A37BA2FC8FE223E01E4', 'jx0601_text': ''}
r_post = s.post('https://jwxt.njfu.edu.cn/jsxsd/kbcx/kbxx_classroom_ifr', data=data_post)
soup = BeautifulSoup(r_post.text, 'html.parser')
table = soup.select_one('table#timetable')
print('table text:', table.text[0:100] if table else 'No table')
