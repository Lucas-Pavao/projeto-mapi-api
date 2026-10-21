-- Perfil no token (ADR 0010, emenda 2): o access token leva role e tv (token_version). Mudar perfil ou ativação
-- incrementa token_version e invalida na hora os tokens já emitidos. Aditiva.
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
