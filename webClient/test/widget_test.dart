import 'package:flutter_test/flutter_test.dart';
import 'package:burmalda/main.dart';

void main() {
  testWidgets('Shows disconnected chat', (tester) async {
    await tester.pumpWidget(const MyApp());
    expect(find.text('Чем могу помочь?'), findsOneWidget);
    await tester.pumpAndSettle();
    expect(
      find.text('Агент не подключён: адрес сервера не настроен.'),
      findsOneWidget,
    );
  });
}
