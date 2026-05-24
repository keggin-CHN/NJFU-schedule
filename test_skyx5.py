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


r4 = s.get('https://jwxt.njfu.edu.cn/jsxsd/kbcx/kbxx_classroom_ifr')
soup4 = BeautifulSoup(r4.text, 'html.parser')
print('kbxx_classroom_ifr skyx options:', len(soup4.select('select[name=skyx] option')))
print('kbxx_classroom_ifr xq options:', len(soup4.select('select[name=xq] option')))
print('kbxx_classroom_ifr jxl options:', len(soup4.select('select[name=jxl] option')))
print(r4.text[0:1000])
