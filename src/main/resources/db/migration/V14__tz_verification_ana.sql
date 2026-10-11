-- F2-DADOS-10: resultado do reteste de fuso da ANA_SOAP (docs/revisao/fase-2/F2-DADOS-10.md; critério pré-fixado em
-- F2-DADOS-10-criterio.md, commit 283f6b7). SÓ DADO DE CONFIGURAÇÃO: atualiza uma linha de source_registry; nenhuma
-- tabela é criada ou alterada e nenhuma observação é tocada. O bruto em quarentena TZ_UNVERIFIED sai por REVALIDATE
-- (ADR 0006 seção 3), nunca por UPDATE à mão.
--
-- Braço 1 (relógio, leituras ao vivo coletadas depois do critério): 33 leituras novas de 12 estações; h=-3 é a única
-- hipótese viável (p01 do atraso +31,9 min; h=-4 lê o futuro: p01 -28,1 min). Braço 2 (constância 2021-2026 por ano):
-- 19 de 21 anos-estação conformes (UTC rejeitado, -3 a menos de 0,03 do máximo). Verificado para o serviço legado
-- (ServiceANA.asmx); o Hidro-WebService (ao vivo, com credencial) não foi testado e compartilha a chave ANA_SOAP.
UPDATE source_registry
   SET source_tz = 'America/Recife',
       verification_state = 'VERIFIED',
       note = 'F2-DADOS-10 (2026-10-11): reteste aprovado em 2 braços. Relógio ao vivo (serviço legado): h=-3 única viável '
              '(33 leituras novas, 12 estações; h=-4 lê o futuro). Constância por ano 2021-2026: 19/21 anos-estação conformes. '
              'Vale para ServiceANA.asmx; Hidro-WebService (credencial) não testado. Nível do legado em cm convertido para m no adaptador.',
       updated_at = now()
 WHERE source = 'ANA_SOAP';
