# Fase 2 — o que APAC, Cemaden e Open-Meteo oferecem para prever chuva

Data: 2026-11-16. Só investigação: nenhum adaptador foi escrito. DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC.

Scripts da medida (fora do repositório, reproduzíveis com os mesmos arquivos de entrada): `lag2.py` (defasagem), `lag.py` (persistência), `om.py` (Open-Meteo). Entradas: `observation_current` da PED (1.485.181 leituras de 10 min, 2021-09 a 2026-11, 46 estações da RMR, sem as geotécnicas), `projeto-mapi-ai/data/in1165/eventos_CEMADEN_PED.csv.gz` (eventos do ADR 0017) e `cemaden_point_hourly.csv.gz` (chuva do ponto, regra chuva-ponto).

## Resposta curta

**A rede dá menos de 1 h de antecedência, e nenhum produto de previsão da APAC ou do Cemaden tem histórico acessível por máquina.** A mediana da defasagem entre o início da chuva numa estação vizinha e o início no ponto é de 0 a 10 min em todos os setores. A chuva entra pelo leste, pelo mar, onde não há pluviômetro. A previsão arquivada do Open-Meteo é o único produto com histórico; ela só vence a persistência na janela de 6 h, e mesmo assim por pouco.

## 2.1 Produtos de previsão

| Produto | Legível por máquina? | Resolução | Histórico de 2 anos? | Emissão separada da validade? | Termo de uso |
|---|---|---|---|---|---|
| Avisos meteorológicos da APAC (`apac.pe.gov.br/meteorologia-3/avisos`) | Não: lista HTML, um item por aviso | Mesorregião, 24 h ou mais | Paginação até a página 78 (não conferido item a item); só HTML | A lista mostra só a validade | Não encontrado |
| Previsão do tempo da APAC (`painel.apac.pe.gov.br/previsao`) | Sim, JSON por trás do painel (`api.apac.pe.gov.br/api.php/previsao_mesoregiao`, `previsao_municipio`) | Mesorregião e município, por dia (hoje e amanhã); turno; **texto qualitativo**: "pancadas", "intensidade moderada", "de forma isolada"; mm só no card do município | **Não confirmado.** A rota aceita `data=`. A consulta direta foi bloqueada pela política desta sessão; falta testar com o seu ok | Não visto | Não encontrado |
| Tendência semanal da APAC (`painel.apac.pe.gov.br/tendencia`) | JSON pela mesma API (não lido) | Média de 24 h, um modelo, diária | Não visto | Não visto | Não encontrado |
| Radar da APAC (`apac.pe.gov.br/monitoramento`) | Imagem PNG (CAPPI 1 km), nome com o instante: `barramento2.apac.pe.gov.br/imagens_radar/AAAAMMDDHHMM.cappi1km_trans.png` | A cada 10 min, pixel de ~1 km | **Não**: a de 15/11 existe; as de 12/11 para trás dão 404. O servidor guarda de 1 a 4 dias | O nome traz o instante da imagem | Não encontrado |
| Modelos WRF 10 km e Eta 12 km da APAC (mesma página) | Mapas na página; dados não localizados | 10 a 12 km | Não visto | Não visto | Não encontrado |
| PED do Cemaden (swagger `sws.cemaden.gov.br/PED/api/ui/swagger.json`, relido) | Sim | — | — | — | Termo da PED, já aceito |
| Alertas de risco do Cemaden por município | Não: `www2.cemaden.gov.br/alertas-emitidos` não carrega (laço http/https); `gov.br/cemaden` publica um boletim diário em texto ("Previsão de Riscos Geo-Hidrológicos") | Município, diário | Boletins em página; sem arquivo de dados | Data do boletim | Não encontrado |

As 15 rotas da PED são de cadastro, dados medidos, acumulados, paginação e agendamento. **Nenhuma devolve previsão, radar ou alerta.**

Conclusão de 2.1: **nenhum produto passa** nas cinco perguntas. A previsão da APAC é a única candidata. Para ela sair do "não confirmado", é preciso testar se `data=` devolve dias passados e pedir o termo de uso à APAC. Mesmo que passe, é texto por mesorregião e por dia: serve para exibição, não como entrada de uma janela de 3 h.

## 2.2 A rede como instrumento de previsão

### Estações por anel de distância ao marco zero do Recife

Contagem do feed estadual da APAC (`dados.apac.pe.gov.br:41120/cemaden/`, 16/11, 335 estações, 291 com coordenada). Setor: N, S, L (mar), O.

| Anel | N | S | L | O | Total |
|---|---|---|---|---|---|
| 0–15 km | 9 | 3 | 0 | 13 | 25 |
| 15–40 km | 3 | 2 | 0 | 7 | 12 |
| 40–100 km | 4 | 13 | 0 | 36 | 53 |
| Acima de 100 km | 0 | 3 | 0 | 198 | 201 |

**O leste é o mar: não há estação nenhuma.** Das 335, 283 são pluviométricas; 43 estações da APAC vêm sem coordenada no feed. No feed, o intervalo mediano entre leituras é de 60 min (hora seca em bloco); a série de 10 min está na PED.

### De onde vem a chuva (defasagem por setor)

Para cada um dos 874 eventos MÉDIA e FORTE (ADR 0017), o início da chuva é a primeira leitura com 0,4 mm ou mais em 10 min depois de 2 h secas, na estação do ponto e em cada vizinha da PED. A vizinha só conta se o início dela cair a ±2 h do início no ponto. Defasagem positiva = a vizinha começou antes.

O controle repete o cálculo com o início do ponto deslocado 3 dias (chuva que não é a mesma). Ele mostra quanto da "antecedência" aparece por acaso.

| Setor (em relação ao ponto) | Pares | Começou a ±2 h | Mediana | Começou 30 min antes ou mais | No controle |
|---|---|---|---|---|---|
| L (lado do mar) | 4.575 | 34% | **+10 min** | 13,0% | 4,0% |
| N | 13.390 | 36% | 0 min | 10,2% | 5,2% |
| S | 4.492 | 35% | 0 min | 8,9% | 4,8% |
| O (interior) | 7.259 | 33% | **−20 min** | 6,7% | 4,6% |

- A chuva chega primeiro pelo leste e anda para o interior: as estações a oeste começam, em mediana, 20 min **depois** do ponto.
- Ganho sobre o acaso, no melhor setor (leste): 9 pontos percentuais de eventos com 30 min de aviso.
- "Alguma das ~40 estações começou 30 min antes": 70% dos eventos, contra **38% no controle**. Metade disso é acaso de ter muitas estações.
- Evento FORTE (80): mediana da estação que começou primeiro, 55 min, contra 40 min no controle.

**A antecedência que a rede dá é de 0 a 10 min em mediana.** As estações a leste dos pontos, no próprio Recife, são as que mais ajudam, e só alguns minutos. Estações do interior (40–100 km, oeste) não ajudam para a chuva que vem do mar.

### Persistência (baseline)

Chuva do ponto nas próximas 1, 2 e 3 h, dado o que choveu na hora atual (5 pontos, 2021-09 a 2026-10):

| Agora | Próximas | Horas | Mediana | p80 | p95 | P(≥ 1 mm) | P(≥ 5 mm) |
|---|---|---|---|---|---|---|---|
| Seco | 1 h | 169.990 | 0,00 | 0,00 | 0,40 | 3,3% | 0,5% |
| Seco | 3 h | 169.716 | 0,00 | 0,20 | 2,95 | 10,6% | 2,7% |
| 0–1 mm | 1 h | 29.360 | 0,20 | 0,80 | 3,54 | 18,2% | 2,8% |
| 0–1 mm | 3 h | 29.280 | 0,60 | 3,18 | 10,20 | 41,7% | 12,9% |
| 1–5 mm | 1 h | 15.109 | 0,79 | 2,95 | 7,60 | 45,0% | 9,8% |
| 1–5 mm | 3 h | 15.072 | 2,56 | 8,05 | 19,42 | 68,2% | 32,2% |
| ≥ 5 mm (MÉDIA) | 1 h | 5.008 | 3,34 | 9,00 | 20,20 | 72,6% | 38,0% |
| ≥ 5 mm (MÉDIA) | 3 h | 4.990 | 7,80 | 20,53 | 44,25 | 86,1% | 61,2% |

Continuar uma chuva é previsível; começar uma, não: de hora seca, 2,7% chegam a 5 mm em 3 h.

## 2.3 Open-Meteo, previsão arquivada (não a reanálise)

Fontes:
- `historical-forecast-api.open-meteo.com`: rodadas emendadas, as primeiras horas de cada rodada, ou seja, antecedência de **0 a ~6 h**, sem separar 1, 3 e 6 h; 2022-01 a 2026-10.
- `previous-runs-api.open-meteo.com`: rodada do dia anterior (24 h) e de dois dias antes (48 h); 2024-01 a 2026-10.

Os 5 pontos caem em **3 células da grade** (-8,1195/-34,9530; -8,0492/-34,9231; -8,1195/-35,0300). A chuva medida é a chuva do ponto.

| Janela | Previsão | Correlação | Média medida → prevista (mm) | CSI no limiar do evento | CSI no melhor limiar da previsão |
|---|---|---|---|---|---|
| 1 h (≥ 5,03 mm) | OM curta | 0,28 | 0,48 → 0,18 | 0,03 | 0,12 |
| 1 h | OM 24 h | 0,19 | 0,42 → 0,12 | 0,01 | 0,07 |
| 1 h | **Persistência** | **0,52** | — | **0,24** | **0,24** |
| 3 h (≥ 10 mm) | OM curta | 0,40 | 1,43 → 0,53 | 0,05 | 0,19 |
| 3 h | **Persistência** | **0,46** | — | **0,22** | **0,22** |
| 6 h (≥ 15 mm) | **OM curta** | **0,50** | 2,86 → 1,07 | 0,06 | **0,25** |
| 6 h | OM 24 h | 0,35 | — | 0,01 | 0,17 |
| 6 h | Persistência | 0,40 | — | 0,20 | 0,20 |

- O Open-Meteo subestima a chuva do ponto em cerca de 2,7 vezes: a chuva do ponto é o máximo das estações a até 5 km, e o modelo é a média de uma célula. Com o limiar ajustado (3,3 mm prevendo 15 mm), ele passa a persistência só em 6 h.
- "Curta" não é antecedência de 6 h: mistura 0 a ~6 h. A comparação justa por antecedência (1, 3, 6 h) exige guardar as rodadas daqui para frente, porque o arquivo não separa.

## Recomendação para a Fase 3

1. **Entram:** a rede da PED (vizinhança, com destaque para o setor leste e para a tendência no próprio ponto), a persistência como baseline obrigatório e o Open-Meteo arquivado como fonte `OPEN_METEO_FORECAST`, separada da reanálise, para a janela de 6 h.
2. **Não entram:** avisos e previsão da APAC (sem histórico confirmado, texto por mesorregião), radar (sem histórico) e alertas do Cemaden (sem dado).
3. **Estações fora da RMR:** carregar só o anel de 15–40 km (12 estações). As de 40 km para cima ficam no oeste e começam depois do ponto.
4. **Expectativa:** o ganho virá de prever a continuação e a intensidade de uma chuva que já começou. O início de uma chuva vinda do mar a rede não vê. Antecedência real para evento FORTE: **minutos, não horas.**
5. **Ação que depende de você:** passar a gravar o radar da APAC a cada 10 min (o servidor apaga em poucos dias) e as rodadas do Open-Meteo. Em um ano, isso vira o histórico que hoje falta. Para a previsão da APAC: testar a consulta com `data=` passada e pedir o termo de uso.
