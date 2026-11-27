import 'package:flutter/material.dart';

import 'widgets/chat_sidebar.dart';
import 'widgets/message_bubble.dart';
import 'widgets/message_composer.dart';
import 'widgets/chat_error_banner.dart';

import '../config/api_config.dart';
import '../data/agent_api.dart';
import 'chat_controller.dart';

class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key});
  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final chat = ChatController(AgentApi(ApiConfig.baseUrl));
  final input = TextEditingController();
  final scroll = ScrollController();
  final focus = FocusNode();
  @override
  void initState() {
    super.initState();
    chat.addListener(changed);
    chat.refresh();
  }

  String? _sessionId;
  Object? _lastMessage;
  int _messageCount = 0;
  bool _openingHistory = false;

  void changed() {
    if (!mounted) return;
    final switched = _sessionId != chat.selected?.id;
    if (switched || chat.status == ChatStatus.loadingHistory) {
      _openingHistory = true;
    }
    final last = chat.messages.lastOrNull;
    final added =
        last != null &&
        !identical(last, _lastMessage) &&
        (chat.messages.length > _messageCount || _openingHistory);
    final nearBottom = !scroll.hasClients || scroll.position.extentAfter < 120;
    final shouldScroll = added && (_openingHistory || nearBottom);
    _sessionId = chat.selected?.id;
    _lastMessage = last;
    _messageCount = chat.messages.length;
    if (added) _openingHistory = false;
    setState(() {});
    if (!shouldScroll) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && scroll.hasClients) {
        scroll.animateTo(
          scroll.position.maxScrollExtent,
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOut,
        );
      }
    });
  }

  Future<void> retry() async {
    final draft = input.text;
    if (await chat.retry() &&
        mounted &&
        input.text == draft &&
        draft.trim() == chat.lastSentText) {
      input.clear();
    }
    if (mounted) focus.requestFocus();
  }

  Future<void> send() async {
    final text = input.text;
    if (await chat.send(text) && mounted) input.clear();
    if (mounted) focus.requestFocus();
  }

  @override
  void dispose() {
    chat.removeListener(changed);
    chat.dispose();
    input.dispose();
    scroll.dispose();
    focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final narrow = MediaQuery.sizeOf(context).width < 1000;
    return Scaffold(
      drawer: narrow
          ? Drawer(child: ChatSidebar(chat: chat, drawer: true))
          : null,
      body: Row(
        children: [
          if (!narrow) ChatSidebar(chat: chat),
          Expanded(
            child: SafeArea(
              child: Column(
                children: [
                  SizedBox(
                    height: 64,
                    child: Row(
                      children: [
                        if (narrow)
                          Builder(
                            builder: (context) => IconButton(
                              onPressed: () =>
                                  Scaffold.of(context).openDrawer(),
                              icon: const Icon(Icons.menu),
                            ),
                          ),
                        const Padding(
                          padding: EdgeInsets.symmetric(horizontal: 24),
                          child: Text(
                            'Burmalda AI',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  Expanded(
                    child: Center(
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 820),
                        child: Column(
                          children: [
                            Expanded(
                              child: chat.messages.isEmpty
                                  ? Center(
                                      child: Padding(
                                        padding: const EdgeInsets.all(24),
                                        child: Column(
                                          mainAxisSize: MainAxisSize.min,
                                          children: [
                                            if (chat.isBusy)
                                              const CircularProgressIndicator(
                                                strokeWidth: 2,
                                              )
                                            else ...[
                                              const Icon(
                                                Icons.auto_awesome_outlined,
                                                size: 38,
                                              ),
                                              const SizedBox(height: 24),
                                              Text(
                                                chat.selected == null
                                                    ? 'Чем могу помочь?'
                                                    : chat.selected!.title,
                                                textAlign: TextAlign.center,
                                                style: const TextStyle(
                                                  fontSize: 30,
                                                  fontWeight: FontWeight.w500,
                                                ),
                                              ),
                                              const SizedBox(height: 12),
                                              const Text(
                                                'Задайте вопрос или опишите задачу',
                                                style: TextStyle(
                                                  color: Colors.grey,
                                                ),
                                              ),
                                            ],
                                          ],
                                        ),
                                      ),
                                    )
                                  : ListView.builder(
                                      controller: scroll,
                                      padding: const EdgeInsets.all(24),
                                      itemCount:
                                          chat.messages.length +
                                          ((chat.status == ChatStatus.sending)
                                              ? 1
                                              : 0),
                                      itemBuilder: (context, i) {
                                        if (i == chat.messages.length) {
                                          return const Padding(
                                            padding: EdgeInsets.all(16),
                                            child: Text(
                                              'Burmalda думает…',
                                              style: TextStyle(
                                                color: Colors.grey,
                                              ),
                                            ),
                                          );
                                        }
                                        return MessageBubble(
                                          message: chat.messages[i],
                                        );
                                      },
                                    ),
                            ),
                            if (chat.error != null)
                              ChatErrorBanner(
                                message: chat.error!,
                                onRetry: chat.canRetry ? retry : null,
                                reloadHistory: chat.needsHistoryReload,
                              ),
                            MessageComposer(
                              input: input,
                              focus: focus,
                              busy: chat.isBusy,
                              needsHistoryReload: chat.needsHistoryReload,
                              isConfigured: chat.api.isConfigured,
                              send: send,
                              onChanged: () => setState(() {}),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
