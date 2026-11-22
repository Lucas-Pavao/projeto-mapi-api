# 0029 — Tela por horizonte: INMET e MAPi lado a lado, sem combinar

- Status: proposto (implementado na branch `feat/onda15-horizontes`, 2026-11-22)
- Data: 2026-11-22
- Decisores: Lucas, agente de implementação
- Tarefa: prompt dos horizontes, blocos A a D (onda 15)

## Contexto

O `rain_v1` avisa chuva forte cerca de 20 min antes, a partir dos pluviômetros (ADRs 0026 a 0028). Faltava dizer de
manhã que o dia é de risco. Para isso existem a previsão oficial do INMET por município, os avisos do INMET (ADR 0024)
e os modelos numéricos servidos pelo Open-Meteo.

Três achados de 22/11/2026 definem o que cabe na tela:

1. A previsão do INMET por município não traz quantidade de chuva (nem mm, nem probabilidade) e não traz hora de
   emissão. Só texto, ícone, temperatura, umidade e vento (`docs/extratos/inmet-previsao-municipio.md`).
2. O "dia de risco" do MAPi, feito com o histórico de rodadas do ECMWF, do ICON e do modelo atual, **não passou** no
   critério escrito antes. Em H = 12 h às 06h, o melhor modelo na validação (ICON, BSS 0,071) teve IC 95% de −0,081 a
   0,260, que contém o zero. Pela regra, fica a climatologia (`projeto-mapi-ai/reports/day_risk/RELATORIO.md`).
3. O teste tinha 28 eventos FORTE em 14 dias. Os intervalos são largos.

## Decisão

1. **Três camadas, sempre separadas.** `GET /api/horizontes/{slug}` devolve `official` (INMET), `dayRisk` (MAPi) e
   `nextHour` (MAPi), cada uma com origem, idade e motivo quando falta. Nenhum número do INMET é reescrito, somado ou
   posto na mesma frase que um número do MAPi.
2. **INMET como INMET.** Na tela, o aviso aparece com a cor e o texto do INMET. A previsão do turno atual e do
   próximo leva a etiqueta "previsão oficial do INMET" e "visto às HHhMM". Nunca usa as cores de risco do MAPi.
   "Visto às" é o `first_seen_at` (o primeiro instante em que o MAPi viu a versão), declarado como tal.
3. **Dia de risco fora da tela enquanto não passar no critério.** A camada vem `available: false`, com motivo
   `NAO_VALIDADO`. No front, ela não aparece no modo enxuto. No modo Detalhes, aparece "Dia de risco: não validado" com
   o motivo. O critério de adoção é o do pré-registro (`mapi_ai/dayrisk/config.py` @ `3d9e5af`): BSS contra a
   climatologia com IC 95% sem o zero, em H = 12 h. Para ligar, é preciso uma nova avaliação que passe e o serviço
   `POST /v1/forecast/day-risk`. A chave `app.horizontes.dia-de-risco-validado` sozinha não liga nada.
4. **Próxima hora é o alerta por ponto, como já está.** Em modo sombra, quem não é administrador vê "em teste", sem
   nível. Sem estado do ponto, a camada diz `SEM_DADO` com motivo, nunca "sem alerta".
5. **Minha rua (pública):** só a linha do INMET (`/api/public/previsao-oficial/{municipio}` e
   `/api/public/avisos-oficiais/{municipio}`) e o estado do alerta que a página já mostra. O dia de risco fica para
   quem está logado.
6. **Arquivar para avaliar depois.** Cada versão da previsão do INMET vira uma linha (V41). As rodadas dos modelos do
   Open-Meteo, com o instante de publicação, são gravadas a cada hora (V42). Com uma estação chuvosa arquivada, a
   consulta `docs/extratos/sql/inmet-previsao-x-chuva-medida.sql` mede se o texto e os avisos do INMET acompanharam
   os eventos FORTE.

## Consequências

- **Melhor:** a tela mostra o que existe de oficial para o dia, sem fingir que o MAPi tem um "dia de risco" validado.
  O arquivo das previsões e das rodadas permite reavaliar sem depender de histórico de terceiros.
- **Pior:** o horizonte do dia fica só com texto do INMET, sem número. O bloco "Hoje" acrescenta uma consulta por card
  aberto e uma por minuto no painel.
- O ICON Global, o melhor modelo na validação, é descontinuado pelo Open-Meteo em 30/11/2026. Uma nova avaliação vai
  precisar de outro modelo.

## Condições que reabririam a decisão

- Uma avaliação nova, com critério escrito antes, em que o dia de risco vença a climatologia com IC sem o zero.
- O INMET passar a publicar quantidade de chuva ou hora de emissão na previsão por município.
- Uma estação chuvosa arquivada mostrar que o texto ou os avisos do INMET acompanham (ou não) os eventos FORTE.

## Emenda 1 (2026-11-22) — saída do ICON Global e quebra de série

- A documentação do Open-Meteo (https://open-meteo.com/en/docs/dwd-api, lida em 2026-11-22) marca o ICON Global, o EU e
  o D2 como descontinuados em 30/11/2026: "dwd_icon_global, dwd_icon_eu and dwd_icon_d2 will use native grids". A página
  não diz qual nome usar depois.
- Conferido na própria API em 2026-11-22:
  - `dwd_icon_global_native` responde, tem `meta.json` próprio (instante da rodada) e célula centrada em −8,0206; −34,9728,
    mas o histórico de rodadas só começa em 2026-09-22;
  - `icon_seamless` responde e tem histórico de rodadas desde 2024-01-19.
- Decisão: coleta ao vivo com `dwd_icon_global_native`; histórico com `icon_seamless`. Sai o `icon_global` do coletor.
- Quebra de série: 2026-11-30 registrada em `source_series_break` (V43) para o `best_match` (as 3 entradas do Open-Meteo do
  rain_v1 vêm dele) e para o `icon_seamless`. Toda avaliação separa o antes e o depois. A conferência semanal do rain_v1
  (V44, `docs/PRE-REGISTRO-CONFERENCIA-SEMANAL-RAIN.md`) já faz isso.
- O dia de risco (bloco C) foi avaliado com o `icon_global` até 31/10/2026 e não passou no critério. A avaliação não
  muda; uma nova teria de usar o `icon_seamless` e respeitar a quebra.
