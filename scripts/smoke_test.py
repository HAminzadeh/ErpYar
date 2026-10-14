#!/usr/bin/env python3
"""Minimal end-to-end local environment verification. Creates demo records."""
import json
import os
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from uuid import uuid4

ENV = {}
for line in (Path(__file__).resolve().parents[1] / '.env').read_text(encoding='utf-8').splitlines():
    if '=' in line and not line.startswith('#'):
        k, v = line.split('=', 1)
        ENV[k] = v
base = 'http://localhost:' + ENV.get('GATEWAY_PORT', '8080')
token = None

def call(method, route, body=None, expected=(200, 201)):
    payload = json.dumps(body).encode() if body is not None else None
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    try:
        with urlopen(Request(base + route, data=payload, headers=headers, method=method), timeout=20) as resp:
            result = json.loads(resp.read() or b'{}')
            assert resp.status in expected, (route, resp.status)
            return result
    except HTTPError as e:
        if e.code in expected:
            return {'status': e.code}
        raise RuntimeError(f'{route}: HTTP {e.code} — {e.read()[:400]!r}') from e

login = call('POST', '/api/auth/login', {
    'username': ENV.get('BOOTSTRAP_ADMIN_USERNAME', 'admin'),
    'password': ENV['BOOTSTRAP_ADMIN_PASSWORD']
})
token = login['accessToken']
print('PASS: admin login')
h = call('POST', '/api/holdings', {'code': 'HT-' + uuid4().hex[:8], 'name': 'Smoke Holding'})
c = call('POST', '/api/companies', {
    'holdingId': h['id'], 'code': 'CO-' + uuid4().hex[:8], 'name': 'Smoke Company', 'businessType': 'SERVICE'
})
print('PASS: holding + company')
d = call('POST', '/api/departments', {'companyId': c['id'], 'code': 'OPS', 'name': 'Operations'})
cat = call('POST', '/api/master-data/categories', {'companyId': c['id'], 'code': 'A', 'name': 'Category A'})
print('PASS: org + master data')
request = call('POST', '/api/workflows/requests', {
    'companyId': c['id'], 'requestType': 'PURCHASE', 'description': 'Local test approval'
})
assert request['status'] == 'PENDING'
call('POST', '/api/workflows/requests/' + request['id'] + '/approve', expected=(403,))
print('PASS: workflow self-approval prevented')
events = call('GET', '/api/audit/events?companyId=' + c['id'])
assert isinstance(events, list)
print('PASS: audit API responds; event count:', len(events))
staff_username = 's-' + uuid4().hex[:10]
user = call('POST', '/api/users', {'username': staff_username, 'password': 'localDemoPassword123!', 'displayName': 'Smoke Staff'})
call('PUT', f"/api/users/{user['id']}/access", {'roles': ['STAFF'], 'companyIds': [c['id']], 'holdingIds': []})
print('PASS: identity + grants')
other = call('POST', '/api/companies', {'holdingId': h['id'], 'code': 'CO-' + uuid4().hex[:8], 'name': 'Other Company', 'businessType': 'TRADE'})
token = call('POST', '/api/auth/login', {'username': staff_username, 'password': 'localDemoPassword123!'})['accessToken']
assert isinstance(call('GET', '/api/master-data/categories?companyId=' + c['id']), list)
assert call('GET', '/api/master-data/categories?companyId=' + other['id'], expected=(403,))['status'] == 403
print('PASS: staff cannot read other company data')
print('All basic smoke checks passed. Demo rows remain in database.')
