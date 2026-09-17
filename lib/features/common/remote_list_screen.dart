import 'package:flutter/material.dart';

import '../../core/widgets/app_header.dart';
import '../../core/widgets/state_views.dart';

class RemoteListScreen extends StatefulWidget {
  const RemoteListScreen({
    super.key,
    required this.title,
    required this.loader,
    required this.onTap,
    this.emptyText = 'No hay registros.',
    this.subtitleBuilder,
  });

  final String title;
  final String emptyText;
  final Future<List<Map<String, dynamic>>> Function() loader;
  final void Function(Map<String, dynamic>) onTap;
  final String Function(Map<String, dynamic>)? subtitleBuilder;

  @override
  State<RemoteListScreen> createState() => _RemoteListScreenState();
}

class _RemoteListScreenState extends State<RemoteListScreen> {
  late Future<List<Map<String, dynamic>>> future;

  @override
  void initState() {
    super.initState();
    future = widget.loader();
  }

  Future<void> _reload() async {
    final next = widget.loader();
    setState(() => future = next);
    await next;
  }

  String _title(Map<String, dynamic> item) {
    return '${item['name'] ?? item['label'] ?? item['program_name'] ?? item['id'] ?? 'Registro'}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Column(
        children: [
          AppHeader(title: widget.title),
          Expanded(
            child: FutureBuilder<List<Map<String, dynamic>>>(
              future: future,
              builder: (context, snapshot) {
                if (snapshot.connectionState != ConnectionState.done) {
                  return const LoadingView();
                }
                if (snapshot.hasError) {
                  return ErrorView(
                    message: '${snapshot.error}',
                    onRetry: _reload,
                  );
                }

                final data = snapshot.data ?? const <Map<String, dynamic>>[];
                return RefreshIndicator(
                  onRefresh: _reload,
                  child: data.isEmpty
                      ? ListView(
                          physics: const AlwaysScrollableScrollPhysics(),
                          padding: const EdgeInsets.all(24),
                          children: [
                            const SizedBox(height: 90),
                            Icon(
                              Icons.inbox_outlined,
                              size: 54,
                              color: Theme.of(context).colorScheme.outline,
                            ),
                            const SizedBox(height: 14),
                            Text(
                              widget.emptyText,
                              textAlign: TextAlign.center,
                              style: Theme.of(context).textTheme.titleMedium,
                            ),
                          ],
                        )
                      : ListView.separated(
                          physics: const AlwaysScrollableScrollPhysics(),
                          padding: const EdgeInsets.all(14),
                          itemCount: data.length,
                          separatorBuilder: (_, _) => const SizedBox(height: 8),
                          itemBuilder: (context, index) {
                            final item = data[index];
                            return Card(
                              child: ListTile(
                                title: Text(_title(item)),
                                subtitle: widget.subtitleBuilder == null
                                    ? null
                                    : Text(widget.subtitleBuilder!(item)),
                                trailing: const Icon(Icons.chevron_right),
                                onTap: () => widget.onTap(item),
                              ),
                            );
                          },
                        ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
