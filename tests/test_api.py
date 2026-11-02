import base64
import io
import json
import gzip

import httpx
import pytest
from fastapi.testclient import TestClient
from PIL import Image

import app as server

client = TestClient(server.app)
MP3 = b'\xff\xfb\x90\x64test-audio'


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
    mock_remote(monkeypatch, lambda request: httpx.Response(200, content=MP3, headers={'content-type': 'audio/mpeg'}))
    response = client.post('/api/speech', json={'text': '주의하세요'})
    assert response.headers['content-type'] == 'audio/mpeg'
    assert response.content == MP3


def walking(**overrides):
    return {'mode': 'walking', 'provider': 'gemini', 'images': [frame()['image']], **overrides}


def gemini_reply(value):
    return httpx.Response(200, json={'candidates': [{'content': {'parts': [{'text': json.dumps(value)}]}}]})


@pytest.mark.parametrize('provider', ['gemini', 'openai'])
def test_walking_uses_ordered_images_personal_key_and_strict_response(monkeypatch, provider):
    monkeypatch.setenv('GEMINI_API_KEY', 'server-key-must-not-be-used')
    monkeypatch.setenv('OPENAI_API_KEY', 'server-key-must-not-be-used')
    verdict = {'speak': True, 'message': '앞쪽에 사람이 있어요. 잠시 멈춰 확인하세요.', 'urgency': 'notice'}

    def remote(request):
        payload = json.loads(request.content)
        assert '이전 안내' in request.content.decode()
        if provider == 'gemini':
            assert request.headers['x-goog-api-key'] == 'personal-key-only'
            assert payload['generationConfig']['thinkingConfig'] == {'thinkingLevel': 'MINIMAL'}
            assert payload['generationConfig']['responseSchema']['required'] == ['speak', 'message', 'urgency']
            parts = payload['contents'][0]['parts']
            assert sum('inline_data' in part for part in parts) == 3
            assert '1/3' in parts[0]['text'] and '3/3' in parts[4]['text']
            return gemini_reply(verdict)
        assert request.headers['Authorization'] == 'Bearer personal-key-only'
        assert sum(part['type'] == 'image_url' for part in payload['messages'][1]['content']) == 3
        return httpx.Response(200, json={'choices': [{'message': {'content': json.dumps(verdict)}}]})

    mock_remote(monkeypatch, remote)
    response = client.post('/api/assess', json=walking(provider=provider, apiKey=' "personal-\u200bkey-only" ',
        images=[frame()['image']] * 3, previousSpoken='이전 안내', context='걷는 중입니다.'))
    assert response.status_code == 200
    assert response.json() == verdict
    assert 'server-key' not in response.text and 'personal-key' not in response.text


def test_old_automatic_frames_are_silent_without_credentials():
    response = client.post('/api/assess', json=walking(frameAgeMs=5001))
    assert response.status_code == 200
    assert response.json() == {'speak': False, 'message': '', 'urgency': 'none'}


def test_manual_photo_is_explained_even_when_old(monkeypatch):
    verdict = {'speak': True, 'message': '책상 위에 컵이 놓여 있어요.', 'urgency': 'notice'}
    mock_remote(monkeypatch, lambda request: gemini_reply(verdict))
    response = client.post('/api/assess', json=walking(manual=True, apiKey='personal-test-key', frameAgeMs=10000, question='무엇이 보이나요?'))
    assert response.status_code == 200
    assert response.json() == verdict


@pytest.mark.parametrize('changes', [
    {'images': []}, {'images': [frame()['image']] * 4}, {'frameAgeMs': -1}, {'frameAgeMs': 600001},
    {'frameAgeMs': '100'}, {'manual': 'yes'}, {'context': 'x' * 6001}, {'question': 'x' * 12001},
    {'previousSpoken': 'x' * 301}])
def test_walking_rejects_invalid_request_shapes(changes):
    assert client.post('/api/assess', json=walking(**changes)).status_code == 422


def test_walking_rejects_remote_image_urls_and_total_image_budget():
    assert client.post('/api/assess', json=walking(images=['http://192.168.1.2/camera.jpg'])).status_code == 400
    jpeg = base64.b64decode(frame()['image'].split(',')[1])
    padded = jpeg + b'\0' * (server.MAX_IMAGE_BYTES // 3 + 1 - len(jpeg))
    image = 'data:image/jpeg;base64,' + base64.b64encode(padded).decode()
    assert client.post('/api/assess', json=walking(images=[image] * 3)).status_code == 400


def test_walking_rejects_model_urls_and_bad_keys_without_exposing_them():
    response = client.post('/api/assess', json=walking(apiKey='personal-secret-test', model='https://other.example/model'))
    assert response.status_code == 400
    response = client.post('/api/assess', json=walking(apiKey='bad-secret-키'))
    assert response.status_code == 400
    assert 'bad-secret' not in response.text


@pytest.mark.parametrize('value', [
    {'speak': 'true', 'message': '앞을 확인하세요.', 'urgency': 'notice'},
    {'speak': False, 'message': '앞을 확인하세요.', 'urgency': 'none'},
    {'speak': True, 'message': '', 'urgency': 'urgent'},
    {'speak': True, 'message': '앞을 확인하세요.', 'urgency': 'none'},
    {'speak': False, 'message': '', 'urgency': 'urgent'},
    {'speak': False, 'message': '', 'urgency': 'none', 'extra': 'not allowed'},
])
def test_walking_rejects_non_strict_or_inconsistent_ai_output(monkeypatch, value):
    monkeypatch.setenv('GEMINI_API_KEY', 'test-only')
    mock_remote(monkeypatch, lambda request: gemini_reply(value))
    assert client.post('/api/assess', json=walking()).status_code == 502


def test_walking_rejects_duplicate_json_keys(monkeypatch):
    monkeypatch.setenv('GEMINI_API_KEY', 'test-only')
    mock_remote(monkeypatch, lambda request: httpx.Response(200, json={'candidates': [{'content': {'parts': [
        {'text': '{"speak":true,"speak":false,"message":"","urgency":"none"}'}]}}]}))
    assert client.post('/api/assess', json=walking()).status_code == 502


@pytest.mark.parametrize('voice,identifier', list(server.VOICES.items()))
def test_speech_uses_personal_key_and_fixed_selected_voice(monkeypatch, voice, identifier):
    monkeypatch.setenv('ELEVENLABS_API_KEY', 'server-secret')
    monkeypatch.setenv('ELEVENLABS_VOICE_ID', 'unwanted-server-voice')
    def remote(request):
        assert request.headers['xi-api-key'] == 'personal-eleven-key'
        assert request.url.path.endswith('/' + identifier)
        assert json.loads(request.content)['model_id'] == 'eleven_multilingual_v2'
        return httpx.Response(200, content=MP3, headers={'content-type': 'audio/mpeg'})
    mock_remote(monkeypatch, remote)
    response = client.post('/api/speech', json={'text': '앞을 확인하세요.', 'apiKey': 'personal-eleven-key', 'voice': voice})
    assert response.status_code == 200 and response.content == MP3


def test_same_origin_and_request_size_limits():
    assert client.post('/api/assess', json=walking(), headers={'Origin': 'https://foreign.example'}).status_code == 403
    assert client.post('/api/assess', json=walking(frameAgeMs=5001), headers={'Origin': 'http://testserver'}).status_code == 200
    response = client.post('/api/speech', content=b' ' * (server.MAX_REQUEST_BYTES + 1), headers={'Content-Type': 'application/json'})
    assert response.status_code == 413


@pytest.mark.parametrize('message', [
    '2m 앞에 사람이 있어요.', '두 걸음 앞에 사람이 있어요.', '통로가 안전합니다.',
    '장애물이 없어요.', '오른쪽으로 이동하세요.', '머리 높이에 장애물이 있어요.',
    '지팡이가 장애물을 감지합니다.', '화면 중앙에 사람이 감지됐어요.', 'A person is ahead.'
])
def test_walking_rejects_unsupported_distances_safety_and_boilerplate(monkeypatch, message):
    monkeypatch.setenv('GEMINI_API_KEY', 'test-only')
    mock_remote(monkeypatch, lambda request: gemini_reply({'speak': True, 'message': message, 'urgency': 'notice'}))
    assert client.post('/api/assess', json=walking()).status_code == 502


def test_manual_can_describe_a_visible_screen(monkeypatch):
    monkeypatch.setenv('GEMINI_API_KEY', 'test-only')
    verdict = {'speak': True, 'message': '컴퓨터 화면에 문서가 열려 있어요.', 'urgency': 'notice'}
    mock_remote(monkeypatch, lambda request: gemini_reply(verdict))
    assert client.post('/api/assess', json=walking(manual=True)).json() == verdict


def test_response_size_limit_and_incomplete_generation(monkeypatch):
    monkeypatch.setenv('GEMINI_API_KEY', 'test-only')
    mock_remote(monkeypatch, lambda request: httpx.Response(200, content=b' ' * (1024 * 1024 + 1)))
    assert client.post('/api/assess', json=walking()).status_code == 502


@pytest.mark.parametrize('content,type', [(b'not-an-mp3', 'audio/mpeg'), (MP3, 'application/json')])
def test_invalid_speech_audio_signals_native_fallback(monkeypatch, content, type):
    monkeypatch.setenv('ELEVENLABS_API_KEY', 'test-only')
    mock_remote(monkeypatch, lambda request: httpx.Response(200, content=content, headers={'content-type': type}))
    response = client.post('/api/speech', json={'text': '앞을 확인하세요.', 'voice': 'female'})
    assert response.status_code == 502 and response.json()['fallback'] is True


def test_compressed_upstream_json_is_decoded_only_once(monkeypatch):
    monkeypatch.setenv('GEMINI_API_KEY', 'test-only')
    verdict = {'speak': False, 'message': '', 'urgency': 'none'}
    wire = json.dumps({'candidates': [{'content': {'parts': [{'text': json.dumps(verdict)}]}}]}).encode()
    mock_remote(monkeypatch, lambda request: httpx.Response(200,
        headers={'content-type': 'application/json', 'content-encoding': 'gzip'}, content=gzip.compress(wire)))
    response = client.post('/api/assess', json=walking())
    assert response.status_code == 200 and response.json() == verdict


def test_personal_speech_key_defaults_to_fixed_female_voice(monkeypatch):
    monkeypatch.setenv('ELEVENLABS_VOICE_ID', 'old-server-voice')
    def remote(request):
        assert request.url.path.endswith('/' + server.VOICES['female'])
        return httpx.Response(200, content=MP3, headers={'content-type': 'audio/mpeg'})
    mock_remote(monkeypatch, remote)
    assert client.post('/api/speech', json={'text': '주변을 확인하세요.', 'apiKey': 'personal-eleven-key'}).status_code == 200


def test_camera_model_route_is_not_shadowed_by_pwa_assets():
    response = client.get('/yolo11n.onnx', headers={'Range': 'bytes=0-15'})
    assert response.status_code == 206
    assert len(response.content) == 16
    assert response.headers['content-type'] == 'application/octet-stream'


def test_pwa_script_is_served_without_api_route_interference():
    response = client.get('/sw.js')
    assert response.status_code == 200
    assert 'javascript' in response.headers.get('content-type', '')
    assert 'self.addEventListener' in response.text
    assert response.headers['cache-control'] == 'no-cache'
