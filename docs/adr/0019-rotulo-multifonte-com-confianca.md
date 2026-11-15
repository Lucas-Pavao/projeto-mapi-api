# 0019 — Rótulo de alagamento multifonte, com precisão e confiança declaradas

- Status: aprovado (2026-11-08, Lucas)
- Data: 2026-11-08
- Decisores: Claude (autor), Lucas
- Tarefa: F6-DADOS-01

## Contexto

O rótulo vem de uma fonte só (Defesa Civil no CKAN), com precisão de dia: 225 ocorrências, 18 dias, 3 ponto-dia a 500 m, 0 com hora (`docs/REVISAO-INGESTAO-2026-11.md`, seção 4). As outras fontes públicas investigadas também não têm hora (156 da Emlurb, com `DATA_DEMANDA` sem hora e sem tipo "alagamento") ou são recortes sem série (Sedec tempo real, 173 registros). Com hora, só os pedidos 05 (Defesa Civil, LAI) e 07 (Bombeiros). O filtro atual aceita só a ocorrência "Alagamentos"; "Imóveis Alagados" leva de 18 para cerca de 49 dias, mas mistura inundação e usa a data da vistoria.

## Opções consideradas

### A — Continuar com uma fonte e um filtro
A favor: nada muda no que foi pré-registrado. Contra: o conjunto não cresce; não há onde guardar hora quando ela chegar.

### B — Tabela por fonte, sem deduplicação
A favor: simples. Contra: o mesmo alagamento contado duas vezes infla o positivo.

### C — Modelo multifonte com precisão, confiança e deduplicação entre fontes
A favor: cresce sem perder auditoria; a hora entra quando existir; cada análise escolhe a confiança mínima. Contra: migração de schema; regra de deduplicação a manter.

## Decisão

Opção C.

1. `flood_occurrence` ganha `source` (CKAN_RECIFE, EMLURB_156, DEFESA_CIVIL_LAI, BOMBEIROS_PE, IMPRENSA, OUTRA), `spatial_precision` (MUNICIPIO, BAIRRO, LOGRADOURO, COORDENADA), `temporal_precision` passa a aceitar MINUTE além de DAY e HOUR, `confidence` (ALTA, MEDIA, BAIXA), `event_type` (ALAGAMENTO, ENXURRADA, INUNDACAO; código Cobrade quando houver) e `origin_id` (identificador na fonte). `run_id` continua sendo a proveniência.
2. Confiança calculada por regra fixa, não à mão: ALTA = fonte oficial com hora e logradouro ou coordenada; MÉDIA = fonte oficial com dia e logradouro; BAIXA = bairro ou município, imprensa, ou indício indireto (156 de drenagem).
3. `occurrence_cluster` agrupa registros de fontes diferentes do mesmo dia local, a até 200 m `[sem fonte]` um do outro; o grupo herda a melhor precisão temporal e espacial e guarda os membros.
4. `verified_negative` (ponto, dia local, origem, confiança) guarda negativos verificados, separados do "dia sem registro".
5. O filtro amplo da Defesa Civil ("Imóveis Alagados") entra como `event_type = ALAGAMENTO` com confiança MÉDIA **só se o Lucas aprovar**; a decisão de 2026-10-10 (só "alagamento") fica valendo até lá.
6. Toda fonte nova entra pelo pipeline (bruto com hash, fuso declarado, quarentena), nunca direto na tabela consolidada.

> **Aviso.** Nada ingerido como rótulo pode realimentar a definição de evento, o limiar de classificação ou a capacidade da galeria (ADR 0006, ADR 0017).

## Consequências

- Melhor: a hora e o logradouro, quando chegarem, entram sem migração nova; o tamanho da amostra por confiança fica explícito.
- Pior: migração do schema (próxima V livre) e reprocessamento do CKAN; os arquivos congelados da F1 não mudam, e qualquer número da F1 que a recarga alterar vira divergência registrada no PROGRESSO.
- Medida: dias com alagamento por confiança e por ponto a 500 m, antes e depois.

## Condições que reabririam a decisão

- Uma fonte com volume que justifique outra regra de deduplicação (por exemplo, chamados do 156 com tipo "alagamento").
- Banca ou orientador pedirem outra escala de confiança.
