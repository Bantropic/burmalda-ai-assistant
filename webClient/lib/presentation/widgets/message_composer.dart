import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class MessageComposer extends StatelessWidget {
  const MessageComposer({
    super.key,
    required this.input,
    required this.focus,
    required this.busy,
    required this.needsHistoryReload,
    required this.isConfigured,
    required this.send,
    required this.onChanged,
  });
  final TextEditingController input;
  final FocusNode focus;
  final bool busy, needsHistoryReload, isConfigured;
  final VoidCallback send, onChanged;
  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.fromLTRB(20, 12, 20, 16),
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(
      color: const Color(0xff303030),
      borderRadius: BorderRadius.circular(26),
    ),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Focus(
          onKeyEvent: (_, event) {
            if (event is KeyDownEvent &&
                event.logicalKey == LogicalKeyboardKey.enter &&
                !HardwareKeyboard.instance.isShiftPressed &&
                input.value.composing.isCollapsed) {
              send();
              return KeyEventResult.handled;
            }
            return KeyEventResult.ignored;
          },
          child: TextField(
            controller: input,
            focusNode: focus,
            readOnly: busy,
            minLines: 1,
            maxLines: 6,
            decoration: const InputDecoration(
              hintText: 'Спросите что-нибудь…',
              border: InputBorder.none,
            ),
            onChanged: (_) => onChanged(),
          ),
        ),
        Row(
          children: [
            const Expanded(
              child: Text(
                'Enter — отправить · Shift + Enter — новая строка',
                style: TextStyle(fontSize: 11, color: Colors.grey),
              ),
            ),
            const SizedBox(width: 8),
            IconButton.filled(
              tooltip: 'Отправить',
              onPressed:
                  !isConfigured ||
                      busy ||
                      needsHistoryReload ||
                      input.text.trim().isEmpty
                  ? null
                  : send,
              icon: const Icon(Icons.arrow_upward),
            ),
          ],
        ),
      ],
    ),
  );
}
