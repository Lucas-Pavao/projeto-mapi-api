-- F2-DADOS-07: resultado da verificação de fuso (docs/revisao/fase-2/F2-DADOS-07.md). SÓ DADO DE CONFIGURAÇÃO:
-- atualiza linhas existentes de source_registry; nenhuma tabela é criada ou alterada e nenhuma observação é tocada.
-- O bruto que estava em quarentena TZ_UNVERIFIED sai por REVALIDATE (ADR 0006 seção 3), nunca por UPDATE à mão.

-- CEMADEN (endpoint /cemaden/ da APAC): APROVADO pelo teste B (relógio) sobre 328 leituras de 47 estações coletadas
-- ao vivo: com rótulo = UTC-3, mediana do atraso de publicação 3,8 min e p01 +1,5 min; as outras 12 hipóteses de
-- -6 a +6 h reprovam (nenhuma leitura do futuro só vale para h >= -3; mediana <= 30 min só vale para h <= -3).
UPDATE source_registry
   SET source_tz = 'America/Recife',
       verification_state = 'VERIFIED',
       note = 'F2-DADOS-07 (2026-10-10): teste B, 328 leituras LIVE de 47 estações, h=-3 única hipótese viável (mediana do atraso 3,8 min). '
              'Verificado só para a janela observada (outubro/2026): mudança de fuso passada não é testável (o endpoint só devolve ~24 h).',
       updated_at = now()
 WHERE source = 'CEMADEN';

-- ANA_SOAP: NÃO aprovada. Teste A (chuva x Open-Meteo, 4 estações, 2021-10..2026-10): nenhuma estação passou no critério
-- pré-fixado (correlação máxima 0,25-0,33; argmax em -4 h nas quatro; margem sobre -3 h <= 0,009). h=0 (UTC) foi
-- rejeitado em todas as quatro (IC 95% disjunto do máximo), mas -3 não se separa de -4 pela chuva. Permanece em quarentena.
UPDATE source_registry
   SET note = 'F2-DADOS-07 (2026-10-10): NÃO verificada. Teste A: 0 de 4 estações aprovadas (spearman máx 0,25-0,33; argmax -4 h; margem <= 0,009); '
              'UTC (h=0) rejeitado nas 4, -3 vs -4 indistinguíveis pela chuva. Teste C pós-hoc (relógio): 7 estações, abaixo do poder mínimo. '
              'Hidro-WebService (ao vivo, com credencial) não testado.',
       updated_at = now()
 WHERE source = 'ANA_SOAP';

-- APAC_METEO24H: NÃO aprovada por falta de poder. O endpoint /meteorologia24h/ devolveu, em 2026-10-10, uma única
-- estação fluviométrica (Jaboatão); teste B com 11 leituras de 2 estações (mínimo 200 e 20), todas coerentes com -3 h.
UPDATE source_registry
   SET note = 'F2-DADOS-07 (2026-10-10): NÃO verificada por falta de poder (teste B: 11 leituras LIVE de 2 estações; mínimo 200 e 20). '
              'As poucas leituras são coerentes com h=-3, sem valor de decisão. Reavaliar quando o endpoint publicar mais estações.',
       updated_at = now()
 WHERE source = 'APAC_METEO24H';
