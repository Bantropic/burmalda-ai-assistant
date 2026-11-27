import 'package:flutter/material.dart';

import '../chat_controller.dart';

class ChatSidebar extends StatelessWidget {
  const ChatSidebar({super.key, required this.chat, this.drawer = false});
  final ChatController chat;
  final bool drawer;
  @override
  Widget build(BuildContext context) => Container(
    width: 272,
    color: const Color(0xff212121),
    child: SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 20, horizontal: 12),
              child: Text(
                'Burmalda',
                style: TextStyle(fontSize: 24, fontWeight: FontWeight.w600),
              ),
            ),
            OutlinedButton.icon(
              onPressed: chat.isBusy
                  ? null
                  : () {
                      chat.newChat();
                      if (drawer) Navigator.pop(context);
                    },
              icon: const Icon(Icons.edit_square, size: 18),
              label: const Text('Новый чат'),
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.all(18),
              ),
            ),
            const SizedBox(height: 28),
            Row(
              children: [
                const Expanded(
                  child: Text('Недавние', style: TextStyle(color: Colors.grey)),
                ),
              ],
            ),
            Expanded(
              child: chat.sessions.isEmpty
                  ? const Padding(
                      padding: EdgeInsets.all(12),
                      child: Text(
                        'Бурмалда побурмалди',
                        style: TextStyle(color: Colors.grey, height: 1.5),
                      ),
                    )
                  : ListView(
                      children: chat.sessions
                          .map(
                            (s) => Padding(
                              padding: const EdgeInsets.only(bottom: 4),
                              child: ListTile(
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                selected: chat.selected?.id == s.id,
                                selectedTileColor: const Color(0xff363636),
                                title: Text(
                                  s.title,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                leading: const Icon(
                                  Icons.chat_bubble_outline,
                                  size: 18,
                                ),
                                onTap: chat.isBusy
                                    ? null
                                    : () {
                                        chat.select(s);
                                        if (drawer) Navigator.pop(context);
                                      },
                              ),
                            ),
                          )
                          .toList(),
                    ),
            ),
          ],
        ),
      ),
    ),
  );
}
