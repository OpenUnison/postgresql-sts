#!/usr/bin/env python3
"""Exercise the running app with a temporary task; requires only Python 3."""
import json
import sys
import urllib.error
import urllib.request
import uuid
base = (sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8080').rstrip('/')
def request(path, method='GET', data=None):
    req = urllib.request.Request(base+path, method=method,
        data=json.dumps(data).encode() if data is not None else None,
        headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req, timeout=15) as r:
        body = r.read()
        return r.status, json.loads(body) if body else None
payload = {'title':'Smoke '+str(uuid.uuid4()),'description':'Persistence check', 'status':'TODO','dueDate':'2030-01-01'}
task_id = None
try:
    status, task = request('/api/tasks','POST',payload)
    assert status == 201 and task['title'] == payload['title']
    task_id = task['id']
    assert any(t['id']==task_id for t in request('/api/tasks')[1])
    payload['status']='DONE'
    assert request('/api/tasks/'+str(task_id),'PUT',payload)[1]['status']=='DONE'
    try:
        request('/api/tasks','POST',{**payload,'title':'   '})
        raise AssertionError('Blank title accepted')
    except urllib.error.HTTPError as e:
        assert e.code == 400
    assert request('/api/tasks/'+str(task_id),'DELETE')[0] == 204
    assert all(t['id']!=task_id for t in request('/api/tasks')[1])
    try:
        request('/api/tasks/'+str(task_id),'PUT',payload)
        raise AssertionError('Missing task update succeeded')
    except urllib.error.HTTPError as e:
        assert e.code == 404
    task_id = None
    assert request('/actuator/health/readiness')[1]['status'] == 'UP'
    print('PASS: create, list, update, validation, delete, missing task, database readiness')
finally:
    if task_id is not None:
        try: request('/api/tasks/'+str(task_id),'DELETE')
        except Exception: pass
