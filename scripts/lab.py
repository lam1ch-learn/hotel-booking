#!/usr/bin/env python3
"""Запуск и остановка только процессов этого проекта."""
import argparse
import json
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / '.work'
SERVICES = {'discovery-server': ('DISCOVERY_PORT', 8761), 'hotel-service': ('HOTEL_PORT', 8081),
            'booking-service': ('BOOKING_PORT', 8082), 'gateway': ('GATEWAY_PORT', 8080)}
STATE = WORK / 'processes.json'


def configure():
    WORK.mkdir(exist_ok=True)
    keys = WORK / 'keys'
    keys.mkdir(exist_ok=True)
    if not (keys / 'private.pem').exists():
        subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048',
                        '-out', str(keys / 'private.pem')], check=True, stderr=subprocess.DEVNULL)
        (keys / 'private.pem').chmod(0o600)
        subprocess.run(['openssl', 'pkey', '-in', str(keys / 'private.pem'), '-pubout',
                        '-out', str(keys / 'public.pem')], check=True)
    settings = WORK / 'demo.json'
    if not settings.exists():
        settings.write_text(json.dumps({'DEMO_ADMIN_PASSWORD': secrets.token_urlsafe(18),
                                       'DEMO_USER_PASSWORD': secrets.token_urlsafe(18)}, indent=2))
        settings.chmod(0o600)
    env = {**os.environ, **json.loads(settings.read_text())}
    env.update(DEBUG='false', TRACE='false', JWT_PRIVATE_KEY=(keys / 'private.pem').as_uri(),
               JWT_PUBLIC_KEY=(keys / 'public.pem').as_uri(), SPRING_PROFILES_ACTIVE='demo')
    env.setdefault('EUREKA_URL', f"http://localhost:{env.get('DISCOVERY_PORT',8761)}/eureka/")
    return env


def identity(pid):
    try:
        return Path(f'/proc/{pid}/stat').read_text().split()[21]
    except FileNotFoundError:
        return None


def owned(record):
    if identity(record['pid']) != record['start']:
        return False
    return str(ROOT).encode() in Path(f"/proc/{record['pid']}/cmdline").read_bytes()


def stop():
    if not STATE.exists():
        return
    records = json.loads(STATE.read_text())
    for record in reversed(records):
        if owned(record):
            os.kill(record['pid'], signal.SIGTERM)
    deadline = time.monotonic() + 20
    while any(owned(r) for r in records) and time.monotonic() < deadline:
        time.sleep(.2)
    if any(owned(r) for r in records):
        raise RuntimeError('Процессы ещё останавливаются; повторите stop')
    STATE.unlink()
    print('Сервисы остановлены.')


def start():
    if STATE.exists() and any(owned(r) for r in json.loads(STATE.read_text())):
        raise RuntimeError('Стенд уже запущен; сначала выполните stop')
    env = configure()
    ports = {name: int(env.get(key, default)) for name, (key, default) in SERVICES.items()}
    if len(set(ports.values())) != len(ports):
        raise RuntimeError('Порты сервисов должны различаться')
    for port in ports.values():
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', port))
    jars = {name: ROOT / name / 'target' / f'{name}-1.0.0.jar' for name in SERVICES}
    if not all(p.exists() for p in jars.values()):
        raise RuntimeError('Сначала выполните ./scripts/check.sh')
    records = []
    try:
        for name, jar in jars.items():
            with (WORK / f'{name}.log').open('w') as log:
                proc = subprocess.Popen(['java', '-Xms64m', '-Xmx384m', '-jar', str(jar)],
                                        cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT,
                                        start_new_session=True)
            records.append({'name': name, 'pid': proc.pid, 'start': identity(proc.pid)})
            STATE.write_text(json.dumps(records, indent=2))
            deadline = time.monotonic() + 120
            while time.monotonic() < deadline:
                if proc.poll() is not None:
                    raise RuntimeError(f'{name} завершился. Лог: .work/{name}.log')
                try:
                    with urllib.request.urlopen(f'http://127.0.0.1:{ports[name]}/actuator/health', timeout=2) as res:
                        if res.status == 200:
                            break
                except (OSError, urllib.error.URLError):
                    time.sleep(1)
            else:
                raise RuntimeError(f'{name} не запустился за 120 секунд')
            print(f'{name}: OK, порт {ports[name]}', flush=True)
        print('Учётные данные: .work/demo.json. Подождите несколько секунд регистрации в Eureka.')
    except BaseException:
        stop()
        raise


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['start', 'stop', 'status'])
    args = parser.parse_args()
    if args.action == 'start':
        start()
    elif args.action == 'stop':
        stop()
    else:
        records = json.loads(STATE.read_text()) if STATE.exists() else []
        print('\n'.join(f"{r['name']}: {'running' if owned(r) else 'stopped'}" for r in records) or 'Стенд выключен.')


if __name__ == '__main__':
    main()
