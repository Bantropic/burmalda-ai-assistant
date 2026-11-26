import 'package:flutter/material.dart';

class ChatErrorBanner extends StatelessWidget {
  const ChatErrorBanner({
    super.key,
    required this.message,
    this.onRetry,
    this.reloadHistory = false,
  });
  final String message;
  final VoidCallback? onRetry;
  final bool reloadHistory;

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    margin: const EdgeInsets.symmetric(horizontal: 20),
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(
      color: const Color(0xff303030),
      border: Border.all(color: Colors.white38),
      borderRadius: BorderRadius.circular(16),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(Icons.error_outline, size: 22),
            const SizedBox(width: 12),
            Expanded(
              child: Text(message, style: const TextStyle(color: Colors.white)),
            ),
          ],
        ),
        if (onRetry != null)
          TextButton(
            onPressed: onRetry,
            child: Text(
              reloadHistory
                  ? 'Загрузить историю перед повторной отправкой'
                  : 'Повторить',
            ),
          ),
      ],
    ),
  );
}
