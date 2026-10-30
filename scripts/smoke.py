#!/usr/bin/env python3
"""Проверка запущенных сервисов через Gateway. Только стандартная библиотека Python."""
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
BASE = os.getenv('API_URL', 'http://127.0.0.1:8080')
CHECKS = []


def call(method, path, body=None, token=None, expected=200):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(),
                                 headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=20)
    except urllib.error.HTTPError as exc:
        response = exc
    with response:
        text = response.read().decode()
        data = json.loads(text) if text else None
        if expected is not None:
            assert response.status == expected, (method, path, response.status, text)
        return response.status, data, response.headers


def ok(label):
    CHECKS.append(label)
    print('OK:', label, flush=True)


def main():
    secrets = json.loads((ROOT / '.work/demo.json').read_text())
    for attempt in range(60):
        status, admin, _ = call('POST', '/api/users/auth',
                                {'username': os.getenv('DEMO_ADMIN_USERNAME', 'admin'),
                                 'password': secrets['DEMO_ADMIN_PASSWORD']}, expected=None)
        if status == 200:
            break
        time.sleep(1)
    else:
        raise AssertionError('Gateway не дождался регистрации сервисов')
    admin = admin['accessToken']
    _, login, _ = call('POST', '/api/users/auth', {'username': os.getenv('DEMO_USER_USERNAME', 'maxim'),
                                                  'password': secrets['DEMO_USER_PASSWORD']})
    user = login['accessToken']
    assert login['expiresIn'] == 3600
    ok('Вход, подпись JWT и срок 1 час')
    call('GET', '/api/hotels', expected=401)
    call('GET', '/api/hotels', token=user[:-8] + 'tampered', expected=401)
    call('POST', '/api/hotels', {'name': 'X', 'address': 'X'}, user, 403)
    ok('Без токена — 401; подделка — 401; чужая роль — 403')
    _, h, headers = call('POST', '/api/hotels', {'name': 'Проверка', 'address': 'Казань'}, admin, 201)
    assert headers.get('X-Request-Id')
    hid = h['id']
    call('PUT', f'/api/hotels/{hid}', {'name': 'Проверка обновлена', 'address': 'Казань'}, admin)
    _, h, _ = call('GET', f'/api/hotels/{hid}', token=user)
    assert h['name'] == 'Проверка обновлена'
    _, r, _ = call('POST', '/api/rooms', {'hotelId': hid, 'number': '1', 'available': True}, admin, 201)
    rid = r['id']
    call('PUT', f'/api/rooms/{rid}', {'number': '2', 'available': True}, admin)
    call('GET', f'/api/rooms/{rid}', token=user)
    call('DELETE', f'/api/rooms/{rid}', token=admin, expected=204)
    call('DELETE', f'/api/hotels/{hid}', token=admin, expected=204)
    ok('CRUD отелей и номеров через Gateway')
    suffix = uuid.uuid4().hex[:10]
    _, created, _ = call('POST', '/api/users', {'username': 'u' + suffix, 'password': 'test-password', 'role': 'USER'}, admin, 201)
    uid = created['id']
    call('PATCH', f'/api/users/{uid}', {'username': 'v' + suffix}, admin)
    _, account, _ = call('GET', f'/api/users/{uid}', token=admin)
    assert account['username'] == 'v' + suffix and 'password' not in account
    call('GET', '/api/users', token=admin)
    call('DELETE', f'/api/users/{uid}', token=admin, expected=204)
    _, other, _ = call('POST', '/api/users/register', {'username': 'r' + suffix, 'password': 'test-password'}, expected=201)
    other = other['accessToken']
    ok('CRUD пользователей и открытая регистрация')
    start = date.today() + timedelta(days=100)
    dates = {'startDate': str(start), 'endDate': str(start + timedelta(days=2))}
    query = urllib.parse.urlencode(dates)
    _, free, _ = call('GET', '/api/rooms?' + query, token=user)
    rid = free['content'][0]['id']
    booking = {**dates, 'requestId': str(uuid.uuid4()), 'roomId': rid, 'autoSelect': False}
    _, b, _ = call('POST', '/api/bookings', booking, user)
    assert b['status'] == 'CONFIRMED'
    _, again, _ = call('POST', '/api/bookings', booking, user)
    assert again['id'] == b['id']
    call('POST', '/api/bookings', {**booking, 'endDate': str(start + timedelta(days=3))}, user, 409)
    call('POST', '/api/bookings', {**booking, 'requestId': str(uuid.uuid4())}, user, 409)
    call('GET', '/api/bookings/' + b['id'], token=other, expected=404)
    call('DELETE', '/api/bookings/' + b['id'], token=other, expected=404)
    _, history, _ = call('GET', '/api/bookings?page=0&size=1', token=user)
    assert len(history['content']) == 1
    call('DELETE', '/api/bookings/' + b['id'], token=user)
    call('DELETE', '/api/bookings/' + b['id'], token=user)
    _, b2, _ = call('POST', '/api/bookings', {**booking, 'requestId': str(uuid.uuid4())}, user)
    assert b2['status'] == 'CONFIRMED'
    ok('Подтверждение, идемпотентность, 409, личная история, отмена и освобождение дат')
    for action in ['release', 'confirm-availability']:
        call('POST', f'/api/rooms/{rid}/' + action,
             {**dates, 'bookingId': b2['id']}, user, 403)
    call('POST', '/api/bookings', {**booking, 'requestId': str(uuid.uuid4()),
                                 'endDate': booking['startDate']}, user, 400)
    ok('Внутренние операции закрыты; неверные даты отклоняются')
    concurrent = {**booking, 'startDate': str(start + timedelta(days=10)),
                  'endDate': str(start + timedelta(days=12))}
    with ThreadPoolExecutor(max_workers=8) as pool:
        responses = list(pool.map(lambda _: call('POST', '/api/bookings',
                              {**concurrent, 'requestId': str(uuid.uuid4())}, user, expected=None), range(8)))
    statuses = [r[0] for r in responses]
    assert statuses.count(200) == 1 and statuses.count(409) == 7, statuses
    ok('8 одновременных заявок на один номер: 1 бронь и 7 отказов')
    repeat = {**booking, 'requestId': str(uuid.uuid4()), 'startDate': str(start + timedelta(days=20)),
              'endDate': str(start + timedelta(days=22))}
    with ThreadPoolExecutor(max_workers=8) as pool:
        responses = list(pool.map(lambda _: call('POST', '/api/bookings', repeat, user, expected=None), range(8)))
    assert all(r[0] in (200, 202) for r in responses), responses
    assert len({r[1]['id'] for r in responses}) == 1
    ok('8 повторов одного запроса: один bookingId')
    _, h, _ = call('POST', '/api/hotels', {'name': 'Равномерная загрузка', 'address': 'Тест'}, admin, 201)
    for number in range(3):
        call('POST', '/api/rooms', {'hotelId': h['id'], 'number': str(number), 'available': True}, admin, 201)
    assigned = []
    for n in range(6):
        d = start + timedelta(days=30 + n * 3)
        _, auto, _ = call('POST', '/api/bookings', {'requestId': str(uuid.uuid4()), 'autoSelect': True,
                          'roomId': 999999, 'hotelId': h['id'], 'startDate': str(d), 'endDate': str(d + timedelta(days=2))}, user)
        assigned.append(auto['roomId'])
    assert len(set(assigned)) == 3 and all(assigned.count(r) == 2 for r in assigned), assigned
    _, recommended, _ = call('GET', '/api/bookings/recommendations?' + query, token=user)
    assert recommended == sorted(recommended, key=lambda r: (r['timesBooked'], r['id']))
    call('GET', '/api/rooms/statistics?' + query, token=admin)
    ok('Автоподбор игнорирует roomId, распределяет 6 броней поровну; статистика доступна')
    for port in [os.getenv('HOTEL_PORT', '8081'), os.getenv('BOOKING_PORT', '8082')]:
        with urllib.request.urlopen(f'http://127.0.0.1:{port}/v3/api-docs', timeout=10) as response:
            spec = json.load(response)
            assert spec['paths'] and spec['components']['securitySchemes']['bearer']
    req = urllib.request.Request(os.getenv('EUREKA_URL', 'http://127.0.0.1:8761/eureka/') + 'apps', headers={'Accept': 'application/json'})
    with urllib.request.urlopen(req, timeout=10) as response:
        names = {a['name'] for a in json.load(response)['applications']['application']}
        assert {'HOTEL-SERVICE', 'BOOKING-SERVICE', 'GATEWAY'} <= names, names
    ok('OpenAPI обоих API и регистрация трёх приложений в Eureka')
    (ROOT / '.work/smoke-results.json').write_text(json.dumps({'passed': len(CHECKS), 'checks': CHECKS}, ensure_ascii=False, indent=2))
    print(f'Готово: {len(CHECKS)} групп проверок пройдено.')


if __name__ == '__main__':
    main()
