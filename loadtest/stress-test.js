import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// BASE_URL aponta pra dentro da rede do docker-compose por padrão (serviço "mapi-api"), já que
// este script roda no container k6 do próprio compose. Pra rodar da máquina host contra a API
// publicada, passe -e BASE_URL=http://localhost:8080.
const BASE_URL = __ENV.BASE_URL || 'http://mapi-api:8080';

// Coordenadas fixas (Recife/Olinda) de propósito: WeatherService/MarineService fazem
// @Cacheable com chave arredondada em 2 casas decimais, então reusar sempre o mesmo pool
// pequeno faz a maioria das chamadas ser servida do cache local em vez de bater na Open-Meteo
// a cada request — evita gerar carga real (e possível rate limit) numa API de terceiros.
const COORDS = [
  { lat: -8.05, lon: -34.90 },
  { lat: -8.04, lon: -34.87 },
  { lat: -7.99, lon: -34.85 },
];

const errorRate = new Rate('errors');

export const options = {
  scenarios: {
    ramping_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '1m', target: 30 },
        { duration: '2m', target: 30 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '15s',
    },
  },
  thresholds: {
    // p95 alto de propósito: weather/marine batem na Open-Meteo (externa) quando o cache
    // @Cacheable ainda está frio pra aquele par de coordenadas — 1-2s de round-trip externo é
    // normal aqui, não indica problema na mapi-api. Ajuste pra baixo se cortar esses endpoints.
    http_req_duration: ['p(95)<2500'],
    errors: ['rate<0.05'],
  },
};

function hit(name, url, params) {
  // tags.name fixo em vez da URL crua: sem isso, cada querystring diferente (lat/lon, sensorId)
  // vira uma série própria no Prometheus — explode cardinalidade e quebra o agrupamento por
  // endpoint nos painéis do dashboard 05-k6-load-test.json.
  const mergedParams = Object.assign({}, params, {
    tags: Object.assign({ name }, params && params.tags),
  });
  const res = http.get(url, mergedParams);
  const ok = check(res, { [`${name} -> 2xx/3xx`]: (r) => r.status >= 200 && r.status < 400 });
  errorRate.add(!ok);
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

  const idsRes = http.get(`${BASE_URL}/api/sensors/ids`);
  const sensorIds = idsRes.status === 200 ? idsRes.json() : [];

  return { token, sensorIds };
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
