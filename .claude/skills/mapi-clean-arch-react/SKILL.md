---
name: mapi-clean-arch-react
description: Convenções do front MAPI (React 19, TypeScript, Vite, MapLibre GL, Tailwind 4, shadcn/ui) derivadas do AGENTS.md e do GEMINI.md do projeto-mapi-front. Use sempre que for criar, refatorar ou revisar componentes, hooks, services, tipos ou camadas do mapa no projeto-mapi-front — inclusive ao exibir risco LOW/MEDIUM/HIGH/UNKNOWN, adicionar camada de terreno ou tratar erro da API — mesmo que o pedido não mencione arquitetura.
---

# MAPI — arquitetura do front React

Fonte de verdade: `AGENTS.md` e `GEMINI.md` do projeto-mapi-front. Divergência vira ADR.
Antes de qualquer mudança visual, carregue também a skill `frontend-design`.

## 1. Organização por feature

```
src/
├── components/ui/      primitivos shadcn (não colocar regra de domínio aqui)
├── hooks/              hooks globais (useAuth)
├── lib/utils.ts        cn()
└── features/
    ├── authentication/
    └── map/
        ├── components/ apresentação e contêineres do mapa
        ├── hooks/      efeitos e busca de dados (useMapInteractions…)
        ├── schemas/    zod — validação em runtime da resposta da API
        ├── services/   ÚNICO lugar com axios
        ├── types/      tipos de domínio
        └── utils/
```

Código exclusivo do mapa fica em `features/map/`. Feature nova = pasta nova com os mesmos
subdiretórios.

## 2. Separações que importam

- **Serviço isola HTTP**: componente nunca chama axios/fetch. Base URL só via
  `import.meta.env.VITE_API_URL`.
- **Validação na borda**: resposta da API passa pelo schema zod em `schemas/` antes de virar
  tipo de domínio. O tipo TS deriva do schema (`z.infer`) ou, quando houver geração a partir
  do OpenAPI do backend, do tipo gerado — nunca escrito à mão em paralelo.
- **Estado de servidor ≠ estado de UI**: dados da API ficam em hooks da feature (hoje não há
  TanStack Query; adicionar exige ADR). Seleção, painel aberto, filtro são estado de UI local.
- **Apresentação ≠ contêiner**: componente de apresentação recebe props e não busca dado.
  Contêiner usa hook e repassa. `MapView.tsx` (~1100 linhas) mistura os dois — ao mexer,
  extraia, não aumente.

## 3. MapLibre

- Toda `addSource`/`addLayer`/listener tem remoção no cleanup do `useEffect`.
- Atualizar dado = `map.getSource(id).setData(...)`, não recriar camada.
- Callbacks passados ao mapa em `useCallback`; transformações de série em `useMemo`.
- Nada de GeoJSON grande em `useState` do componente raiz.
- Camada raster (terreno/hipsometria) deve vir de tiles servidos (COG/XYZ), nunca de
  arquivo inteiro baixado no cliente.

## 4. Risco e degradação

| Valor da API | Significado | O que mostrar |
|---|---|---|
| `LOW`/`MEDIUM`/`HIGH` | classe derivada da probabilidade do modelo | variante visual fixa + legenda em linguagem de Defesa Civil |
| `UNKNOWN` | IA indisponível ou sem dado suficiente | estado neutro explícito ("previsão indisponível"), **nunca** cor de risco baixo |
| erro HTTP / timeout | API fora | mensagem de erro com horário do último dado válido |

Mapeamento enum→variante em um só lugar (`utils/`), exaustivo (`switch` com `never`).

## 5. Fuso e unidades

Backend persiste em UTC (ver ADR de fuso). Conversão para America/Recife é feita na
exibição, num utilitário único. Toda grandeza exibida mostra unidade (mm, m, cm).
Nomes de campo seguem o `docs/GLOSSARIO.md` do projeto-mapi-api.

## 6. Prompt-gate

1. Sem `any`; tipos batem com o contrato (schema zod/OpenAPI)?
2. Efeito isolado em hook, com cleanup?
3. Sem re-render infinito do mapa?
4. Dark/Light, foco visível, contraste AA, funciona em 390 px?
5. `UNKNOWN` e erro têm estado visual próprio?
