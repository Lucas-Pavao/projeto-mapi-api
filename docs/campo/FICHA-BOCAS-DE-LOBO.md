# Ficha de campo: bocas de lobo

- Data: 2026-11-21 · Prompt 3, tarefa 3 · Para: Lucas (visitas aos pontos)
- Objetivo: medir o entupimento (taxa **medida**, não reclamada) e o tipo real das bocas de lobo, que o cadastro do ESIG não traz.
- Planilha modelo: `docs/campo/bocas_de_lobo_modelo.csv` (uma linha por boca de lobo por visita).
- Regra: a ficha não registra alagamento. Se houver água na rua na visita, anote só em `observacao`; esse dado não entra no cálculo
  do entupimento.

## 1. Quais bocas visitar

Em cada ponto do Recife, as bocas de lobo do ESIG (`TIPO = BL`) a até 100 m do ponto, mais as que você encontrar na rua e não
estiverem no cadastro (essas recebem `esig_objectid` vazio). Lista inicial: `docs/extratos/esig-drenagem/geojson/<PONTO>__MI_c0.geojson`
(Ibura: 8 BL a 100 m; Derby: 3; CIn: 0; Imbiribeira: 0 cadastradas). Em CIn e Imbiribeira, o levantamento de campo é o único dado.

## 2. Protocolo

1. **Mesmo conjunto, datas fixas:** a cada 2 semanas, por 3 meses (7 visitas), sempre as mesmas bocas e na mesma ordem.
2. **Antes e depois de chuva forte:** uma visita extra até 24 h antes da chuva prevista (aviso do INMET ou previsão do MAPi) e outra
   até 48 h depois.
3. Foto de cima, com o celular a ~1 m, e uma de frente para a abertura da guia. Nome do arquivo: `<id_visita>_<id_boca>_<n>.jpg`.
4. Coordenada do GPS do celular parado sobre a boca; anote a precisão que o app mostrar.
5. Não abrir tampa nem entrar em caixa. Avaliar só o que se vê de fora.

## 3. Campos

| Campo | Como preencher |
|---|---|
| `id_visita` | AAAAMMDD-PONTO (ex.: 20261201-IBURA) |
| `id_boca` | código seu, fixo entre visitas (ex.: IBU-01) |
| `esig_objectid` | `OBJECTID` do ESIG, se a boca estiver no cadastro; senão vazio |
| `ponto` | slug do ponto (AV_RECIFE_IBURA, AGAMENON_DERBY, CIN_UFPE, MASCARENHAS_IMBIRIBEIRA) |
| `data`, `hora` | da observação (hora local) |
| `lat`, `lon`, `precisao_gps_m` | do celular |
| `tipo` | `guia` (abertura no meio-fio), `grelha`, `combinada` |
| `n_unidades` | quantas aberturas ou grelhas lado a lado (para o fator de Guo, 2012) |
| `largura_m`, `altura_m` | da abertura (guia) ou da grelha; trena |
| `obstrucao_faixa` | % da abertura coberta: `0`, `<25`, `25-50`, `50-75`, `>75`, `total` |
| `material` | um ou mais: `lixo`, `areia`, `folhas`, `entulho`, `outro` |
| `dano` | `nenhum`, `grelha_quebrada`, `sem_tampa`, `abatimento`, `outro` |
| `foto` | nomes dos arquivos, separados por `;` |
| `dias_desde_ultima_chuva` | da série do pluviômetro mais próximo no MAPi (só chuva; ≥ 1 mm no dia) |
| `limpeza_vista` | `sim` se houver sinal de limpeza recente (material ensacado, caminhão, equipe) |
| `observacao` | texto livre |

## 4. O que sai disso

- Taxa de entupimento por boca de lobo: transições de faixa entre visitas consecutivas (por exemplo, `0` → `25-50`).
- Fração do tempo em cada estado (livre, parcial, entupida), para conferir π do modelo de estados.
- Tipo e número de unidades por ponto, para escolher o fator de Guo (2012) e a capacidade de entrada (prompt 1).

## 5. Proposta para o app: relato "boca de lobo entupida" (só proposta, nada implementado)

Hoje o app tem o relato de alagamento. A proposta é um **tipo novo de relato, separado**, que alimenta a mesma tabela desta ficha
(campos da seção 3), para juntar observações de mais gente.

Telas (celular):

1. **Botão** "Boca de lobo entupida" na tela do ponto e no menu de relatar, ao lado de "Reportar alagamento", com outra cor e outro
   ícone, para não confundir.
2. **Foto** (obrigatória): câmera aberta direto; a coordenada vem do GPS no momento da foto.
3. **Três toques:** tipo (guia, grelha, combinada, "não sei"); quanto está tapado (as 6 faixas, com desenho de cada uma); material
   (lixo, areia, folhas, entulho).
4. **Confirmação:** "Obrigado. Isso ajuda a medir a drenagem. Para pedir a limpeza, ligue 156." (o app não abre chamado na EMLURB).

Campos gravados: tipo de relato `BOCA_DE_LOBO`, foto, lat/lon e precisão, data e hora do aparelho e do servidor, tipo, faixa,
material, ponto mais próximo (calculado), usuário (se logado). A ligação ao `esig_objectid` é feita depois, no servidor, pela boca
do cadastro mais próxima a até 10 m.

Cuidados:

- **Separação de dados:** relato de boca de lobo não pode virar rótulo de alagamento, e relato de alagamento não entra na taxa de
  entupimento. Tabela, tipo e endpoint separados.
- Foto pode mostrar pessoa ou placa de carro: borrar no servidor antes de guardar (LGPD).
- Relato de usuário é entupimento percebido, como a 156: entra como limite inferior, com peso menor que a ficha de campo.
