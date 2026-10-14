#!/usr/bin/env python3
"""Create local-only secrets; never replace an existing .env."""
import os, secrets, pathlib
root=pathlib.Path(__file__).resolve().parents[1]
p=root/'.env'
if p.exists():
    print('.env already exists; not modified')
else:
    content='\n'.join([
        'JWT_SECRET='+secrets.token_urlsafe(48),
        'DB_PASSWORD='+secrets.token_urlsafe(24),
        'BOOTSTRAP_ADMIN_USERNAME=admin',
        'BOOTSTRAP_ADMIN_PASSWORD='+secrets.token_urlsafe(20),
        'GATEWAY_PORT=8080',
        '',
    ])
    p.write_text(content,encoding='utf-8')
    if os.name=='posix': p.chmod(0o600)
    print('Created .env. View BOOTSTRAP_ADMIN_PASSWORD locally, keep it private.')
