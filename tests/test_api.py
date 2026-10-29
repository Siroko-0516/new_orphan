import base64
import io
import json

import httpx
import pytest
from fastapi.testclient import TestClient
from PIL import Image

import app as server

client = TestClient(server.app)


@pytest.fixture(autouse=True)
def clear_credentials(monkeypatch):
    for name in ('GEMINI_API_KEY', 'OPENAI_API_KEY', 'ELEVENLABS_API_KEY', 'ELEVENLABS_VOICE_ID'):
        monkeypatch.delenv(name, raising=False)
    monkeypatch.setenv('AI_PROVIDER', 'gemini')
    monkeypatch.delenv('GEMINI_MODEL', raising=False)


def frame():
    data = io.BytesIO()
    Image.new('RGB', (64, 64)).save(data, format='JPEG')
    return {'image': 'data:image/jpeg;base64,' + base64.b64encode(data.getvalue()).decode()}


def mock_remote(monkeypatch, handler):
    original = httpx.AsyncClient
    monkeypatch.setattr(server.httpx, 'AsyncClient', lambda **kwargs: original(transport=httpx.MockTransport(handler)))


def test_page_and_configuration():
    assert client.get('/').status_code == 200
    assert '길잡이' in client.get('/').text
    assert client.get('/api/status').json()['ai_configured'] is False


def test_reject_invalid_frame():
    assert client.post('/api/detect', json={'image': 'not an image'}).status_code == 400


def test_detection_returns_model_result(monkeypatch):
    boxes = [{'label': 'person', 'confidence': .9, 'box': [.1, .1, .8, .8]}]
    monkeypatch.setattr(server, 'detect', lambda image: boxes)
    assert client.post('/api/detect', json=frame()).json() == {'detections': boxes}


def test_missing_model_is_actionable(monkeypatch):
    monkeypatch.setenv('YOLO_MODEL', '/tmp/nonexistent-camera-safety-model.pt')
    assert client.post('/api/detect', json=frame()).status_code == 503


def test_no_keys_preserves_detection_only_mode():
    assert client.post('/api/assess', json=frame()).status_code == 503
    assert client.post('/api/speech', json={'text': '주의하세요'}).status_code == 503


@pytest.mark.parametrize('provider', ['gemini', 'openai'])
def test_vision_includes_image_and_yolo_and_validates_result(monkeypatch, provider):
    monkeypatch.setenv('AI_PROVIDER', provider)
    monkeypatch.setenv('GEMINI_API_KEY' if provider == 'gemini' else 'OPENAI_API_KEY', 'test-only')
    verdict = {'level': 'medium', 'message': '앞쪽에 사람이 있습니다. 주변을 살펴주세요.'}

    def remote(request):
        payload = json.loads(request.content)
        assert 'person' in request.content.decode()
        if provider == 'gemini':
            assert request.url.path.endswith('/gemini-3.1-flash-lite:generateContent')
            assert payload['contents'][0]['parts'][1]['inline_data']['data']
            return httpx.Response(200, json={'candidates': [{'content': {'parts': [{'text': json.dumps(verdict)}]}}]})
        assert payload['messages'][1]['content'][0]['image_url']['url'].startswith('data:image/jpeg')
        return httpx.Response(200, json={'choices': [{'message': {'content': json.dumps(verdict)}}]})

    mock_remote(monkeypatch, remote)
    body = frame() | {'detections': [{'label': 'person', 'confidence': .9, 'box': [0, 0, 1, 1]}]}
    assert client.post('/api/assess', json=body).json() == verdict


def test_invalid_ai_output_is_not_shown_as_safe(monkeypatch):
    monkeypatch.setenv('GEMINI_API_KEY', 'test-only')
    mock_remote(monkeypatch, lambda request: httpx.Response(200, json={
        'candidates': [{'content': {'parts': [{'text': '{"level":"safe","message":"괜찮아요"}'}]}}]}))
    assert client.post('/api/assess', json=frame()).status_code == 502


@pytest.mark.parametrize('status,word', [(401, '인증'), (403, '권한'), (404, '모델'), (429, '한도'), (503, '혼잡')])
def test_ai_errors_are_actionable_without_exposing_remote_details(monkeypatch, status, word):
    monkeypatch.setenv('GEMINI_API_KEY', 'test-only-secret')
    mock_remote(monkeypatch, lambda request: httpx.Response(status, json={'error': {'message': 'test-only-secret'}}))
    response = client.post('/api/assess', json=frame())
    assert response.status_code == 502
    assert word in response.json()['detail']
    assert 'test-only-secret' not in response.text


def test_gemini_ignores_thought_parts(monkeypatch):
    monkeypatch.setenv('GEMINI_API_KEY', 'test-only')
    verdict = {'level': 'unknown', 'message': '판단할 수 없습니다.'}
    mock_remote(monkeypatch, lambda request: httpx.Response(200, json={'candidates': [{'content': {'parts': [
        {'thought': True, 'text': 'internal reasoning'}, {'text': json.dumps(verdict)}]}}]}))
    assert client.post('/api/assess', json=frame()).json() == verdict


def test_ai_timeout_has_specific_message(monkeypatch):
    monkeypatch.setenv('GEMINI_API_KEY', 'test-only')
    def timeout(request):
        raise httpx.ReadTimeout('upstream timeout', request=request)
    mock_remote(monkeypatch, timeout)
    response = client.post('/api/assess', json=frame())
    assert response.status_code == 504
    assert '시간' in response.json()['detail']


def test_elevenlabs_failure_signals_browser_fallback(monkeypatch):
    monkeypatch.setenv('ELEVENLABS_API_KEY', 'test-only')
    monkeypatch.setenv('ELEVENLABS_VOICE_ID', 'test-voice')
    mock_remote(monkeypatch, lambda request: httpx.Response(401))
    assert client.post('/api/speech', json={'text': '주의하세요'}).status_code == 502


def test_elevenlabs_audio_response(monkeypatch):
    monkeypatch.setenv('ELEVENLABS_API_KEY', 'test-only')
    monkeypatch.setenv('ELEVENLABS_VOICE_ID', 'test-voice')
    mock_remote(monkeypatch, lambda request: httpx.Response(200, content=b'test-audio'))
    response = client.post('/api/speech', json={'text': '주의하세요'})
    assert response.headers['content-type'] == 'audio/mpeg'
    assert response.content == b'test-audio'
