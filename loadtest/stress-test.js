import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// BASE_URL aponta pra dentro da rede do docker-compose por padrão (serviço "mapi-api"), já que
// este script roda no container k6 do próprio compose. Pra rodar da máquina host contra a API
// publicada, passe -e BASE_URL=http://localhost:8080.
const BASE_URL = __ENV.BASE_URL || 'http://mapi-api:8080';

// Pool ampliado de ~3 para ~10 coordenadas (todas dentro da RMR): com só 3 pontos, depois das
// 3 primeiras chamadas TUDO vinha do @Cacheable de WeatherService/MarineService e o
// taskExecutor (pool "Ingest-" que alimenta o dashboard 01-teoria-das-filas) parava de receber
// tarefas — só o dashboard do k6 continuava variando. Com mais pontos, novos cache misses reais
// continuam acontecendo ao longo da rampa (VUs sorteiam índices novos conforme sobem), mantendo
// o taskExecutor e o circuit breaker "openMeteo" ativos durante o teste inteiro. Ainda é um pool
// pequeno e fixo de propósito: o cache evict roda a cada 10min (CacheConfig), então isso não gera
// uma explosão de chamadas reais à Open-Meteo, só mais variedade que 3 pontos únicos.
const COORDS = [
  { lat: -8.05, lon: -34.90 }, // Recife - Centro
  { lat: -8.04, lon: -34.87 }, // Recife - Boa Viagem
  { lat: -7.99, lon: -34.85 }, // Olinda
  { lat: -8.11, lon: -34.99 }, // Jaboatão dos Guararapes
  { lat: -8.02, lon: -34.94 }, // Recife - Zona Oeste
  { lat: -7.94, lon: -34.87 }, // Paulista
  { lat: -8.02, lon: -35.02 }, // Camaragibe
  { lat: -8.28, lon: -35.03 }, // Cabo de Santo Agostinho
  { lat: -8.15, lon: -34.92 }, // São Lourenço da Mata
  { lat: -7.84, lon: -34.87 }, // Igarassu
];

const errorRate = new Rate('errors');
// Métrica separada da "errors" global: a mapi-ai (POST /api/flood-predictions) ainda não está
// 100% estável, então falhas dela não devem contar pro threshold geral do teste nem fazer o
// k6 sair com erro — só ficam visíveis à parte no dashboard/console.
const floodPredictionErrorRate = new Rate('flood_prediction_errors');

export const options = {
  scenarios: {
    ramping_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 15 },
        { duration: '1m', target: 40 },
        { duration: '1m30s', target: 40 },
        // Spike pra 70 VUs: o objetivo aqui não é mais só simular tráfego, é saturar de
        // propósito o pool do HikariCP (10 conexões por padrão) e os threads do Tomcat, pra que
        // "Utilização" e "Queue Length" do dashboard 01-teoria-das-filas realmente subam do zero
        // em vez de ficarem uma linha reta — sem isso, 30 VUs nunca chegava perto de enfileirar
        // nada.
        { duration: '30s', target: 70 },
        { duration: '30s', target: 70 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '15s',
    },
    // Cenário paralelo, poucas VUs, vida inteira do teste: bate periodicamente no endpoint de
    // exportação de CSV (query mais pesada de DB + serialização) pra gerar picos visíveis de heap/
    // GC (dashboard 03-jvm-recursos) e de uso do HikariCP que não dependem do tráfego "normal" de
    // leitura rápida do ramping_load. days=30 (em vez do endpoint /all/csv com days=0, que puxa
    // TODO o histórico de TODOS os pontos) mantém isso pesado mas limitado — não é pra arriscar
    // OOM a cada iteração.
    heavy_export: {
      executor: 'constant-vus',
      vus: 2,
      duration: '4m30s',
      startTime: '5s',
      exec: 'heavyExport',
    },
    // Cenário à parte pra POST /api/flood-predictions (chama a mapi-ai via
    // FloodPredictionServiceImpl). Poucas VUs e iterações espaçadas porque é a chamada mais cara
    // do teste (I/O pra outro serviço) e, principalmente, porque a mapi-ai ainda não está 100%
    // estável — não queremos que ela vire o gargalo dominante da rampa principal nem que suas
    // falhas contaminem a métrica "errors" global (ver flood_prediction_errors abaixo).
    flood_prediction: {
      executor: 'constant-vus',
      vus: 2,
      duration: '4m30s',
      startTime: '5s',
      exec: 'floodPrediction',
    },
  },
  thresholds: {
    // Escopados por cenário (tag automática "scenario" do k6): a rampa principal e o export
    // pesado têm perfis de latência completamente diferentes, então um limiar único acabava
    // sendo ou frouxo demais pra rampa ou impossível de cumprir pro export.
    'http_req_duration{scenario:ramping_load}': ['p(95)<3000'],
    'http_req_duration{scenario:heavy_export}': ['p(95)<15000'],
    errors: ['rate<0.05'],
    // Sem threshold em cima de flood_prediction_errors de propósito: a mapi-ai ainda está em
    // desenvolvimento e é esperado que erre com frequência. A métrica fica só de olho no
    // dashboard/console, sem fazer o k6 sair com código de erro por causa dela.
  },
};

function hit(name, url, params, metric = errorRate) {
  // tags.name fixo em vez da URL crua: sem isso, cada querystring diferente (lat/lon, sensorId,
  // slug) vira uma série própria no Prometheus — explode cardinalidade e quebra o agrupamento por
  // endpoint nos painéis do dashboard 05-k6-load-test.json.
  const mergedParams = Object.assign({}, params, {
    tags: Object.assign({ name }, params && params.tags),
  });
  const res = http.get(url, mergedParams);
  const ok = check(res, { [`${name} -> 2xx/3xx`]: (r) => r.status >= 200 && r.status < 400 });
  metric.add(!ok);
  return res;
}

function post(name, url, body, params, metric = errorRate) {
  const mergedParams = Object.assign({}, params, {
    tags: Object.assign({ name }, params && params.tags),
  });
  const res = http.post(url, body, mergedParams);
  const ok = check(res, { [`${name} -> 2xx/3xx`]: (r) => r.status >= 200 && r.status < 400 });
  metric.add(!ok);
  return res;
}

export function setup() {
  const username = `loadtest_${Date.now()}`;
  const password = 'LoadTest#12345';
  const headers = { headers: { 'Content-Type': 'application/json' } };

  http.post(`${BASE_URL}/api/auth/register`, JSON.stringify({ username, password }), headers);

  const loginRes = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ username, password }), headers);
  check(loginRes, { 'login retornou 200': (r) => r.status === 200 });
  const token = loginRes.json('accessToken');
  const authHeaders = { headers: { Authorization: `Bearer ${token}` } };

  const idsRes = http.get(`${BASE_URL}/api/sensors/ids`);
  const sensorIds = idsRes.status === 200 ? idsRes.json() : [];

  // Slugs reais dos pontos cadastrados, usados pelo cenário heavy_export — evita hardcoded/mock
  // que quebraria assim que os pontos de monitoramento mudassem.
  const pontosRes = http.get(`${BASE_URL}/api/pontos`, authHeaders);
  const slugs = pontosRes.status === 200 ? pontosRes.json().map((p) => p.id_ponto).filter(Boolean) : [];

  return { token, sensorIds, slugs };
}

export default function (data) {
  const authHeaders = { headers: { Authorization: `Bearer ${data.token}` } };
  const coord = COORDS[Math.floor(Math.random() * COORDS.length)];
  const qs = `latitude=${coord.lat}&longitude=${coord.lon}`;

  group('publico - sensores e clima', () => {
    hit('sensors/latest', `${BASE_URL}/api/sensors/latest`);
    hit('sensors/ids', `${BASE_URL}/api/sensors/ids`);
    hit('sensors/inventory', `${BASE_URL}/api/sensors/inventory?page=0&size=20`);
    if (data.sensorIds.length > 0) {
      const sensorId = data.sensorIds[Math.floor(Math.random() * data.sensorIds.length)];
      hit('sensors/{id}/latest', `${BASE_URL}/api/sensors/${sensorId}/latest`);
      hit('sensors/{id}/history', `${BASE_URL}/api/sensors/${sensorId}/history?page=0&size=20`);
    }
    hit('weather', `${BASE_URL}/api/weather?${qs}`);
  });

  group('autenticado - mare e pontos', () => {
    hit('tide/harbors', `${BASE_URL}/api/tide/harbors`, authHeaders);
    // Ao contrário de weather/marine, TabuaMareServiceImpl não tem @Cacheable — em carga
    // concorrente alta isso pode abrir o circuit breaker Resilience4j contra a API externa
    // (visto ao vivo rodando este script: WARN "CircuitBreaker 'tabuaMare' is OPEN"). É
    // esperado, não um bug: acompanhe no dashboard 04-coletores-resiliencia.json.
    hit('tabua-mare/states', `${BASE_URL}/api/tabua-mare/states`, authHeaders);
    hit('marine', `${BASE_URL}/api/marine?${qs}`, authHeaders);
    hit('pontos', `${BASE_URL}/api/pontos`, authHeaders);
  });

  sleep(Math.random() * 1 + 0.5);
}

export function heavyExport(data) {
  if (data.slugs.length === 0) {
    sleep(10);
    return;
  }
  const authHeaders = { headers: { Authorization: `Bearer ${data.token}` }, timeout: '30s' };
  const slug = data.slugs[Math.floor(Math.random() * data.slugs.length)];
  hit('export/csv', `${BASE_URL}/api/export/ia-dataset/${slug}/csv?days=30`, authHeaders);
  sleep(10 + Math.random() * 5);
}

export function floodPrediction(data) {
  const authHeaders = { headers: { Authorization: `Bearer ${data.token}` }, timeout: '15s' };
  const coord = COORDS[Math.floor(Math.random() * COORDS.length)];
  const qs = `latitude=${coord.lat}&longitude=${coord.lon}`;
  post('flood-predictions', `${BASE_URL}/api/flood-predictions?${qs}`, null, authHeaders, floodPredictionErrorRate);
  sleep(5 + Math.random() * 5);
}
