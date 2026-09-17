import 'package:dio/dio.dart';
import 'package:path/path.dart' as p;
import '../../core/api/api_client.dart';

class CiagroRepository {
  final _api = ApiClient.instance.dio;

  Future<List<Map<String, dynamic>>> parentCias() async => _paged('api/v1/organizations/data-centrals-main/');
  Future<List<Map<String, dynamic>>> cias() async => _paged('api/v1/organizations/datacentrals/');
  Future<List<Map<String, dynamic>>> agroUnits({String? dataCentral}) async => _paged('api/v1/organizations/', query: {'datacentral': dataCentral, 'data_central': dataCentral});
  Future<List<Map<String, dynamic>>> ranches() async => _paged('api/v1/geo_assets/ranches/');
  Future<List<Map<String, dynamic>>> plots() async => _paged('api/v1/geo_assets/plots/');
  Future<List<Map<String, dynamic>>> crops() async => _paged('api/v1/agro-catalogs/crops/');
  Future<List<Map<String, dynamic>>> phytosanitary() async => _paged('api/v1/agro-catalogs/phytosanitary/');

  Future<List<Map<String, dynamic>>> monitoringHeaders({String? plot, String? status, String? assignedTo}) async => _paged('api/v1/monitoring/phyto/headers/', query: {'plot':plot,'status':status,'assigned_to':assignedTo});
  Future<List<Map<String, dynamic>>> checkpoints(String headerId) async => _paged('api/v1/monitoring/phyto/checkpoints/', query: {'header':headerId});
  Future<List<Map<String, dynamic>>> targetPoints() async => _paged('api/v1/monitoring/phyto/target-points/');

  Future<List<Map<String, dynamic>>> aspersionSessions({String? cia, String? plot, String? status, String? from, String? to}) async => _paged('api/v1/monitoring/aspersion/headers/', query: {'data_central':cia,'plot':plot,'status':status,'date_from':from,'date_to':to,'page_size':100});
  Future<Map<String, dynamic>> aspersionDetail(String id) async => _one('api/v1/monitoring/aspersion/headers/$id/');
  Future<List<Map<String, dynamic>>> aspersionPoints(String id) async => _paged('api/v1/monitoring/aspersion/points/', query: {'session_header':id,'page_size':2000});
  Future<Map<String, dynamic>> aspersionStats(String id) async => _one('api/v1/monitoring/aspersion/headers/$id/stats/');

  Future<List<Map<String, dynamic>>> ndviSessions({String? cia, String? plot}) async => _paged('api/v1/monitoring/ndvi/headers/', query: {'data_central':cia,'plot':plot,'page_size':100});
  Future<Map<String, dynamic>> ndviDetail(String id) async => _one('api/v1/monitoring/ndvi/headers/$id/');
  Future<List<Map<String, dynamic>>> ndviPoints(String id) async => _paged('api/v1/monitoring/ndvi/points/', query: {'session_header':id,'page_size':2000});
  Future<Map<String, dynamic>> ndviStats(String id) async => _one('api/v1/monitoring/ndvi/headers/$id/variable-stats/');
  Future<Map<String, dynamic>> ndviContourIndices(String id) async => _one('api/v1/monitoring/ndvi/headers/$id/contours/indices/');

  Future<void> patchMonitoringHeader(String id, Map<String, dynamic> data) => _api.patch('api/v1/monitoring/phyto/headers/$id/update/', data:data);
  Future<Map<String, dynamic>> createCheckpoint(Map<String, dynamic> data) async {
    final r = await _api.post('api/v1/monitoring/phyto/checkpoints/create/', data:data);
    return Map<String,dynamic>.from(r.data as Map);
  }

  Future<Map<String, dynamic>> uploadCheckpointPhoto(String checkpointId, String filePath) async {
    final form = FormData.fromMap({
      'photo': await MultipartFile.fromFile(filePath, filename: p.basename(filePath)),
    });
    final r = await _api.patch('api/v1/monitoring/phyto/checkpoints/$checkpointId/update/', data: form);
    return Map<String,dynamic>.from(r.data as Map);
  }

  Future<List<Map<String, dynamic>>> _paged(String path, {Map<String, dynamic>? query}) async {
    final q = <String,dynamic>{...?query}..removeWhere((k,v)=>v==null || '$v'.isEmpty);
    final all=<Map<String,dynamic>>[];
    var page=1;
    for (var guard=0; guard<50; guard++) {
      q['page']=page;
      final r=await _api.get(path, queryParameters:q);
      final batch=ApiClient.unpackResults(r.data);
      all.addAll(batch);
      if (r.data is! Map || (r.data as Map)['next']==null) break;
      page++;
    }
    return all;
  }

  Future<Map<String,dynamic>> _one(String path) async {
    final r=await _api.get(path);
    return Map<String,dynamic>.from(r.data as Map);
  }
}
