import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../domain/chat.dart';

class MessageBubble extends StatelessWidget {
  const MessageBubble({super.key, required this.message});
  final ChatMessage message;
  @override
  Widget build(BuildContext context) {
    final user = message.role == 'user';
    return Align(
      alignment: user ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: const BoxConstraints(maxWidth: 650),
        margin: const EdgeInsets.only(bottom: 24),
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: user ? const Color(0xff303030) : null,
          borderRadius: BorderRadius.circular(22),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (!user)
              const Padding(
                padding: EdgeInsets.only(bottom: 10),
                child: Text(
                  'Burmalda',
                  style: TextStyle(fontWeight: FontWeight.w600),
                ),
              ),
            SelectableText(
              message.content,
              style: const TextStyle(fontSize: 16, height: 1.6),
            ),
            if (!user)
              IconButton(
                tooltip: 'Копировать',
                icon: const Icon(Icons.copy_outlined, size: 16),
                onPressed: () =>
                    Clipboard.setData(ClipboardData(text: message.content)),
              ),
          ],
        ),
      ),
    );
  }
}
