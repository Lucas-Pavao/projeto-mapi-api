-- Cache versionado da geocodificação (modelo v3, etapa A4). ADITIVA: só CREATE TABLE.
--
-- O Nominatim devolveu coordenadas diferentes para os mesmos endereços entre duas cargas (50 das 225 ocorrências
-- mudaram mais de 50 m e 29 mais de 500 m entre 2026-11-08 e a recarga), e a contagem de ocorrências a 500 m dos
-- pontos deixou de ser reprodutível. A recarga passa a ler daqui e só geocodifica endereço novo.
--
-- Chave: endereço saneado (CkanAddress.sanitize), bairro e cidade normalizados. Uma linha ativa por chave; trocar a
-- coordenada de um endereço é gravar nova versão e desativar a anterior, nunca UPDATE da coordenada.
CREATE TABLE geocode_cache (
    address_key      TEXT         NOT NULL,
    neighborhood_key TEXT         NOT NULL DEFAULT '',
    city_key         TEXT         NOT NULL DEFAULT '',
    version          INTEGER      NOT NULL DEFAULT 1,
    lat              DOUBLE PRECISION NOT NULL,
    lon              DOUBLE PRECISION NOT NULL,
    geocode_level    VARCHAR(12),
    provider         VARCHAR(40)  NOT NULL,
    origin           TEXT         NOT NULL,
    geocoded_at      TIMESTAMPTZ  NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (address_key, neighborhood_key, city_key, version)
);
CREATE UNIQUE INDEX geocode_cache_active_uq ON geocode_cache (address_key, neighborhood_key, city_key) WHERE active;
