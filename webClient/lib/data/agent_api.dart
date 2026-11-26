import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../domain/chat.dart';
import 'agent_api_exception.dart';
import 'chat_models.dart';

class AgentApi {
  AgentApi(this.baseUrl, {http.Client? client})
    : _client = client ?? http.Client();
  final String baseUrl;
  final http.Client _client;
  bool get isConfigured => baseUrl.trim().isNotEmpty;
  Future<dynamic> _request(
    String path, {
    bool post = false,
    String? content,
  }) async {
    if (!isConfigured) {
      throw AgentApiException('Агент не подключён: адрес сервера не настроен.');
    }
    final uri = Uri.tryParse(
      '${baseUrl.replaceFirst(RegExp(r"/+$"), "")}$path',
    );
    if (uri == null ||
        !uri.hasAuthority ||
        (uri.scheme != 'http' && uri.scheme != 'https')) {
      throw const AgentApiException('Некорректный адрес сервера.');
    }
    final http.Response response;
    try {
      response =
          await (post
                  ? _client.post(
                      uri,
                      headers: {'Content-Type': 'application/json'},
                      body: content == null
                          ? null
                          : jsonEncode({'content': content}),
                    )
                  : _client.get(uri))
              .timeout(Duration(seconds: content == null ? 20 : 120));
    } on TimeoutException {
      throw AgentApiException('Сервер не ответил вовремя. Попробуйте ещё раз.');
    } on http.ClientException {
      throw AgentApiException(
        'Не удалось подключиться к агенту. Проверьте, что сервер запущен '
        'и доступен. В браузере соединение также может блокировать CORS.',
      );
    }
    dynamic json;
    try {
      json = jsonDecode(utf8.decode(response.bodyBytes));
    } on FormatException {
      if (response.statusCode >= 200 && response.statusCode < 300) {
        throw AgentApiException('Сервер вернул некорректный JSON.');
      }
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      final detail = json is Map
          ? json['message'] ?? json['detail'] ?? json['error']
          : null;
      throw AgentApiException(
        'Ошибка HTTP ${response.statusCode}.'
        '${detail is String && detail.trim().isNotEmpty ? ' $detail' : ' Не удалось выполнить запрос.'}',
        statusCode: response.statusCode,
      );
    }
    return json;
  }

  Future<List<ChatSession>> sessions() async =>
      decodeList(await _request('/sessions'), ChatSessionModel.fromJson);
  Future<ChatSession> create() async =>
      ChatSessionModel.fromJson(await _request('/sessions', post: true));
  Future<List<ChatMessage>> messages(String id) async => decodeList(
    await _request('/sessions/${Uri.encodeComponent(id)}/messages'),
    ChatMessageModel.fromJson,
  );
  Future<ChatMessage> send(String id, String text) async =>
      ChatMessageModel.fromJson(
        await _request(
          '/sessions/${Uri.encodeComponent(id)}/messages',
          post: true,
          content: text,
        ),
      );
  void dispose() => _client.close();
}
