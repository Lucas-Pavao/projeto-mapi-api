#!/usr/bin/env bash
# Congelamento da validação de 2027 (onda 16, Fase 9.2; docs/CONGELAMENTO-2027.md).
# Confere que os três repositórios estão limpos, na main e iguais ao origin, que o código no ar é o mesmo commit e então
# cria a tag ASSINADA "validacao-2027-congelada" em cada um. Por padrão só confere (--dry-run); cria com --criar e envia
# ao GitHub com --enviar. Assinatura com a chave SSH (gpg.format=ssh) só nesta chamada, sem mudar a configuração global.
set -euo pipefail
MAPI="$(cd "$(dirname "$0")/../../.." && pwd)"
TAG="validacao-2027-congelada"
CHAVE="${MAPI_CHAVE_ASSINATURA:-$HOME/.ssh/id_ed25519.pub}"
MODO="--dry-run"; ENVIAR=0
for a in "$@"; do case "$a" in --criar) MODO="--criar";; --enviar) ENVIAR=1;; --dry-run) MODO="--dry-run";; *) echo "opção desconhecida: $a"; exit 2;; esac; done

no_ar() {   # commit servido por cada serviço (pilha local)
  case "$1" in
    projeto-mapi-api) docker exec mapi-api wget -qO- "http://localhost:${MANAGEMENT_PORT:-9404}/actuator/info" \
      | python3 -c 'import json,sys; print(json.load(sys.stdin)["git"]["commit"]["id"]["full"])' ;;
    projeto-mapi-ai) curl -fsS http://localhost:8000/health | python3 -c 'import json,sys; print(json.load(sys.stdin)["code_version"])' ;;
    projeto-mapi-front) curl -fsS http://localhost:3000/version.json | python3 -c 'import json,sys; print(json.load(sys.stdin)["commit"])' ;;
  esac
}

falhou=0
for r in projeto-mapi-api projeto-mapi-ai projeto-mapi-front; do
  d="$MAPI/$r"
  git -C "$d" fetch -q origin
  ramo=$(git -C "$d" branch --show-current)
  sujo=$(git -C "$d" status --porcelain --untracked-files=no | wc -l)
  local_c=$(git -C "$d" rev-parse HEAD)
  remoto_c=$(git -C "$d" rev-parse origin/main)
  servido=$(no_ar "$r" || true)
  echo "$r: ramo=$ramo commit=$local_c origin/main=$remoto_c no_ar=${servido:-?} alterados=$sujo"
  [ "$ramo" = "main" ] || { echo "  ERRO: não está na main"; falhou=1; }
  [ "$sujo" -eq 0 ] || { echo "  ERRO: há arquivos alterados"; falhou=1; }
  [ "$local_c" = "$remoto_c" ] || { echo "  ERRO: diferente do origin/main"; falhou=1; }
  [ "${servido:-}" = "$local_c" ] || { echo "  ERRO: o commit no ar não é o da main (refaça o build com GIT_COMMIT)"; falhou=1; }
  git -C "$d" rev-parse -q --verify "refs/tags/$TAG" >/dev/null && { echo "  ERRO: a tag $TAG já existe"; falhou=1; }
done
[ "$falhou" -eq 0 ] || { echo "Nada foi criado."; exit 1; }
[ "$MODO" = "--criar" ] || { echo "Conferência ok. Rode com --criar para criar as tags."; exit 0; }

for r in projeto-mapi-api projeto-mapi-ai projeto-mapi-front; do
  d="$MAPI/$r"
  git -C "$d" -c gpg.format=ssh -c user.signingkey="$CHAVE" tag -s "$TAG" \
    -m "Validação de 2027 congelada (docs/PRE-REGISTRO-VALIDACAO-2027.md). Nada muda no modelo até o fim do período."
  echo "$r: tag $TAG em $(git -C "$d" rev-parse "$TAG^{commit}")"
  [ "$ENVIAR" -eq 1 ] && git -C "$d" push origin "$TAG"
done
echo "Feito. Registre os três commits em docs/CONGELAMENTO-2027.md e no PROGRESSO."
