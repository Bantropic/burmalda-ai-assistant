/// Same-origin API address when the app is served through Nginx.
/// For flutter run, override with --dart-define=API_BASE_URL=http://localhost:8080
abstract final class ApiConfig {
  static const baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: '/api',
  );
}
