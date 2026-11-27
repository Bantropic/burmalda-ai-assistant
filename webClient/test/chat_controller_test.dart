import 'dart:convert';

import 'package:burmalda/data/agent_api.dart';
import 'package:burmalda/data/agent_api_exception.dart';
import 'package:burmalda/domain/chat.dart';
import 'package:burmalda/presentation/chat_controller.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

http.Response response(Object body, [int status = 200]) =>
    http.Response(jsonEncode(body), status);

void main() {
  test('Retry creates the session and sends the original message', () async {
    final requests = <String>[];
    var creates = 0;
    final chat = ChatController(
      AgentApi(
        'http://localhost',
        client: MockClient((r) async {
          requests.add('${r.method} ${r.url.path}');
          if (r.url.path == '/sessions') {
            if (++creates == 1) return response({'message': 'Try again'}, 500);
            return response({'id': 's1', 'title': 'Chat'}, 201);
          }
          expect(jsonDecode(r.body)['content'], 'Hello');
          return response({
            'id': 'm1',
            'role': 'assistant',
            'content': 'Hi',
            'createdAt': '2026-11-25T12:00:00Z',
          });
        }),
      ),
    );
    addTearDown(chat.dispose);
    expect(await chat.send('Hello'), isFalse);
    expect(chat.failedOperation, FailedOperation.creatingSession);
    expect(await chat.retry(), isTrue);
    expect(requests, [
      'POST /sessions',
      'POST /sessions',
      'POST /sessions/s1/messages',
    ]);
    expect(chat.messages.length, 2);
    expect(chat.status, ChatStatus.idle);
    expect(chat.error, isNull);
  });

  test('Failed send reloads history without duplicating POST', () async {
    final requests = <String>[];
    final chat = ChatController(
      AgentApi(
        'http://localhost',
        client: MockClient((r) async {
          requests.add(r.method);
          return r.method == 'POST'
              ? response({'message': 'Failed'}, 500)
              : response([]);
        }),
      ),
    );
    addTearDown(chat.dispose);
    await chat.select(const ChatSession('s1', 'Chat'));
    expect(await chat.send('Hello'), isFalse);
    expect(chat.failedOperation, FailedOperation.sending);
    expect(chat.needsHistoryReload, isTrue);
    expect(chat.messages, isEmpty);
    await chat.retry();
    expect(requests, ['GET', 'POST', 'GET']);
    expect(chat.needsHistoryReload, isFalse);
    expect(chat.error, isNull);
  });

  test('Invalid response structure becomes a typed API error', () async {
    final api = AgentApi(
      'http://localhost',
      client: MockClient(
        (_) async => response([
          {'id': 42},
        ]),
      ),
    );
    addTearDown(api.dispose);
    await expectLater(api.sessions(), throwsA(isA<AgentApiException>()));
  });
}
