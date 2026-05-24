import requests
from bs4 import BeautifulSoup
import urllib3
import re
import base64
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad
import json

urllib3.disable_warnings()
session = requests.Session()
session.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
})

def encrypt_password(password, key):
    key_bytes = key.encode('utf-8')
    iv = re.sub(r'(.{8}).*', r'\1', key).encode('utf-8') + b'00000000'
    cipher = AES.new(key_bytes, AES.MODE_CBC, iv)
    padded_password = pad(password.encode('utf-8'), AES.block_size)
    encrypted = cipher.encrypt(padded_password)
    return base64.b64encode(encrypted).decode('utf-8')

# Login
r = session.get('https://uia.njfu.edu.cn/authserver/login?service=http%3A%2F%2Fjwxt.njfu.edu.cn%2Fsso.jsp', verify=False)
soup = BeautifulSoup(r.text, 'html.parser')
pwdDefaultEncryptSalt = soup.select_one('#pwdDefaultEncryptSalt')['value']
lt = soup.select_one('input[name=lt]')['value']
execution = soup.select_one('input[name=execution]')['value']
eventId = soup.select_one('input[name=_eventId]')['value']

data = {
    'username': '2410403132',
    'password': encrypt_password('Zhouwenjie@790920', pwdDefaultEncryptSalt),
    'lt': lt,
    'execution': execution,
    '_eventId': eventId,
    'rmShown': '1'
}

r2 = session.post('https://uia.njfu.edu.cn/authserver/login?service=http%3A%2F%2Fjwxt.njfu.edu.cn%2Fsso.jsp', data=data, verify=False)

r3 = session.get('https://jwxt.njfu.edu.cn/jsxsd/framework/xsrkxz.htmlx', verify=False)

# Get kbxx_xqsj_ifr
r4 = session.get('https://jwxt.njfu.edu.cn/jsxsd/kbcx/kbxx_xqsj_ifr', verify=False)
soup4 = BeautifulSoup(r4.text, 'html.parser')

print("kbxx_xqsj_ifr skyx options:", len(soup4.select('select[name=skyx] option')))
print("kbxx_xqsj_ifr xq options:", len(soup4.select('select[name=xq] option')))
print("kbxx_xqsj_ifr jxl options:", len(soup4.select('select[name=jxl] option')))

# Let's see if we post with skyx, does it return data?
# Or do we need to post with xq and jxl?
kbjcmsidMatch = re.search(r'id="kbjcmsid"\s+value="([^"]+)"', r4.text)
kbjcmsid = kbjcmsidMatch.group(1) if kbjcmsidMatch else ""

# Try querying skyx
post_data = {
    'xnxqh': '2025-2026-2',
    'kbjcmsid': kbjcmsid,
    'skyx': '12' # 外国语学院
}
r5 = session.post('https://jwxt.njfu.edu.cn/jsxsd/kbcx/kbxx_xqsj_ifr', data=post_data, verify=False)
soup5 = BeautifulSoup(r5.text, 'html.parser')
table5 = soup5.select_one('table#timetable')
print("Query by skyx '12' table rows:", len(table5.select('tr')) if table5 else 0)

# Try querying jxl
jxl_options = soup4.select('select[name=jxl] option')
if len(jxl_options) > 1:
    jxl_val = jxl_options[1]['value']
    post_data_jxl = {
        'xnxqh': '2025-2026-2',
        'kbjcmsid': kbjcmsid,
        'jxl': jxl_val
    }
    r6 = session.post('https://jwxt.njfu.edu.cn/jsxsd/kbcx/kbxx_xqsj_ifr', data=post_data_jxl, verify=False)
    soup6 = BeautifulSoup(r6.text, 'html.parser')
    table6 = soup6.select_one('table#timetable')
    print("Query by jxl", jxl_val, "table rows:", len(table6.select('tr')) if table6 else 0)


print(r4.text[0:1000])
