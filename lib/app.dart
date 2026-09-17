import 'package:flutter/material.dart';

import 'core/app_controller.dart';
import 'core/theme/app_theme.dart';
import 'core/widgets/state_views.dart';
import 'core/widgets/navigation_guards.dart';
import 'features/auth/login_screen.dart';
import 'features/auth/signup_screen.dart';
import 'features/cia/cia_selection_screen.dart';
import 'features/misc/contact_screen.dart';
import 'features/misc/info_screen.dart';
import 'features/misc/misc_screens.dart';
import 'features/monitoring/monitoring_screens.dart';

class CiagroApp extends StatefulWidget {
  const CiagroApp({super.key});

  @override
  State<CiagroApp> createState() => _CiagroAppState();
}

class _CiagroAppState extends State<CiagroApp> {
  final controller = AppController();

  @override
  void initState() {
    super.initState();
    controller.initialize();
  }

  @override
  Widget build(BuildContext context) {
    return AppScope(
      controller: controller,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        title: 'Tierra Inteligente',
        theme: AppTheme.light,
        home: AnimatedBuilder(
          animation: controller,
          builder: (context, _) {
            if (controller.message != null && controller.page != AppPage.login) {
              WidgetsBinding.instance.addPostFrameCallback((_) {
                final messenger = ScaffoldMessenger.maybeOf(context);
                final msg = controller.message;
                if (msg != null) {
                  messenger?.showSnackBar(SnackBar(content: Text(msg)));
                  controller.consumeMessage();
                }
              });
            }
            final currentPage = _page(controller.page);

            if (controller.page == AppPage.login ||
                controller.page == AppPage.ciaSelection) {
              return ExitAppGuard(child: currentPage);
            }

            if (controller.page == AppPage.info ||
                controller.page == AppPage.contact ||
                controller.page == AppPage.signup) {
              return BackToLoginGuard(
                onBackToLogin: () => controller.go(AppPage.login),
                child: currentPage,
              );
            }

            return currentPage;
          },
        ),
      ),
    );
  }

  Widget _page(AppPage page) {
    return switch (page) {
      AppPage.loading => const Scaffold(body: LoadingView(text: 'Cargando sesión...')),
      AppPage.login => const LoginScreen(),
      AppPage.signup => const SignupScreen(),
      AppPage.info => const InfoScreen(),
      AppPage.contact => const ContactScreen(),
      AppPage.ciaSelection => const CiaSelectionScreen(),
      AppPage.monitoringList => const MonitoringListScreen(),
      AppPage.monitoringMap => const MonitoringMapScreen(),
      AppPage.monitoringCheckpoint => const MonitoringCheckpointScreen(),
      AppPage.monitoringReport => const MonitoringReportScreen(),
      AppPage.profile => const ProfileScreen(),
      AppPage.modules => const MonitoringListScreen(),
      AppPage.aspersionList => const MonitoringListScreen(),
      AppPage.aspersionDetail => const MonitoringListScreen(),
      AppPage.aspersionMap => const MonitoringListScreen(),
      AppPage.ndviList => const MonitoringListScreen(),
      AppPage.ndviDetail => const MonitoringListScreen(),
      AppPage.ndviMap => const MonitoringListScreen(),
      AppPage.adminHome => const MonitoringListScreen(),
    };
  }
}
