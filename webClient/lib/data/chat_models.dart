import '../domain/chat.dart';
import 'agent_api_exception.dart';

Never _invalid() => throw const AgentApiException(
  'Формат ответа сервера не соответствует API.',
);

Map<String, dynamic> _object(Object? value) =>
    value is Map<String, dynamic> ? value : _invalid();

String _string(Map<String, dynamic> json, String key) =>
    json[key] is String ? json[key] as String : _invalid();

List<T> decodeList<T>(Object? value, T Function(Object?) decode) =>
    value is List ? value.map(decode).toList() : _invalid();

class ChatSessionModel {
  static ChatSession fromJson(Object? value) {
    final json = _object(value);
    return ChatSession(_string(json, 'id'), _string(json, 'title'));
  }
}

class ChatMessageModel {
  static ChatMessage fromJson(Object? value) {
    final json = _object(value);
    final role = _string(json, 'role');
    final createdAt = DateTime.tryParse(_string(json, 'createdAt'));
    if ((role != 'user' && role != 'assistant') || createdAt == null) {
      _invalid();
    }
    return ChatMessage(
      _string(json, 'id'),
      role,
      _string(json, 'content'),
      createdAt,
    );
  }
}
