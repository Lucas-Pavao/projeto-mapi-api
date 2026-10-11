# Handoff — Fase 5: revisão crítica, prontidão acadêmica e fechamento (2026-10-11)

**Feito:**
- Revisão crítica independente (`docs/REVISAO-CRITICA.md`): 25 contestações, das quais 5 críticas. As
  críticas foram tratadas: dump pós-carga, push de tudo, correção da narrativa RC-04, opção G no MEMO,
  codeVersion pelo build, ArchUnit e selo de previsão experimental.
- Prontidão acadêmica (`docs/PRONTIDAO-ACADEMICA.md`): sustenta a dissertação no enquadramento G.
  Reprodução por comando único (`avaliacao/reproduzir.sh`) com hashes idênticos.
- Consolidação: `docs/RELATORIO-REVISAO.md`, `docs/ROADMAP-REVISAO.md`, `docs/arquitetura/` e
  `docs/PENDENCIAS-DE-INFORMACAO.md`.
- Tudo fundido na `main` dos 4 repositórios:
  - api: #21–#50
  - ia: #5–#9
  - front: #14–#15
  - projeto-mapi-sensores: #12

**Estado da main:**

| Repositório | Verificação |
|---|---|
| api | 240 testes, 0 falhas; migrações V1–V15 aditivas |
| ia | 8 testes de inferência; reprodução congelada idêntica (27 testes) |
| front | 89 testes; build ok |

**Para testar localmente:**
1. `cd projeto-mapi-api && docker compose up -d postgres mapi-ai mapi-api mapi-front`.
2. Na primeira vez, defina `GIT_COMMIT=$(git rev-parse HEAD)` antes do build da API.
3. Para a camada de terreno, o `.pmtiles` precisa estar em `projeto-mapi-front/public/terrain/`. Ele já está lá nesta máquina.

**Abertos:** ver `docs/PENDENCIAS-DE-INFORMACAO.md`.
- Pergunta 31 (cópia dos dumps fora da máquina): **urgente**.
- 6 pedidos institucionais (`docs/pedidos/01–06`), a enviar pelo autor.
- Teste da camada de terreno em celular real.
- Leitura do orientador sobre os pré-registros e o MEMO.

**Riscos de ambiente:** os discos `/` e `/home` estão quase cheios (87–96%). Reconstruir a imagem da IA
(TensorFlow) estourou o disco do Docker uma vez.
