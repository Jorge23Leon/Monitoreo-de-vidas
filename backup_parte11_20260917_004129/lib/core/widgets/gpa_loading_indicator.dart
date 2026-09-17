import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

/// Marca GPA reutilizable con la tuerca animada y el logotipo central fijo.
///
/// Se usa tanto en el login como en indicadores de carga para mantener una
/// identidad visual consistente en toda la aplicación.
class GpaRotatingMark extends StatefulWidget {
  const GpaRotatingMark({
    super.key,
    this.size = 72,
    this.duration = const Duration(seconds: 8),
    this.backgroundColor,
    this.padding = 8,
  });

  final double size;
  final Duration duration;
  final Color? backgroundColor;
  final double padding;

  @override
  State<GpaRotatingMark> createState() => _GpaRotatingMarkState();
}

class _GpaRotatingMarkState extends State<GpaRotatingMark>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: widget.duration)
      ..repeat();
  }

  @override
  void didUpdateWidget(covariant GpaRotatingMark oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.duration != widget.duration) {
      _controller
        ..duration = widget.duration
        ..repeat();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final innerSize = (widget.size - widget.padding * 2)
        .clamp(16.0, widget.size)
        .toDouble();
    final centerSize = innerSize * 0.56;

    return Semantics(
      label: 'Grupo GPA',
      image: true,
      child: Container(
        width: widget.size,
        height: widget.size,
        padding: EdgeInsets.all(widget.padding),
        decoration: BoxDecoration(
          color: widget.backgroundColor,
          shape: BoxShape.circle,
        ),
        child: Stack(
          alignment: Alignment.center,
          children: [
            Container(
              width: innerSize * 0.70,
              height: innerSize * 0.70,
              decoration: const BoxDecoration(
                color: Colors.white,
                shape: BoxShape.circle,
              ),
            ),
            RotationTransition(
              turns: _controller,
              child: Image.asset(
                'assets/images/agroindustry-gear.png',
                width: innerSize,
                height: innerSize,
                fit: BoxFit.contain,
                filterQuality: FilterQuality.high,
              ),
            ),
            Image.asset(
              'assets/images/gpa-center.png',
              width: centerSize,
              fit: BoxFit.contain,
              filterQuality: FilterQuality.high,
              semanticLabel: 'GPA',
            ),
          ],
        ),
      ),
    );
  }
}

/// Indicador de carga visual de CIAGRO basado en el logo GPA.
class GpaLoadingIndicator extends StatelessWidget {
  const GpaLoadingIndicator({
    super.key,
    this.text = 'Cargando...',
    this.size = 58,
    this.showText = true,
    this.textColor,
    this.backgroundColor,
  });

  final String text;
  final double size;
  final bool showText;
  final Color? textColor;
  final Color? backgroundColor;

  @override
  Widget build(BuildContext context) {
    final mark = GpaRotatingMark(
      size: size,
      duration: const Duration(milliseconds: 1100),
      backgroundColor: backgroundColor,
      padding: size <= 30 ? 2 : 5,
    );

    if (!showText) return mark;

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        mark,
        const SizedBox(height: 10),
        Text(
          text,
          textAlign: TextAlign.center,
          style: TextStyle(
            color: textColor ?? AppTheme.darkGreen,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }
}
