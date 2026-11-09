# 0022 — Chuva do ponto: maior valor entre as estações válidas, com checagem espacial mensal

- Status: aprovado (2026-11-09, Lucas: decisão 3 da etapa 2, regra por extenso)
- Data: 2026-11-09
- Decisores: Claude (autor), Lucas
- Tarefa: F6-DADOS-02
- Altera: ADR 0018 (só a escolha dentro do raio; o raio e a origem declarada continuam)

## Contexto

O ADR 0018 escolheu "a estação mais próxima dentro de 5 km". Em maio/2022 a mais próxima do Derby (Torreão, a 2,07 km) marcou 0,0 mm, enquanto as vizinhas marcaram cerca de 700 mm. Cavaleiro marcou 65 mm. Imbiribeira e Jaboatão Centro marcaram cerca de metade das vizinhas (`docs/extratos/cemaden-ped.md`). "A mais próxima" sozinha leva a estação quebrada para a série do ponto.

## Opções consideradas

### A — Mais próxima (ADR 0018)
Contra: herda a falha da estação, como no Derby.

### B — Mais próxima entre as aprovadas na checagem
A favor: uma estação por vez, sem viés de seleção. Contra: a chuva convectiva cai em núcleos de poucos km; a mais próxima aprovada pode estar fora do núcleo.

### C — Maior valor entre as aprovadas
A favor: é a política do sistema para a chuva atual; leitura conservadora para risco de alagamento. Contra: o máximo de várias estações tende a ser maior que a chuva de qualquer uma, então a contagem de eventos fortes sobe (o argumento do ADR 0018 contra o máximo). Uma estação que exagera domina a série; por isso o excesso também reprova na checagem.

### D — Média das aprovadas
Contra: suaviza o pico, o defeito da reanálise.

## Decisão

Opção C. Dentro do raio do vínculo (5 km), descartar as estações reprovadas na checagem espacial do mês e usar o maior valor horário entre as válidas; empate fica com a mais próxima. A opção B entra como sensibilidade no relatório.

**Checagem espacial** (`StationMonthChecker`), por estação e mês civil UTC. Compara a estação com cada vizinha a até 10 km nas horas em que as duas têm valor. A vizinha só conta se somar pelo menos 20 mm nessas horas.

| Defeito | Motivo | Critério |
|---|---|---|
| Cobertura parcial | `PARTIAL_COVERAGE` | blocos aceitos cobrem menos de 80% do tempo do mês |
| Pluviômetro travado | `STUCK_ZERO` | mediana, entre as vizinhas, da soma da estação nas horas em comum abaixo de 1 mm |
| Entupimento | `UNDERCATCH` | mediana da razão estação/vizinha abaixo de 0,5 |
| Excesso | `OVERCATCH` | mediana da razão acima de 2,0 |

Qualquer motivo reprova o mês (`FAIL`). Com menos de 2 vizinhas com chuva suficiente, a estação fica `UNCHECKED` e é usada, com o motivo `FEW_WET_NEIGHBORS`. Sem nenhuma hora com valor no mês, fica `OFFLINE`.

Por que cobertura de **bloco** e não de hora: na V22 o bloco que falta numa hora com leitura conta zero, então a estação que perde blocos tem toda hora preenchida e volume baixo. Imbiribeira em maio/2022 teve hora preenchida e metade do volume.

Todos os limiares são `[sem fonte]` como critério físico: escolhidos antes de olhar o rótulo de alagamento, gravados em cada linha de `station_month_check.metrics.rules` e sujeitos a sensibilidade.

**Três exigências**:

1. A checagem roda mês a mês (`station_month_check`, uma linha por estação e mês).
2. Cada hora da série do ponto (`point_rain_hourly`) declara a estação que forneceu o valor, a distância, quantas candidatas havia no raio, quantas estavam válidas e quantas tinham valor.
3. A estação reprovada não é apagada: a linha fica com status e motivos.

O vínculo estação↔ponto vai para `point_station_link` (`method = MAX_VALID`, `linkage_config` `MAX_VALID_MONTHLY`), com `valid_from`/`valid_to` = primeira e última hora com valor da estação nos dados (o cadastro não informa desativação).

## Consequências

- Melhor: estação quebrada sai do mês em que quebrou, com motivo; a série do ponto é auditável hora a hora.
- Pior: o máximo eleva o pico e a contagem de eventos fortes em relação a uma estação só; a comparação com a ANA e a reanálise (uma série por ponto) não é de mesma natureza. Por isso o relatório traz a opção B ao lado.
- Muda: a tabela de eventos ganha a fonte `CEMADEN_PED`, que lê `point_rain_hourly`; `stationId`/`stationDistanceM` do evento = estação da hora de pico; `rainOrigin = MEASURED_MAX_VALID`.

## Condições que reabririam a decisão

- Sensibilidade mostrar que o máximo muda a conclusão (eventos acima da capacidade) e a opção B não.
- Densidade de estações suficiente para interpolação com validação cruzada.
- Rótulo de alagamento com hora (hoje 0 dias com hora e ponto) permitir comparar as opções contra o desfecho.
