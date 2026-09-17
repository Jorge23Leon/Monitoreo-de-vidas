import 'package:flutter/material.dart';

/// Réplica del logo animado del frontend web de CIAGRO.
///
/// La tuerca gira lentamente mientras el texto y la marca central permanecen fijos.
/// Se usan exactamente los assets `agroindustry-gear.png` y
/// `agroindustry-static.png` del frontend web.
class AnimatedAgroindustryLogo extends StatefulWidget {
  const AnimatedAgroindustryLogo({
    super.key,
    this.width = 170,
    this.duration = const Duration(seconds: 32),
  });

  final double width;
  final Duration duration;

  @override
  State<AnimatedAgroindustryLogo> createState() => _AnimatedAgroindustryLogoState();
}

class _AnimatedAgroindustryLogoState extends State<AnimatedAgroindustryLogo>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: widget.duration)
      ..repeat();
  }

  @override
  void didUpdateWidget(covariant AnimatedAgroindustryLogo oldWidget) {
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
    // Misma relación de aspecto que usa el frontend: 5390 / 2040.
    final height = widget.width * (2040 / 5390);
    final gearWidth = widget.width * 0.3449;
    final gearTop = height * 0.03627;

    return Semantics(
      label: 'GPA Agroindustry',
      image: true,
      child: SizedBox(
        width: widget.width,
        height: height,
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            Positioned(
              left: 0,
              top: gearTop,
              width: gearWidth,
              child: RotationTransition(
                turns: _controller,
                child: Image.asset(
                  'assets/images/agroindustry-gear.png',
                  fit: BoxFit.contain,
                  filterQuality: FilterQuality.high,
                ),
              ),
            ),
            Positioned.fill(
              child: Image.asset(
                'assets/images/agroindustry-static.png',
                fit: BoxFit.contain,
                alignment: Alignment.centerLeft,
                filterQuality: FilterQuality.high,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
