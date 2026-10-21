-- Tela de usuários (redesign, decisão 43): e-mail e nome reais. Aditiva; usuários atuais ficam com e-mail nulo.
ALTER TABLE users ADD COLUMN email VARCHAR(255);
ALTER TABLE users ADD COLUMN name VARCHAR(255);
-- E-mail único sem diferenciar maiúsculas; vários nulos são permitidos.
CREATE UNIQUE INDEX users_email_lower_uq ON users (lower(email)) WHERE email IS NOT NULL;
