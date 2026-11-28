# 0033 — Satélite (GOES RRQPE) e radar para ver a chuva chegando do mar

- Status: aprovado (aval geral do Lucas de 22/11; ponto G coberto) para coletar e testar; o rain_v2 é treinado só para
  comparação e **não vai para o ar** (a fonte foi aposentada em 02/11/2026; seção "Consequências")
- Data: 2026-11-23
- Decisores: Claude (autor), Lucas
- Tarefa: O16-F6 (onda 16, Fase 6)

## Contexto

O rain_v1 só prevê a continuação de chuva que já começou: antecedência mediana de 20 min para chuva FORTE e nenhum aviso
antes de começar a chover no ponto (`projeto-mapi-ai/reports/v4/rain/RELATORIO.md`). A chuva do Recife chega muito do mar,
onde não há pluviômetro. Duas fontes poderiam ver essa chuva antes: o radar da APAC e a taxa de chuva do GOES-East.

## Opções consideradas

### A — Radar da APAC

O ESIG do Recife publica o radar da APAC como ImageServer georreferenciado (`pgdbesig_esi2_MD_APAC_v2`, WGS84, ~0,0064°, uma
imagem a cada 10 min, janela móvel de 5 h; inventário de 23/11 em `docs/extratos/esig-inventario/inventario.json`). A favor:
resolução boa e georreferência pronta. Contra: as imagens são figuras coloridas (RGBA de 8 bits, 362 cores numa exportação de
teste, com mapa de fundo), sem a tabela de cores em dBZ; sem ela não há Marshall–Palmer. Histórico só de 5 h.

### B — GOES-East ABI RRQPE

Taxa de chuva por satélite, 2 km, disco inteiro a cada 10 min, pública no S3 da NOAA (`noaa-goes16` até 06/04/2025,
`noaa-goes19` depois), ~1,3 a 1,8 MB por arquivo. A favor: histórico desde 2017, gratuito, cobre o mar. Contra: só
infravermelho (fraco para chuva rasa de nuvem quente, comum vinda do mar); histórico de 5 anos = ~271 mil arquivos. Baixar
cada arquivo inteiro daria ~350 GB e ~90 h; lendo só as faixas de bytes da região (seção "Consequências"), ~53 GB e ~5 h.

## Decisão

1. **GOES RRQPE como candidato** a entrada do rain_v2, em 3 caixas sobre o mar a leste do Recife (0–25, 25–60 e 60–120 km;
   fração de pixels com > 1 mm/h e percentil 90), extraídas por `projeto-mapi-ai/mapi_ai/satelite/rrqpe.py` (baixa, lê só a
   janela, descarta). O rain_v2 segue o protocolo do rain_v1: mesmos cortes de treino, validação e teste, mesma escolha por
   validação, SHAP para dizer se ajuda; relatado mesmo se não ajudar.
2. **Radar da APAC parado** até a tabela de cores em dBZ (pergunta 4 do pedido 10 à APAC). Sem ela, converter cor em chuva
   seria inventar número.
3. pySTEPS fica fora desta onda (opcional no prompt; depende do radar em dBZ).

## Consequências

- **Histórico por faixa de bytes (emenda de 2026-11-28).** Em 23/11 o histórico não foi disparado: baixar cada arquivo
  inteiro daria ~350 GB e ~90 h. O extrator passou a ler o arquivo por pedidos HTTP Range de 16 KB, numa conexão mantida
  aberta, e o h5py lê só o metadado e os pedaços de 226 × 226 pixels que cobrem a região: ~196 KB e ~12 pedidos por
  arquivo, 10 s por dia com 24 processos (fonte: rodada de 30/04 a 02/05/2026, 432 varreduras, 84,8 MB, 30 s). O resultado
  é **idêntico** ao do piloto que baixou os arquivos inteiros (432 de 432 linhas iguais). Histórico de 31/08/2021 a
  31/10/2026 disparado em 2026-11-28; nada fica em disco além do CSV das caixas.
- O treino do rain_v1 usa as três fases de 10 min (cada ponto cai numa: Derby em :10/:40, Ibura e Jaboatão em :00/:30, CIn
  e Mascarenhas em :20/:50), então todas as imagens do período entram.
- **O RRQPE legado foi aposentado.** A NESDIS anunciou em 01/09/2026 a aposentadoria do produto `ABI-L2-RRQPEF`, com
  impacto a partir de 02/11/2026 00h UTC
  (https://www.nesdis.noaa.gov/news/retirement-of-goes-r-rainfall-ratequantitative-precipitation-estimation-grrqpe,
  acesso em 2026-11-28). No bucket `noaa-goes19`, a última pasta é a das 16h UTC de 02/11/2026 (dia 306), e os outros
  produtos do ABI continuam saindo (listagem de 2026-11-28; o "dia 320" escrito em 23/11 estava errado). O substituto
  (Enterprise Rainfall Rate, `ABI-L2-FLDK-RRQPE-AWIPS`) sai pelo PDA da NOAA, que exige cadastro, e não está no bucket
  público. **Sem coletor ao vivo.** O rain_v2 responde à pergunta "o satélite sobre o mar ajuda?" com o histórico e não
  vai para o ar; critérios em `docs/PRE-REGISTRO-RAIN-V2-SATELITE.md`, fixados antes de rodar.
- Piloto de 3 dias (30/04 a 02/05/2026) rodado para conferir a extração.

## Condições que reabririam a decisão

- APAC mandar a tabela de cores (ou o produto em dBZ): radar passa a candidato, com a mesma regra das caixas.
- O histórico do GOES mostrar que as caixas não ajudam o rain_v2 (critérios A e B do pré-registro): o satélite sai do
  escopo.
- As caixas ajudarem e houver cadastro no PDA: o Enterprise Rainfall Rate entra como fonte nova, com quebra de série e
  novo treino.
