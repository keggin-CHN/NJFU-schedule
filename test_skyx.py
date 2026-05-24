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

r4 = s.get('https://jwxt.njfu.edu.cn/jsxsd/kbcx/kbxx_kc')
print("kbxx_kc len:", len(r4.text))
soup = BeautifulSoup(r4.text, 'html.parser')
options = soup.select('select[name=skyx] option')
print("kbxx_kc skyx options:", len(options))

r5 = s.get('https://jwxt.njfu.edu.cn/jsxsd/kbcx/kbxx_xzb')
print("kbxx_xzb len:", len(r5.text))
options2 = BeautifulSoup(r5.text, 'html.parser').select('select[name=skyx] option')
print("kbxx_xzb skyx options:", len(options2))

r6 = s.post('https://jwxt.njfu.edu.cn/jsxsd/kscj/cjcx_list', data={'kksj': '', 'kcxz': '', 'kcsx': '', 'kcmc': '', 'xsfs': 'all'})
print('cjcx_list len:', len(r6.text))

r7 = s.post('https://jwxt.njfu.edu.cn/jsxsd/kbcx/kbxx_kc_ifr', data={'xnxqh': '2023-2024-2', 'kbjcmsid': '933E103D1CA84D64A71CE6FC60BFE57B', 'skyx': '01'})
print('kbxx_kc_ifr len:', len(r7.text))
print('timetable in kbxx_kc_ifr:', 'timetable' in r7.text)

print(r7.text[0:2000])

from bs4 import BeautifulSoup
soup = BeautifulSoup(r7.text, 'html.parser')
print('kc table:', soup.select_one('table#timetable td div.kbcontent').text)
print('kc div html:', soup.select_one('table#timetable td div.kbcontent').decode_contents())

r8 = session.get('https://jwxt.njfu.edu.cn/jsxsd/kbcx/kbxx_xqsj_ifr')
soup8 = BeautifulSoup(r8.text, 'html.parser')
print('jx0601 skyx options:', len(soup8.select('select[name=skyx] option')))
print('jx0601 xq options:', len(soup8.select('select[name=xq] option')))
print('jx0601 jxl options:', len(soup8.select('select[name=jxl] option')))
