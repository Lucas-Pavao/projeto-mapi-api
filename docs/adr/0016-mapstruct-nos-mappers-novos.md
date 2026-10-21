# 0015 — MapStruct nos mappers novos

- Status: aprovado (2026-10-21, instrução do prompt da F2: "MapStruct")
- Data: 2026-10-21
- Decisores: Agente de backend da F2 (autor), Lucas
- Tarefa: F2-REDESIGN-01

## Contexto

O `agents.md` §5/§6 manda usar MapStruct nos mappers. O código não tinha a dependência; o único mapper (`TideMapper`) é manual, e a skill `mapi-clean-arch-java` §2 pede ADR antes de adicionar a biblioteca (fonte: `grep -n -i mapstruct pom.xml`, 2026-10-21, 0 resultados antes desta tarefa).

## Opções consideradas

### A — Mappers manuais (padrão do `TideMapper`)
A favor: sem dependência. Contra: contraria o `agents.md`; erro de campo esquecido só aparece em teste.

### B — MapStruct 1.6.3 com `componentModel = spring`
A favor: segue o `agents.md`; `unmappedTargetPolicy = ERROR` reprova no compilador o campo do contrato sem origem. Contra: um processador de anotação a mais, que precisa rodar depois do Lombok (`lombok-mapstruct-binding`).

## Decisão

Opção B, só para código novo. `TideMapper` e os mappers manuais existentes não são reescritos.

## Consequências

- Mappers novos ficam em `mapper/` como interface `@Mapper`, sem lógica de negócio.
- Campo do DTO sem origem na entidade quebra a compilação (`-Amapstruct.unmappedTargetPolicy=ERROR`); resolve-se com `@Mapping(target=..., ignore=true)` explícito ou `expression`.
- Medida: `./mvnw -q compile` verde.

## Condições que reabririam a decisão

- Conflito do processador com versão futura do Lombok ou do Java.
