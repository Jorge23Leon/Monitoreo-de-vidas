import 'package:flutter/material.dart';

class LoadingView extends StatelessWidget {
  const LoadingView({super.key, this.text='Cargando...'});
  final String text;
  @override Widget build(BuildContext context) => Center(child: Column(mainAxisSize: MainAxisSize.min, children:[const CircularProgressIndicator(), const SizedBox(height:12), Text(text)]));
}

class ErrorView extends StatelessWidget {
  const ErrorView({super.key, required this.message, required this.onRetry});
  final String message; final VoidCallback onRetry;
  @override Widget build(BuildContext context) => Center(child: Padding(padding:const EdgeInsets.all(24), child:Column(mainAxisSize:MainAxisSize.min, children:[const Icon(Icons.error_outline,size:48),const SizedBox(height:12),Text(message,textAlign:TextAlign.center),const SizedBox(height:16),FilledButton(onPressed:onRetry,child:const Text('Reintentar'))])));
}
