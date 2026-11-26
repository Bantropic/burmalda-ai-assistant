class ChatSession {
  const ChatSession(this.id, this.title);
  final String id, title;
}

class ChatMessage {
  const ChatMessage(this.id, this.role, this.content, this.createdAt);
  final String id, role, content;
  final DateTime createdAt;
}
