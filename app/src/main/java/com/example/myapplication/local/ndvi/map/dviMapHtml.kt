package com.example.myapplication.local.ndvi.map

import com.example.myapplication.local.entities.LocalNdviPointEntity
import com.example.myapplication.local.ndvi.model.NdviIndex
import org.json.JSONArray
import org.json.JSONObject


/**
 * Mapa NDVI calculado localmente, siguiendo la misma idea del visor web:
 *
 * puntos -> Kriging -> malla -> clasificación -> imagen ráster.
 *
 * Si Kriging no logra ajustar el variograma, se usa IDW como fallback.
 * Los colores/bandas vienen de variable-config de la CIA activa.
 */
internal fun createNdviMapHtml(
    points: List<LocalNdviPointEntity>,
    index: NdviIndex,
    internetAvailable: Boolean,
    variableConfigJson: String? = null
): String {

    val pointsJson = JSONArray().apply {
        points.forEach { point ->
            val value = point.valueForNdviIndex(index)
            if (
                point.latitude.isFinite() &&
                point.longitude.isFinite()
            ) {
                put(
                    JSONObject().apply {
                        put("id", point.pointId)
                        put("obj_id", point.objId ?: JSONObject.NULL)
                        put("lat", point.latitude)
                        put("lon", point.longitude)
                        put("value", value ?: JSONObject.NULL)

                        // Datos del punto que mostramos al tocarlo en el mapa,
                        // igual que el popup del frontend web.
                        put("ndvi", point.ndvi ?: JSONObject.NULL)
                        put("nir_vigor", point.nirVigor ?: JSONObject.NULL)
                        put("osavi", point.osavi ?: JSONObject.NULL)
                        put("vari", point.vari ?: JSONObject.NULL)
                        put("bare_soil_index", point.bareSoilIndex ?: JSONObject.NULL)
                        put("red_edge", point.redEdge ?: JSONObject.NULL)
                        put("swir", point.swir ?: JSONObject.NULL)
                        put("ndre", point.ndre ?: JSONObject.NULL)
                        put("msavi2", point.msavi2 ?: JSONObject.NULL)
                        put("gndvi", point.gndvi ?: JSONObject.NULL)
                        put("ndmi", point.ndmi ?: JSONObject.NULL)
                        put("psri", point.psri ?: JSONObject.NULL)
                    }
                )
            }
        }
    }

    val configJson =
        variableConfigJson
            ?.trim()
            ?.takeIf {
                it.isNotEmpty() && it != "null"
            }
            ?.let { raw ->
                runCatching {
                    JSONObject(raw).toString()
                }.getOrNull()
            }
            ?: "null"

    val indexKey = JSONObject.quote(index.apiKey)
    val indexLabel = JSONObject.quote(index.label)
    val internetJson = if (internetAvailable) "true" else "false"

    return """
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=yes" />
<link rel="stylesheet" href="file:///android_asset/leaflet/leaflet.css" />
<style>
html,body,#map{position:fixed;inset:0;width:100%;height:100%;margin:0;padding:0;overflow:hidden;background:#dfe8d1;font-family:Arial,sans-serif}
.leaflet-container{background:#dfe8d1}.leaflet-control-attribution{font-size:9px}
.panel{position:absolute;z-index:900;background:rgba(255,255,255,.92);border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.22);color:#33413A;backdrop-filter:blur(4px)}
#title{left:8px;top:8px;padding:7px 9px;color:#195C25;font-size:12px;font-weight:700}
#mode{display:block;margin-top:2px;color:#6C756F;font-size:9px;font-weight:500}
#stats{left:8px;bottom:38px;width:205px;padding:0;font-size:10px;overflow:hidden}
#statsHeader{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:8px 9px;cursor:pointer}
.stats-title{color:#195C25;font-size:11px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
#statsToggle{border:0;background:#E8F4E9;color:#195C25;border-radius:8px;padding:4px 7px;font-size:9px;font-weight:700}
#statsBody{display:none;padding:0 9px 9px}
#stats.expanded #statsBody{display:block}
.stats-grid{display:grid;grid-template-columns:auto auto;gap:2px 10px;margin-bottom:6px}
#legend{max-height:165px;overflow-y:auto;padding-top:5px;border-top:1px solid rgba(0,0,0,.09)}
.legend-row{display:flex;align-items:center;gap:6px;margin:3px 0}.swatch{width:12px;height:12px;flex:0 0 12px;border-radius:3px;border:1px solid rgba(0,0,0,.14)}
.legend-gradient{height:12px;border-radius:7px;margin:7px 0 4px;background:linear-gradient(to right,#d32f2f,#f57c00,#388e3c,#00acc1,#1565c0)}
.legend-range{display:flex;justify-content:space-between;color:#69766E;font-size:10px}
#loading{left:50%;top:50%;transform:translate(-50%,-50%);padding:12px 16px;color:#195C25;font-size:12px;font-weight:700;text-align:center}
.spinner{width:22px;height:22px;margin:0 auto 8px;border-radius:50%;border:3px solid #D9EAD9;border-top-color:#2E7D32;animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
#message{left:12px;right:12px;bottom:12px;padding:9px 12px;color:#4F5D55;font-size:11px;text-align:center}
/* Popup de detalle de punto, inspirado en el visor web. */
.leaflet-popup-content-wrapper{border-radius:9px;box-shadow:0 3px 16px rgba(0,0,0,.26)}
.leaflet-popup-content{margin:12px 14px;min-width:220px;max-width:290px}
.ndvi-point-popup{color:#43505c;font-size:11px;line-height:1.25}
.ndvi-point-title{font-size:13px;font-weight:700;margin:0 0 5px;color:#3d4b58}
.ndvi-point-primary{font-size:12px;color:#202a33;padding-bottom:6px;margin-bottom:6px;border-bottom:1px solid #d7dde2}
.ndvi-point-grid{display:grid;grid-template-columns:1fr 1fr;column-gap:14px;row-gap:5px}
.ndvi-point-item{display:flex;justify-content:space-between;gap:7px;white-space:nowrap}
.ndvi-point-label{color:#687687;overflow:hidden;text-overflow:ellipsis}
.ndvi-point-value{color:#667486;font-variant-numeric:tabular-nums}
</style>
</head>
<body>
<div id="map"></div>
<div id="title" class="panel">Índice: <span id="indexName"></span><span id="mode">Interpolación Kriging local</span></div>
<div id="stats" class="panel" style="display:none">
<div id="statsHeader" onclick="toggleStats()">
<div class="stats-title" id="statsTitle"></div>
<button id="statsToggle" type="button">Mostrar</button>
</div>
<div id="statsBody">
<div id="statsGrid" class="stats-grid"></div>
<div id="legend"></div>
</div>
</div>
<div id="loading" class="panel"><div class="spinner"></div>Calculando superficie NDVI…</div>
<script src="file:///android_asset/leaflet/leaflet.js"></script>
<script>
const rawPoints=$pointsJson;
const variableConfig=$configJson;
const indexKey=$indexKey;
const indexLabel=$indexLabel;
const internetAvailable=$internetJson;

// Web usa hasta ~700 puntos y grid 260. En móvil bajamos el costo sin cambiar el modelo.
const MAX_KRIGING_POINTS=220;
const GRID_SIZE=180;
const IDW_POWER=4;
const DEFAULT_SMOOTHING_FACTOR=.5;
const ABSOLUTE_BANDS_SMOOTHING_FACTOR=0;
const DEFAULT_QUARTILE_PALETTE=['#d73027','#fc8d59','#fee090','#e0f3f8','#91bfdb','#4575b4'];
const RAMP=[[211,47,47],[245,124,0],[56,142,60],[0,172,193],[21,101,192]];
let map=null;

function safe(v){return String(v==null?'':v).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#039;')}
function num(v){const n=Number(v);return Number.isFinite(n)?n:null}
function fmt(v,d=3){const n=num(v);return n==null?'—':n.toFixed(d)}
function rep(v,n){return new Array(n).fill(v)}
function matDiag(c,n){const z=rep(0,n*n);for(let i=0;i<n;i++)z[i*n+i]=c;return z}
function matTranspose(x,n,m){const z=new Array(m*n);for(let i=0;i<n;i++)for(let j=0;j<m;j++)z[j*n+i]=x[i*m+j];return z}
function matAdd(x,y,n,m){const z=new Array(n*m);for(let i=0;i<n;i++)for(let j=0;j<m;j++)z[i*m+j]=x[i*m+j]+y[i*m+j];return z}
function matMul(x,y,n,m,p){const z=new Array(n*p);for(let i=0;i<n;i++){for(let j=0;j<p;j++){let s=0;for(let k=0;k<m;k++)s+=x[i*m+k]*y[k*p+j];z[i*p+j]=s}}return z}
function matChol(x,n){const p=rep(0,n);for(let i=0;i<n;i++)p[i]=x[i*n+i];for(let i=0;i<n;i++){for(let j=0;j<i;j++)p[i]-=x[i*n+j]*x[i*n+j];if(p[i]<=0)return false;p[i]=Math.sqrt(p[i]);for(let j=i+1;j<n;j++){for(let k=0;k<i;k++)x[j*n+i]-=x[j*n+k]*x[i*n+k];x[j*n+i]/=p[i]}}for(let i=0;i<n;i++)x[i*n+i]=p[i];return true}
function matChol2inv(x,n){for(let i=0;i<n;i++){x[i*n+i]=1/x[i*n+i];for(let j=i+1;j<n;j++){let sum=0;for(let k=i;k<j;k++)sum-=x[j*n+k]*x[k*n+i];x[j*n+i]=sum/x[j*n+j]}}for(let i=0;i<n;i++)for(let j=i+1;j<n;j++)x[i*n+j]=0;for(let i=0;i<n;i++){x[i*n+i]*=x[i*n+i];for(let k=i+1;k<n;k++)x[i*n+i]+=x[k*n+i]*x[k*n+i];for(let j=i+1;j<n;j++)for(let k=j;k<n;k++)x[i*n+j]+=x[k*n+i]*x[k*n+j]}for(let i=0;i<n;i++)for(let j=0;j<i;j++)x[i*n+j]=x[j*n+i]}
function variogramExp(h,nugget,range,sill,A){return nugget+((sill-nugget)/range)*(1-Math.exp(-(1/A)*(h/range)))}

function krigingTrain(t,x,y,sigma2=0,alpha=100){
    const A=1/3,n=t.length;if(n<3)return null;
    const m=(n*n-n)/2,distance=new Array(m);
    for(let i=0,k=0;i<n;i++)for(let j=0;j<i;j++,k++){const dx=x[i]-x[j],dy=y[i]-y[j];distance[k]=[Math.sqrt(dx*dx+dy*dy),Math.abs(t[i]-t[j])]}
    distance.sort((a,b)=>a[0]-b[0]);let range=distance[m-1][0];if(range===0)return null;
    const lags=m>30?30:m,tolerance=range/lags;let lag=rep(0,lags),semi=rep(0,lags),nLags;
    if(lags<30){for(let l=0;l<lags;l++){lag[l]=distance[l][0];semi[l]=distance[l][1]}nLags=lags}else{let l=0;for(let i=0,j=0;i<lags&&j<m;i++){let k=0;while(distance[j][0]<=(i+1)*tolerance){lag[l]+=distance[j][0];semi[l]+=distance[j][1];j++;k++;if(j>=m)break}if(k>0){lag[l]/=k;semi[l]/=k;l++}}lag=lag.slice(0,l);semi=semi.slice(0,l);nLags=l}
    if(nLags<2)return null;range=lag[nLags-1]-lag[0];if(range===0)return null;
    const X=rep(1,2*nLags),Y=new Array(nLags);for(let i=0;i<nLags;i++){X[i*2+1]=1-Math.exp(-(1/A)*(lag[i]/range));Y[i]=semi[i]}
    const Xt=matTranspose(X,nLags,2);let Z=matMul(Xt,X,2,nLags,2);Z=matAdd(Z,matDiag(1/alpha,2),2,2);if(!matChol(Z,2))return null;matChol2inv(Z,2);
    const W=matMul(matMul(Z,Xt,2,2,nLags),Y,2,nLags,1);let nugget=W[0],sill=W[1]*range+nugget;if(nugget<0)nugget=0;
    const K=new Array(n*n);for(let i=0;i<n;i++){for(let j=0;j<i;j++){const dx=x[i]-x[j],dy=y[i]-y[j],val=variogramExp(Math.sqrt(dx*dx+dy*dy),nugget,range,sill,A);K[i*n+j]=val;K[j*n+i]=val}K[i*n+i]=variogramExp(0,nugget,range,sill,A)}
    let sig=sigma2,C=matAdd(K,matDiag(sig,n),n,n),ok=matChol(C,n),tries=0;while(!ok&&tries<6){sig=sig>0?sig*10:1e-6;C=matAdd(K,matDiag(sig,n),n,n);ok=matChol(C,n);tries++}if(!ok)return null;matChol2inv(C,n);const M=matMul(C,t,n,n,1);
    return{x:x.slice(),y:y.slice(),nugget,range,sill,A,n,M};
}
function krigingPredict(px,py,v){let s=0;for(let i=0;i<v.n;i++){const dx=px-v.x[i],dy=py-v.y[i];s+=variogramExp(Math.sqrt(dx*dx+dy*dy),v.nugget,v.range,v.sill,v.A)*v.M[i]}return s}

function convexHull(pts){const p=pts.map(q=>[q.lon,q.lat]).sort((u,v)=>(u[0]-v[0])||(u[1]-v[1]));if(p.length<3)return p;const cross=(o,a,b)=>(a[0]-o[0])*(b[1]-o[1])-(a[1]-o[1])*(b[0]-o[0]);const lower=[];for(const pt of p){while(lower.length>=2&&cross(lower[lower.length-2],lower[lower.length-1],pt)<=0)lower.pop();lower.push(pt)}const upper=[];for(let i=p.length-1;i>=0;i--){const pt=p[i];while(upper.length>=2&&cross(upper[upper.length-2],upper[upper.length-1],pt)<=0)upper.pop();upper.push(pt)}lower.pop();upper.pop();return lower.concat(upper)}
function pointInRing(x,y,ring){let inside=false;for(let i=0,j=ring.length-1;i<ring.length;j=i++){const xi=ring[i][0],yi=ring[i][1],xj=ring[j][0],yj=ring[j][1];const intersect=yi>y!==yj>y&&x<((xj-xi)*(y-yi))/(yj-yi)+xi;if(intersect)inside=!inside}return inside}
function subsample(pts,max){if(pts.length<=max)return pts;const step=Math.ceil(pts.length/max),out=[];for(let i=0;i<pts.length;i+=step)out.push(pts[i]);return out}
function idwValue(x,y,pts){let n=0,d=0;for(const p of pts){const dx=x-p.lon,dy=y-p.lat,d2=dx*dx+dy*dy;if(d2===0)return p.value;const w=1/Math.pow(d2,IDW_POWER/2);n+=w*p.value;d+=w}return d===0?NaN:n/d}
function sampleSpacing(width,height,n){if(n<=1||width<=0||height<=0)return 0;return Math.sqrt((width*height)/n)}
function blurGrid(src,w,h,radius,passes=2){if(radius<1)return src;const r=Math.round(radius);let cur=src;for(let pass=0;pass<passes;pass++){const tmp=new Float32Array(w*h);for(let y=0;y<h;y++)for(let x=0;x<w;x++){let sum=0,count=0;const from=Math.max(0,x-r),to=Math.min(w-1,x+r);for(let k=from;k<=to;k++){const v=cur[y*w+k];if(!Number.isNaN(v)){sum+=v;count++}}tmp[y*w+x]=count?sum/count:NaN}const out=new Float32Array(w*h);for(let y=0;y<h;y++)for(let x=0;x<w;x++){let sum=0,count=0;const from=Math.max(0,y-r),to=Math.min(h-1,y+r);for(let k=from;k<=to;k++){const v=tmp[k*w+x];if(!Number.isNaN(v)){sum+=v;count++}}out[y*w+x]=count?sum/count:NaN}cur=out}return cur}
function quantileBreaks(values,n){const sorted=values.slice().sort((a,b)=>a-b);if(!sorted.length)return[];const count=Math.max(2,n);const at=f=>{if(sorted.length===1)return sorted[0];const pos=f*(sorted.length-1),lo=Math.floor(pos),hi=Math.min(sorted.length-1,lo+1);return sorted[lo]+(sorted[hi]-sorted[lo])*(pos-lo)};const breaks=[sorted[0]];for(let i=1;i<count;i++)breaks.push(at(i/count));breaks.push(sorted[sorted.length-1]);return breaks}
function rankFraction(v,sorted){const n=sorted.length;if(n<=1)return .5;let lo=0,hi=n;while(lo<hi){const mid=(lo+hi)>>1;if(sorted[mid]<v)lo=mid+1;else hi=mid}return lo/(n-1)}
function hexToRgb(hex){let h=String(hex||'').replace('#','');if(h.length===3)h=h[0]+h[0]+h[1]+h[1]+h[2]+h[2];if(!/^[0-9a-fA-F]{6}$/.test(h))return[128,128,128];return[parseInt(h.slice(0,2),16),parseInt(h.slice(2,4),16),parseInt(h.slice(4,6),16)]}
function rampColor(t){const c=Math.max(0,Math.min(1,t)),seg=c*(RAMP.length-1),i=Math.min(RAMP.length-2,Math.floor(seg)),f=seg-i,a=RAMP[i],b=RAMP[i+1];return[Math.round(a[0]+(b[0]-a[0])*f),Math.round(a[1]+(b[1]-a[1])*f),Math.round(a[2]+(b[2]-a[2])*f)]}
function normalizeColor(v,fallback){const c=typeof v==='string'?v.trim():'';return(/^#[0-9a-fA-F]{6}$/.test(c)||/^#[0-9a-fA-F]{3}$/.test(c))?c:fallback}
function resolveQuartileColors(cfg,n){const count=Math.max(2,Number(n)||4),chosen=Array.isArray(cfg&&cfg.colors)?cfg.colors:[],result=[],last=DEFAULT_QUARTILE_PALETTE.length-1;for(let i=0;i<count;i++){const fallback=DEFAULT_QUARTILE_PALETTE[Math.round((i*last)/Math.max(1,count-1))];result.push(normalizeColor(chosen[i],fallback))}return result}
function readConfig(){if(!variableConfig||typeof variableConfig!=='object')return{strategy:'quartile',n_bands:4};const raw=variableConfig[indexKey];return raw&&typeof raw==='object'?raw:{strategy:'quartile',n_bands:4}}
function manualBands(cfg){if(cfg.strategy!=='manual'||!Array.isArray(cfg.bands)||!cfg.bands.length)return null;return cfg.bands.slice().sort((a,b)=>Number(a.order||0)-Number(b.order||0)).map((b,i)=>({order:Number.isFinite(Number(b.order))?Number(b.order):i,min:b.min==null?null:num(b.min),max:b.max==null?null:num(b.max),label:b.label==null?null:String(b.label),color:normalizeColor(b.color,'#808080')}))}
function bandColor(v,bands){for(const b of bands){if((b.min===null||v>=b.min)&&(b.max===null||v<b.max))return hexToRgb(b.color)}return null}
function mean(values){if(!values.length)return null;let s=0;for(const v of values)s+=v;return s/values.length}
function stddev(values,avg){if(!values.length||avg==null)return null;let s=0;for(const v of values){const d=v-avg;s+=d*d}return Math.sqrt(s/values.length)}
function rangeLabel(b){const min=b.min==null?'−∞':fmt(b.min,2),max=b.max==null?'+∞':fmt(b.max,2);return min+' – '+max}

const POINT_INDEX_FIELDS=[
    ['ndvi','NDVI'],
    ['nir_vigor','Vigor NIR'],
    ['osavi','OSAVI'],
    ['vari','VARI'],
    ['bare_soil_index','Suelo desnudo'],
    ['red_edge','Límite rojo'],
    ['swir','SWIR'],
    ['ndre','NDRE'],
    ['msavi2','MSAVI2'],
    ['gndvi','GNDVI'],
    ['ndmi','NDMI'],
    ['psri','PSRI']
];
let selectedPointMarker=null;

function pointPopupHtml(p){
    const pointNumber=p.obj_id!=null?p.obj_id:p.displayNumber;
    const selectedValue=num(p[indexKey])!=null?p[indexKey]:p.value;
    const rows=POINT_INDEX_FIELDS
        .filter(field=>field[0]!==indexKey)
        .map(field=>'<div class="ndvi-point-item"><span class="ndvi-point-label">'+safe(field[1])+'</span><span class="ndvi-point-value">'+fmt(p[field[0]])+'</span></div>')
        .join('');
    return '<div class="ndvi-point-popup">'
        +'<div class="ndvi-point-title">Punto '+safe(pointNumber)+'</div>'
        +'<div class="ndvi-point-primary">'+safe(indexLabel)+': <b>'+fmt(selectedValue)+'</b></div>'
        +'<div class="ndvi-point-grid">'+rows+'</div>'
        +'</div>';
}

function selectPoint(p){
    // Cerramos el popup anterior antes de crear el nuevo resaltado.
    map.closePopup();
    if(selectedPointMarker){map.removeLayer(selectedPointMarker);selectedPointMarker=null;}
    selectedPointMarker=L.circleMarker([p.lat,p.lon],{
        radius:5,
        color:'#145c2a',
        weight:2,
        fillColor:'#ffffff',
        fillOpacity:.95,
        interactive:false
    }).addTo(map);
    L.popup({
        maxWidth:320,
        autoPan:true,
        autoPanPaddingTopLeft:[14,95],
        autoPanPaddingBottomRight:[14,105],
        closeButton:true
    })
        .setLatLng([p.lat,p.lon])
        .setContent(pointPopupHtml(p))
        .openOn(map);
}

function installPointSelection(pts){
    // Dibujamos los puntos sin hacer miles de capas táctiles grandes.
    // El toque se resuelve buscando el punto visualmente más cercano.
    const pointRenderer=L.canvas({padding:.5});
    for(const p of pts){
        L.circleMarker([p.lat,p.lon],{
            radius:1.35,
            stroke:false,
            fillColor:'#FFFFFF',
            fillOpacity:.28,
            interactive:false,
            renderer:pointRenderer
        }).addTo(map);
    }

    map.on('click',function(e){
        const clickPx=map.latLngToContainerPoint(e.latlng);
        let nearest=null;
        let nearestD2=Infinity;
        for(const p of pts){
            const pp=map.latLngToContainerPoint([p.lat,p.lon]);
            const dx=pp.x-clickPx.x,dy=pp.y-clickPx.y,d2=dx*dx+dy*dy;
            if(d2<nearestD2){nearestD2=d2;nearest=p;}
        }
        // Área táctil amplia para celular, sin obligar a tocar el pixel exacto.
        const hitRadiusPx=20;
        if(nearest&&nearestD2<=hitRadiusPx*hitRadiusPx)selectPoint(nearest);
    });

    map.on('popupclose',function(){
        if(selectedPointMarker){map.removeLayer(selectedPointMarker);selectedPointMarker=null;}
    });
}

function toggleStats(){
    const box=document.getElementById('stats'),button=document.getElementById('statsToggle');
    if(!box||!button)return;
    const expanded=box.classList.toggle('expanded');
    button.textContent=expanded?'Ocultar':'Mostrar';
    setTimeout(()=>{if(map)map.invalidateSize(false)},60);
}

function buildField(pts,manual){
    const hull=convexHull(pts);if(hull.length<3)return null;
    const sub=subsample(pts,MAX_KRIGING_POINTS);
    const variogram=krigingTrain(sub.map(p=>p.value),sub.map(p=>p.lon),sub.map(p=>p.lat));
    const predict=variogram?(lon,lat)=>krigingPredict(lon,lat,variogram):(lon,lat)=>idwValue(lon,lat,pts);
    const xs=hull.map(c=>c[0]),ys=hull.map(c=>c[1]),xmin=Math.min(...xs),xmax=Math.max(...xs),ymin=Math.min(...ys),ymax=Math.max(...ys);if(xmax===xmin||ymax===ymin)return null;
    const w=GRID_SIZE,h=Math.max(1,Math.min(360,Math.round((GRID_SIZE*(ymax-ymin))/(xmax-xmin))));
    let grid=new Float32Array(w*h);
    for(let row=0;row<h;row++){const lat=ymax-(row/Math.max(1,h-1))*(ymax-ymin);for(let col=0;col<w;col++){const lon=xmin+(col/Math.max(1,w-1))*(xmax-xmin);grid[row*w+col]=pointInRing(lon,lat,hull)?predict(lon,lat):NaN}}
    const smoothing=manual?ABSOLUTE_BANDS_SMOOTHING_FACTOR:DEFAULT_SMOOTHING_FACTOR,spacing=sampleSpacing(xmax-xmin,ymax-ymin,pts.length),cellSize=(xmax-xmin)/Math.max(1,w-1),radiusCells=cellSize>0?(spacing*smoothing)/cellSize:0;if(radiusCells>=1)grid=blurGrid(grid,w,h,radiusCells);
    return{grid,w,h,xmin,xmax,ymin,ymax,hull,engine:variogram?'Kriging':'IDW'};
}

function render(pts){
    const cfg=readConfig(),manual=manualBands(cfg),quartileColors=!manual&&cfg.strategy==='quartile'&&Array.isArray(cfg.colors)&&cfg.colors.length?resolveQuartileColors(cfg,cfg.n_bands||cfg.colors.length):null;
    const field=buildField(pts,!!manual);if(!field)throw new Error('No se pudo construir la malla.');
    const values=pts.map(p=>p.value),pointSorted=values.slice().sort((a,b)=>a-b),fieldSorted=[];for(const v of field.grid)if(!Number.isNaN(v))fieldSorted.push(v);fieldSorted.sort((a,b)=>a-b);const scale=fieldSorted.length>1?fieldSorted:pointSorted;
    const canvas=document.createElement('canvas');canvas.width=field.w;canvas.height=field.h;const ctx=canvas.getContext('2d'),img=ctx.createImageData(field.w,field.h);
    for(let i=0;i<field.grid.length;i++){const idx=i*4,v=field.grid[i];if(Number.isNaN(v)){img.data[idx+3]=0;continue}let rgb=null;if(manual)rgb=bandColor(v,manual);else if(quartileColors&&quartileColors.length){const cls=Math.min(quartileColors.length-1,Math.floor(rankFraction(v,scale)*quartileColors.length));rgb=hexToRgb(quartileColors[cls])}else rgb=rampColor(rankFraction(v,scale));if(!rgb){img.data[idx+3]=0;continue}img.data[idx]=rgb[0];img.data[idx+1]=rgb[1];img.data[idx+2]=rgb[2];img.data[idx+3]=238}
    ctx.putImageData(img,0,0);L.imageOverlay(canvas.toDataURL('image/png'),[[field.ymin,field.xmin],[field.ymax,field.xmax]],{opacity:.92,interactive:false}).addTo(map);
    L.polygon(field.hull.map(c=>[c[1],c[0]]),{color:'#008E53',weight:2,fill:false,opacity:.95}).addTo(map);
    installPointSelection(pts);
    map.fitBounds([[field.ymin,field.xmin],[field.ymax,field.xmax]],{padding:[20,20],maxZoom:18});
    const avg=mean(values),dev=stddev(values,avg);document.getElementById('statsTitle').textContent=indexLabel+' · '+values.length+' puntos';document.getElementById('statsGrid').innerHTML='<span>Media</span><b>'+fmt(avg)+'</b><span>Desv.</span><b>'+fmt(dev)+'</b><span>Mín.</span><b>'+fmt(Math.min(...values))+'</b><span>Máx.</span><b>'+fmt(Math.max(...values))+'</b>';
    let legend='';if(manual){manual.forEach((b,i)=>{legend+='<div class="legend-row"><span class="swatch" style="background:'+safe(b.color)+'"></span><span>Clase '+(i+1)+' · '+safe(rangeLabel(b))+'</span></div>'})}else if(quartileColors&&quartileColors.length){const breaks=quantileBreaks(scale,quartileColors.length);quartileColors.forEach((c,i)=>{legend+='<div class="legend-row"><span class="swatch" style="background:'+safe(c)+'"></span><span>Clase '+(i+1)+' · '+fmt(breaks[i],2)+' – '+fmt(breaks[i+1],2)+'</span></div>'})}else{legend='<div class="legend-gradient"></div><div class="legend-range"><span>'+fmt(Math.min(...values),2)+'</span><span>'+fmt(Math.max(...values),2)+'</span></div>'}
    document.getElementById('legend').innerHTML=legend;const statsBox=document.getElementById('stats');statsBox.style.display='block';statsBox.classList.remove('expanded');document.getElementById('statsToggle').textContent='Mostrar';document.getElementById('mode').textContent='Superficie '+field.engine+' local';
}

function showMessage(text){let box=document.getElementById('message');if(!box){box=document.createElement('div');box.id='message';box.className='panel';document.body.appendChild(box)}box.innerHTML=text}

try{
    document.getElementById('indexName').textContent=indexLabel;
    // Igual que el mapa de plagas/enfermedades: Leaflet puede acercar hasta 28,
    // pero Esri solo se consulta hasta su zoom nativo 18. A partir de ahí Leaflet
    // amplía la última imagen disponible y evita los mosaicos grises
    // "Map data not yet available".
    map=L.map('map',{
        zoomControl:true,
        preferCanvas:true,
        zoomSnap:.25,
        zoomDelta:.5,
        minZoom:3,
        maxZoom:28
    });
    L.tileLayer(
        'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
        {
            minZoom:3,
            maxZoom:28,
            maxNativeZoom:18,
            attribution:internetAvailable?'Tiles © Esri':'Mapa en cache',
            opacity:1,
            errorTileUrl:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lCjQ9wAAAABJRU5ErkJggg=='
        }
    ).addTo(map);
    L.control.scale({imperial:false,position:'bottomleft'}).addTo(map);
    const pts=rawPoints.map((p,i)=>Object.assign({},p,{lat:num(p.lat),lon:num(p.lon),value:num(p.value),displayNumber:i+1})).filter(p=>p.lat!=null&&p.lon!=null&&p.value!=null);
    if(pts.length<3){document.getElementById('loading').style.display='none';map.setView([21.12,-101.68],8);showMessage('No hay suficientes valores para '+safe(indexLabel)+'.')}else{setTimeout(()=>{try{render(pts);document.getElementById('loading').style.display='none';if(!internetAvailable)showMessage('Sin internet: la superficie se calculó con los puntos NDVI guardados localmente.')}catch(error){document.getElementById('loading').style.display='none';showMessage('<b>No se pudo calcular la superficie.</b><br>'+safe(error&&error.message?error.message:error))}},100)}
}catch(error){document.getElementById('loading').style.display='none';showMessage('<b>No se pudo iniciar el mapa NDVI.</b><br>'+safe(error&&error.message?error.message:error))}
window.ajustarMapaMonitoreo=function(){if(map)map.invalidateSize(true)};
</script>
</body>
</html>
    """.trimIndent()
}


private fun LocalNdviPointEntity.valueForNdviIndex(
    index: NdviIndex
): Double? {
    return when (index) {
        NdviIndex.NDVI -> ndvi
        NdviIndex.NIR_VIGOR -> nirVigor
        NdviIndex.OSAVI -> osavi
        NdviIndex.VARI -> vari
        NdviIndex.BARE_SOIL_INDEX -> bareSoilIndex
        NdviIndex.RED_EDGE -> redEdge
        NdviIndex.SWIR -> swir
        NdviIndex.NDRE -> ndre
        NdviIndex.MSAVI2 -> msavi2
        NdviIndex.GNDVI -> gndvi
        NdviIndex.NDMI -> ndmi
        NdviIndex.PSRI -> psri
    }
}