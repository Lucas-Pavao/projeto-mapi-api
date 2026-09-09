import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Script separado do stress-test.js "realista": aqui o objetivo NÃO é simular tráfego plausível,
// é forçar oscilações extremas e rápidas de carga pra que os dashboards do Grafana (01, 03, 04 e
// 05) tenham picos e vales bem visíveis numa apresentação ao vivo — em vez da rampa suave e única
// do stress-test.js, que sobe devagar e é menos didática pra "ver a curva mudar na tela".
const BASE_URL = __ENV.BASE_URL || 'http://mapi-api:8080';

const COORDS = [
  { lat: -8.05, lon: -34.90 },
  { lat: -8.04, lon: -34.87 },
  { lat: -7.99, lon: -34.85 },
  { lat: -8.11, lon: -34.99 },
  { lat: -8.02, lon: -34.94 },
  { lat: -7.94, lon: -34.87 },
  { lat: -8.02, lon: -35.02 },
  { lat: -8.28, lon: -35.03 },
  { lat: -8.15, lon: -34.92 },
  { lat: -7.84, lon: -34.87 },
];

const errorRate = new Rate('errors');

export const options = {
  scenarios: {
    spike_demo: {
      executor: 'ramping-vus',
      startVUs: 0,
      // Dente-de-serra de propósito: 3 picos bruscos (cada vez mais alto) intercalados com vales
      // até 0 VUs, em vez de uma rampa única. ~2min50s no total — curto o bastante pra caber numa
      // demo em aula, mas com transições rápidas o suficiente (5-10s pra subir) pra aparecerem
      // como degraus nítidos no Grafana, não como uma curva suave.
      stages: [
        { duration: '10s', target: 5 },    // aquecimento
        { duration: '10s', target: 120 },  // pico 1 - subida brusca
        { duration: '20s', target: 120 },  // sustenta pico 1 (dá tempo do HikariCP/Tomcat reagirem)
        { duration: '10s', target: 0 },    // vale total - queda também brusca
        { duration: '15s', target: 0 },    // respiro visível na baseline
        { duration: '5s', target: 180 },   // pico 2 - ainda mais brusco que o 1
        { duration: '25s', target: 180 },  // sustenta pico 2
        { duration: '10s', target: 10 },   // quase-vale (não zera, pra variar o formato do gráfico)
        { duration: '15s', target: 10 },
        { duration: '8s', target: 220 },   // pico 3 - o mais extremo, deve saturar HikariCP e abrir o circuit breaker
        { duration: '20s', target: 220 },  // sustenta pico 3
        { duration: '20s', target: 0 },    // ramp-down final
      ],
      gracefulRampDown: '5s',
      gracefulStop: '5s',
    },
  },
  // Sem thresholds de verdade de propósito: este script existe pra ESTOURAR limites (pool
  // esgotado, circuit breaker abrindo, fila crescendo, GC suando) na frente da turma, não pra
  // validar SLA. "errors" só fica registrado pra aparecer no dashboard 05-k6-load-test.json, sem
  // fazer o k6 sair com código de erro por causa disso.
  thresholds: {
    errors: ['rate<1'],
  },
};

function hit(name, url, params) {
  const mergedParams = Object.assign({}, params, {
    tags: Object.assign({ name }, params && params.tags),
  });
  const res = http.get(url, mergedParams);
  const ok = check(res, { [`${name} -> 2xx/3xx`]: (r) => r.status >= 200 && r.status < 400 });
  errorRate.add(!ok);
  return res;
}

function post(name, url, body, params) {
  const mergedParams = Object.assign({}, params, {
    tags: Object.assign({ name }, params && params.tags),
  });
  const res = http.post(url, body, mergedParams);
  const ok = check(res, { [`${name} -> 2xx/3xx`]: (r) => r.status >= 200 && r.status < 400 });
  errorRate.add(!ok);
  return res;
}

export function setup() {
  const username = `spikedemo_${Date.now()}`;
  const password = 'LoadTest#12345';
  const headers = { headers: { 'Content-Type': 'application/json' } };

  http.post(`${BASE_URL}/api/auth/register`, JSON.stringify({ username, password }), headers);
  const loginRes = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ username, password }), headers);
  check(loginRes, { 'login retornou 200': (r) => r.status === 200 });
  const token = loginRes.json('accessToken');
  const authHeaders = { headers: { Authorization: `Bearer ${token}` } };

  const idsRes = http.get(`${BASE_URL}/api/sensors/ids`);
  const sensorIds = idsRes.status === 200 ? idsRes.json() : [];

  const pontosRes = http.get(`${BASE_URL}/api/pontos`, authHeaders);
  const slugs = pontosRes.status === 200 ? pontosRes.json().map((p) => p.id_ponto).filter(Boolean) : [];

  return { token, sensorIds, slugs };
}

export default function (data) {
  const authHeaders = { headers: { Authorization: `Bearer ${data.token}` } };
  const coord = COORDS[Math.floor(Math.random() * COORDS.length)];
  const qs = `latitude=${coord.lat}&longitude=${coord.lon}`;

  // Ao contrário do stress-test.js (que isola os endpoints caros em cenários paralelos com
  // poucas VUs fixas), aqui TODA VU bate nos endpoints pesados em toda iteração. É isso que faz
  // o pool do HikariCP (10 conexões) e os threads do Tomcat saturarem de verdade durante os
  // picos de 120/180/220 VUs, em vez de só se aproximar do limite.
  group('leve', () => {
    hit('sensors/latest', `${BASE_URL}/api/sensors/latest`);
    hit('sensors/ids', `${BASE_URL}/api/sensors/ids`);
    hit('weather', `${BASE_URL}/api/weather?${qs}`);
  });

  group('pesado', () => {
    // Sem @Cacheable (TabuaMareServiceImpl) - sob concorrência alta é o que mais rápido abre o
    // circuit breaker Resilience4j "tabuaMare", visível no dashboard 04-coletores-resiliencia.
    hit('tabua-mare/states', `${BASE_URL}/api/tabua-mare/states`, authHeaders);
    hit('marine', `${BASE_URL}/api/marine?${qs}`, authHeaders);
    if (data.slugs.length > 0) {
      const slug = data.slugs[Math.floor(Math.random() * data.slugs.length)];
      hit('export/csv', `${BASE_URL}/api/export/ia-dataset/${slug}/csv?days=30`, {
        headers: authHeaders.headers,
        timeout: '20s',
      });
    }
  });

  // Só ~30% das iterações: se toda VU chamasse a mapi-ai em toda iteração, ela dominaria o
  // gráfico de latência e afogaria os outros endpoints — o objetivo aqui é ela aparecer como
  // mais um pico de erro/latência durante os picos, não como a única história do dashboard.
  if (Math.random() < 0.3) {
    post('flood-predictions', `${BASE_URL}/api/flood-predictions?${qs}`, null, {
      headers: authHeaders.headers,
      timeout: '15s',
    });
  }

  // Sleep curto e quase nulo de propósito: durante os picos o objetivo é MUITAS requisições
  // concorrentes disparadas juntas, não um ritmo suave de usuário real (isso é papel do
  // stress-test.js).
  sleep(Math.random() * 0.3);
}
