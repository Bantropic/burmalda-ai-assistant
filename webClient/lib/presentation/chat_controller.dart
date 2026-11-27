import 'package:flutter/foundation.dart';

import '../data/agent_api.dart';
import '../data/agent_api_exception.dart';
import '../domain/chat.dart';

enum ChatStatus { idle, loadingSessions, loadingHistory, sending }

enum FailedOperation {
  loadingSessions,
  loadingHistory,
  creatingSession,
  sending,
}

class ChatController extends ChangeNotifier {
  ChatController(this.api);
  final AgentApi api;
  List<ChatSession> sessions = [];
  List<ChatMessage> messages = [];
  ChatSession? selected;
  ChatStatus status = ChatStatus.idle;
  FailedOperation? failedOperation;
  bool needsHistoryReload = false, _disposed = false;
  String? error;
  String? lastSentText;
  Future<bool> Function()? _retry;
  bool get isBusy => status != ChatStatus.idle;
  bool get canRetry => !isBusy && _retry != null && api.isConfigured;

  void _update() {
    if (!_disposed) notifyListeners();
  }

  Future<bool> _run(
    ChatStatus next,
    Future<void> Function() action, {
    required FailedOperation operation,
    required Future<bool> Function() retry,
  }) async {
    if (isBusy || _disposed) return false;
    status = next;
    error = null;
    failedOperation = null;
    _retry = null;
    _update();
    try {
      await action();
      return true;
    } catch (e) {
      error = e is AgentApiException
          ? e.message
          : 'Не удалось выполнить операцию. Попробуйте ещё раз.';
      failedOperation = operation;
      _retry = retry;
      return false;
    } finally {
      status = ChatStatus.idle;
      _update();
    }
  }

  Future<bool> retry() async => canRetry ? await _retry!() : false;

  Future<void> refresh() async {
    await _run(
      ChatStatus.loadingSessions,
      () async {
        sessions = await api.sessions();
      },
      operation: FailedOperation.loadingSessions,
      retry: () async {
        await refresh();
        return false;
      },
    );
  }

  void newChat() {
    if (isBusy) return;
    selected = null;
    messages = [];
    error = api.isConfigured
        ? null
        : 'Агент не подключён: адрес сервера не настроен.';
    failedOperation = null;
    _retry = null;
    needsHistoryReload = false;
    _update();
  }

  Future<void> select(ChatSession session) async {
    await _run(
      ChatStatus.loadingHistory,
      () async {
        selected = session;
        messages = [];
        needsHistoryReload = true;
        messages = await api.messages(session.id);
        needsHistoryReload = false;
      },
      operation: FailedOperation.loadingHistory,
      retry: () async {
        await select(session);
        return false;
      },
    );
  }

  Future<bool> send(String text) async {
    if (isBusy || needsHistoryReload || text.trim().isEmpty) return false;
    final content = text.trim();
    final success = await _run(
      ChatStatus.sending,
      () async {
        if (selected == null) {
          selected = await api.create();
          sessions = [selected!, ...sessions];
        }
        final local = ChatMessage('pending', 'user', content, DateTime.now());
        messages = [...messages, local];
        _update();
        try {
          messages = [...messages, await api.send(selected!.id, content)];
        } catch (_) {
          messages = messages.where((m) => !identical(m, local)).toList();
          needsHistoryReload = true;
          rethrow;
        }
      },
      operation: FailedOperation.creatingSession,
      retry: () => send(content),
    );
    if (!success && needsHistoryReload) {
      final session = selected!;
      failedOperation = FailedOperation.sending;
      // The request may have reached the server. Reconcile before resending.
      _retry = () async {
        await select(session);
        return false;
      };
      _update();
    }
    if (success) lastSentText = content;
    return success;
  }

  @override
  void dispose() {
    _disposed = true;
    api.dispose();
    super.dispose();
  }
}
