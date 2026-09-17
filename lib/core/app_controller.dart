import 'package:flutter/widgets.dart';
import 'models/session_user.dart';
import 'storage/app_database.dart';
import 'storage/token_storage.dart';
import '../features/auth/auth_repository.dart';

enum AppPage {
  loading, login, signup, info, contact,
  ciaSelection, modules,
  monitoringList, monitoringMap, monitoringCheckpoint, monitoringReport,
  aspersionList, aspersionDetail, aspersionMap,
  ndviList, ndviDetail, ndviMap,
  profile, adminHome,
}

class AppController extends ChangeNotifier {
  final AuthRepository auth = AuthRepository();
  AppPage page = AppPage.loading;
  SessionUser? user;
  Map<String, dynamic>? selectedCia;
  Map<String, dynamic>? selectedMonitoring;
  Map<String, dynamic>? selectedTargetPoint;
  Map<String, dynamic>? selectedAspersion;
  Map<String, dynamic>? selectedNdvi;
  bool busy = false;
  String? message;

  Future<void> initialize() async {
    await AppDatabase.instance.database;
    final access = await TokenStorage.instance.accessToken;
    if (access == null || access.isEmpty) {
      page = AppPage.login;
      notifyListeners();
      return;
    }
    try {
      user = await auth.me();
      page = _initialPageFor(user!);
    } catch (_) {
      await TokenStorage.instance.clearTokens();
      page = AppPage.login;
    }
    notifyListeners();
  }

  Future<bool> login(String username, String password, bool remember) async {
    return _guard(() async {
      await auth.login(username, password);
      await TokenStorage.instance.saveCredentials(username, password, remember);
      user = await auth.me();
      page = _initialPageFor(user!);
    });
  }

  Future<void> logout() async {
    busy = true; notifyListeners();
    await auth.logout();
    user = null; selectedCia = null; selectedMonitoring = null; selectedTargetPoint = null; page = AppPage.login; busy = false; notifyListeners();
  }

  void go(AppPage next) { page = next; message = null; notifyListeners(); }
  void selectCia(Map<String, dynamic> cia) {
    selectedCia = cia;
    final session = user;
    // Esta app Flutter se enfoca en monitoreo fitosanitario. Después de
    // seleccionar la CIA hija se entra directamente a los filtros/listado.
    page = AppPage.monitoringList;
    notifyListeners();
  }
  void selectMonitoring(Map<String, dynamic> item) { selectedMonitoring = item; selectedTargetPoint = null; page = AppPage.monitoringMap; notifyListeners(); }
  void selectTargetPoint(Map<String, dynamic> item) { selectedTargetPoint = item; page = AppPage.monitoringCheckpoint; notifyListeners(); }
  void selectAspersion(Map<String, dynamic> item) { selectedAspersion = item; page = AppPage.aspersionDetail; notifyListeners(); }
  void selectNdvi(Map<String, dynamic> item) { selectedNdvi = item; page = AppPage.ndviDetail; notifyListeners(); }
  void consumeMessage() { message = null; notifyListeners(); }


  AppPage _initialPageFor(SessionUser session) {
    // La migración actual incluye únicamente el flujo operativo de plagas y
    // enfermedades: técnico/invitado entran directo a monitoreos; los roles
    // jerárquicos seleccionan primero su CIA hija.
    if (session.isTechnician || session.isGuest) return AppPage.monitoringList;
    return AppPage.ciaSelection;
  }

  Future<bool> _guard(Future<void> Function() action) async {
    busy = true; message = null; notifyListeners();
    try { await action(); return true; }
    catch (e) { message = _friendlyError(e); return false; }
    finally { busy = false; notifyListeners(); }
  }

  String _friendlyError(Object e) {
    final text = e.toString().replaceFirst('Exception: ', '');
    if (text.contains('401')) return 'Usuario o contraseña incorrectos.';
    if (text.contains('SocketException') || text.contains('connection')) return 'No se pudo conectar al servidor.';
    return text;
  }
}

class AppScope extends InheritedNotifier<AppController> {
  const AppScope({super.key, required AppController controller, required super.child}) : super(notifier: controller);
  static AppController of(BuildContext context) => context.dependOnInheritedWidgetOfExactType<AppScope>()!.notifier!;
  static AppController read(BuildContext context) => context.getInheritedWidgetOfExactType<AppScope>()!.notifier!;
}
