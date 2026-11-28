# Pré-registro — capacidade efetiva da galeria por ponto (onda 16, Fase 4.1)

- Data: 2026-11-23 · Status: **aprovado** (ponto de parada E coberto pelo aval geral do Lucas de 22/11) · escrito antes de rodar
- Substitui, no alerta único, a capacidade de projeto (IDF de Silva e Araújo, TR 5, Te 10 min) pela capacidade da galeria no
  ponto, como **faixa** [Q_min, Q_max] em m³/s. Complementa `docs/PRE-REGISTRO-CAPACIDADE-GALERIA.md` (21/11), que fica valendo
  para a escolha dos elementos do ESIG.
- Nenhum valor aqui é ajustado olhando alagamento. A calibração, se houver, é outro pré-registro
  (`docs/PRE-REGISTRO-CALIBRACAO-INVERSA.md`).

## 1. Por que

Hoje a entrada do balde é C·P·A e a saída é C·i·A com o mesmo C e a mesma A. O balde só acumula com mais de 20,9 mm em 10 min
(`docs/ONDA16-ESTADO-INICIAL.md`, ajuste 2). A capacidade de projeto diz quanto a galeria deveria levar, não quanto ela leva.

## 2. Componentes e fontes

Elementos do ESIG pelas regras de 21/11 (`scripts/capacidade-galeria/capacidade.py`): bocas de lobo (TIPO = BL) a até 50 m do
fundo da bacia e com cota até fundo + 0,15 m; tubo do nó a até 30 m do fundo, com diâmetro e declividade válidos. O fundo é o
do terreno da Fase 3 (`MAPI/terreno/onda16/ponto_terreno.csv`).

### 2.1 Entrada (bocas de lobo)

- Teórica por boca: vertedor de guia em ponto baixo, Q = 1,60 · L · d^1,5 (HEC-22, Eq. 4-30, SI), com d = 0,15 m (h_crit) e
  L de 0,6 a 1,5 m. **L é prior sem medição** (regra 2 do prompt): o ESIG não traz o comprimento da abertura.
- Redução DAEE/CETESB para ponto baixo: guia 0,80; combinada 0,65; grelha 0,50. Fonte: Prefeitura de Suzano, *Manual de
  Manejo das Águas Pluviais* (Produto 40, Consórcio IEME Brasil–Base, 30/09/2022), Tabela 10, p. 36 do PDF, citando
  DAEE/CETESB, *Drenagem Urbana: Manual de Projeto*, 2ª ed., São Paulo, 1980
  (https://pmdmap.suzano.sp.gov.br/wp-content/uploads/Documentos/Produto-Manual_de_manejo_de_aguas_pluviais.pdf, acesso
  23/11/2026, SHA-256 do PDF no anexo). O manual original da CETESB (repositório da CETESB, 458 páginas) é imagem sem camada de
  texto e não foi possível conferir a página nele.
- O ESIG não diz o tipo da boca (campo TIPO só tem "BL"): o fator vai de 0,50 a 0,80.
- Q_entrada,min = Σ 1,60 · 0,6 · 0,15^1,5 · 0,50; Q_entrada,max = Σ 1,60 · 1,5 · 0,15^1,5 · 0,80.

### 2.2 Tubo (Manning, seção plena)

- Q = (1/n) · A · R^(2/3) · S^(1/2), com D e S do ESIG.
- n de 0,013 a 0,015 (concreto). Fontes: Suzano, Tabela 8 ("adaptado de SUDERHSA"), p. 31 do PDF: galerias de concreto
  pré-moldado 0,011–0,014, moldado com forma metálica 0,012–0,014, com forma de madeira 0,015–0,020; e Silva et al. (2020),
  p. 156, que usam 0,014 para condutos fechados de concreto na Madalena, Recife (ver abaixo). A faixa 0,013–0,015 fica dentro
  das linhas de concreto da tabela e em volta do valor usado no Recife.
- Obstrução: 20 cm de sedimento no fundo do tubo. Fonte: Silva, A. C.; Silva Junior, M. A. B.; Silva, S. R.; Cabral, J. J. S. P.
  "Os efeitos da manutenção preventiva no desempenho hidráulico de um sistema de drenagem urbana do Recife – PE". *Revista DAE*,
  v. 68, n. 225, p. 149–166, set. 2020, **p. 156**: "A altura de sedimentos depositados no fundo da galeria, ao longo do
  sistema, foi estimada em 20 cm, caracterizando parcial obstrução do conduto (com base nas investigações em campo)"; o mesmo
  parágrafo avisa que os valores "são iniciais e foram ajustados na etapa de calibração". DOI 10.36659/dae.2020.059;
  https://www.revistadae.com.br/downloads/edicoes/Revista-DAE-225.pdf, acesso 23/11/2026.
- **O "20 cm de sedimento → −40% de vazão" atribuído a essa revista não está no artigo.** O artigo dá a redução do volume
  alagado no modelo SWMM depois da manutenção (80,65% em TR 2 e 61,93% em TR 10), não a perda de vazão do tubo. A perda de
  vazão aqui é calculada pela geometria: seção circular com os 20 cm de baixo cheios, A e R do que sobra, Manning de novo.
  Só vale para D > 0,20 m.
- Q_cano,min = Manning(n = 0,015, com 20 cm de sedimento); Q_cano,max = Manning(n = 0,013, limpo).

### 2.3 Faixa do ponto

- Com os dois componentes: Q_min = min(Q_entrada,min, Q_cano,min); Q_max = min(Q_entrada,max, Q_cano,max).
- Com um só componente (o outro sem cadastro): a faixa é a dele, marcada "limite superior: <componente> sem cadastro".
- Sem nenhum (CIn, Jaboatão): fica a capacidade de projeto, agora como faixa da IDF com TR de 2 a 10 anos em Te = 10 min
  (Silva e Araújo, 2013, Eq. 16; `PARAMETROS-MODELO-FISICO.md`), marcada "sem cadastro".
- Derby continua fora (terreno em revisão).

## 3. Como entra no balde

Saída por passo = Q · Δt (m³), multiplicada pelo fator de refluxo e pelo estado da galeria (limpeza), como hoje. O balde
roda duas vezes, com Q_min e com Q_max. Equivalência para a tela: i_eq = Q / (C · A) em mm/h.

## 4. Regra do nível (fixada agora)

- ALERTA: a lâmina do cenário q50 chega a 15 cm **com Q_max** (alaga mesmo com a galeria no melhor estado com fonte).
- ATENÇÃO: não é ALERTA, e a lâmina do cenário q95 chega a 15 cm **com Q_min**.
- SEM ALERTA: nenhum dos dois.
- A tela mostra a lâmina nas duas pontas da faixa, em cada cenário.
- Se a calibração inversa (outro pré-registro) for adotada num ponto, Q passa a ser o Q calibrado nesse ponto, nos dois
  cenários.

## 5. Saídas

`projeto-mapi-ai/data/capacidade/capacidade_efetiva.json` (por ponto: componentes, faixa, i_eq, fontes) e
`projeto-mapi-ai/reports/capacidade/RELATORIO.md`. A API manda a faixa para `/v1/flood/forecast`.

## Anexo — arquivos conferidos

| Arquivo | SHA-256 |
|---|---|
| Revista-DAE-225.pdf (220 p.) | `05af6cd81547c2d02fc23a571d18c2249cb51d2351637eb3bc04fb3a69554ec9` |
| Manual de Suzano (50 p.) | `361503852dcef845f38a7492bfefe33a90b82b45ce422e315a32840eb73aae47` |
| Manual CETESB (repositório, 458 p., sem texto) | `34a7e43179595d2541ac89ca08584ad80f4b8ce98ca67c639feadccf55d42712` |
