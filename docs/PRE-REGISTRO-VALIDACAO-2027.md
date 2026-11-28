# Pré-registro — validação do alerta na quadra chuvosa de 2027 (onda 16, Fase 9)

- Data: 2026-11-23, revisto em 2026-11-28 · Status: **proposta**. Os números marcados com [fechar] são fechados pelo Lucas com o orientador antes do
  congelamento. Nada aqui foi escolhido olhando rótulo de 2027 (ainda não existe) nem o armazenamento lacrado.
- O que é validado: o alerta único (`flood_reg_v2`, ADR 0030; capacidade em faixa e pico previsto, Fase 4), como está no ar em
  modo sombra, e as comparações da seção 5.

## 1. Período e congelamento

- Período: 15/03/2027 a 31/08/2027 (quadra chuvosa do Recife, março a agosto) [fechar].
- Congelamento: tag git **assinada** em cada repositório antes de 15/03/2027 (`validacao-2027-congelada`), com o commit de cada
  serviço igual ao que o `/health`, o `/actuator/info` e o `version.json` mostram. Procedimento em `docs/CONGELAMENTO-2027.md`.
- Durante a validação nada muda no modelo, nos parâmetros nem na regra. Correção de bug que muda o nível invalida o período a
  partir dela (o relatório separa antes e depois).

## 2. Definição de evento

- **Evento observado:** alagamento no ponto, com hora, a até **500 m** do ponto e com tolerância de **± 2 h** em relação à
  janela do alerta [fechar].
- **Alerta emitido:** o ponto em ALERTA (ou ALERTA ou ATENÇÃO, relatado separado) em algum ciclo de 10 min; episódios
  separados por pelo menos 3 h sem alerta são episódios diferentes.
- **Acerto:** evento observado com alerta ligado em algum instante de (início − 6 h, início + 2 h).
- **Antecedência:** início do evento menos o primeiro instante do episódio de alerta que o acertou.
- **Falso alarme:** episódio de alerta sem evento observado a 500 m entre o início do episódio − 2 h e o fim + 2 h.

## 3. Fontes de rótulo (todas no armazenamento lacrado até o fim do período)

| Fonte | Como entra |
|---|---|
| Dados Vivos da SEDEC | **parados desde 2023** (Fase 7.4); entram se voltarem a atualizar, com a hora da primeira aparição |
| Relatos confirmados no app (Minha rua) | com hora e coordenada; confirmação por regra escrita [fechar] |
| Respostas dos pedidos 05 (Defesa Civil) e 07 (Bombeiros) | se chegarem antes do fim da análise |
| CTTU (interdição por alagamento) | se houver fonte com hora; o inventário do ESIG não achou camada de alagamento |
| Imprensa | só com lugar e hora citados; com controles negativos por regra |

**Controles negativos por regra:** todo ponto-dia do período com 15 a 40 mm de chuva medida no ponto e nenhuma ocorrência a
500 m em nenhuma fonte. Nenhum escolhido a dedo.

## 4. Métricas

POD, FAR, CSI, antecedência mediana, falsos por ponto-mês e escore de Brier (para os índices probabilísticos), por ponto e no
total. **Todo intervalo de proporção pelo método exato de Clopper–Pearson** (IC 95%); antecedência com IC por bootstrap de evento.

## 5. Linhas de base e comparações

1. "Nunca alerta".
2. "Chuva medida em 6 h ≥ 25 mm".
3. Persistência (alerta enquanto choveu ≥ 5 mm na última hora no ponto) [fechar o limiar].
4. Índices da Fase 5 (logística e EBM, ADR 0031), no mesmo limiar de marcação da regra de 25 mm.
5. Variantes do alerta único, só para entender (não decidem o sucesso): sem refluxo; ATENÇÃO com uma só ponta pessimista
   (q95 com Q_max e q50 com Q_min), como proposto no relatório da Fase 4.4.

## 6. Critério de sucesso (escrito antes)

O alerta único (ALERTA) é considerado útil se, no total dos pontos com terreno OK, **POD ≥ 0,7 com FAR ≤ 0,5** e o limite
inferior do IC de Clopper–Pearson do POD for maior que o POD da linha de base "6 h ≥ 25 mm" [fechar os três números].
Sem eventos suficientes (menos de 10 eventos observados no total), o resultado é "inconclusivo", nunca "sucesso".

## 7. Exclusões e quebras de série

- Regra 1 do prompt: os 18 dias da Defesa Civil (2021–2024) e as datas de posicionamento (06/02/2023, 15/05/2025, 04/08/2026)
  não entram (são de antes do período, de qualquer forma).
- **Open-Meteo:** quebra de série em 30/11/2026 (`icon_global` sai; V43); o período é todo depois dela.
- **Obra da Mascarenhas** (reservatório ProMorar da Rua Muritiba): se a operação começar dentro do período, Mascarenhas é
  avaliado em dois pedaços (`mapi_ai/obras.py`; `api_context.obra_periodo` em cada previsão gravada); nunca juntos.
- Derby: terreno em revisão; entra só nas linhas de base.
- Ciclos com SEM_DADO: contados à parte, por motivo; nunca viram "sem alerta".
- **Buracos de operação** (pilha parada, ciclo faltando): o tempo sem ciclo não conta como "sem alerta" nem como falso
  alarme; um evento observado num buraco entra como "não avaliado" e é relatado à parte. Medido pelo relatório semanal
  (seção 9). Já aconteceu: de ~24/11 01h a 28/11 12h UTC de 2026 a pilha ficou parada.
- **Satélite:** o RRQPE legado do GOES foi aposentado em 02/11/2026 (ADR 0033); o rain_v2 não está no ar e não entra.

## 8. Intervalo da chuva (rain_v1)

Feito em 28/11 (pré-registro `docs/PRE-REGISTRO-CQR-RAIN-V1.md`; resultado em `projeto-mapi-ai/reports/cqr/RELATORIO.md`).
O ajuste conforme não muda nada (Q ≈ 0 mm), porque o q95 já cobre 96% a 97% na média. Com chuva já caindo no ponto, cobre
93% a 94%; nas horas em que a chuva forte veio depois, 19% a 41%. **Limitação declarada para 2027:** a faixa pessimista da
chuva (e portanto a ATENÇÃO) fica curta exatamente nos eventos que importam. O q95 servido não muda (modelo congelado); CQR
condicionado ao regime fica como proposta para depois do período.

## 9. Relatório semanal do modo sombra

Toda segunda, automático (`shadow_weekly_report`, V49; `GET /api/alagamento/relatorio-semanal`, ADMIN): por ponto, ciclos
feitos contra esperados, maior buraco sem ciclo, ciclos por nível, SEM_DADO por motivo e episódios de ALERTA e de ALERTA ou
ATENÇÃO (episódios separados por pelo menos 3 h sem alerta, como na seção 2). A comparação com os rótulos lacrados só depois
do fim do período (`docs/CONGELAMENTO-2027.md`); até lá o campo `rotulos` diz "LACRADOS".
