#!/bin/sh
set -e

git filter-branch --force --tree-filter '
  rm -rf backend/target
  if [ -f backend/src/main/resources/application.yml ]; then
    sed -i -E \
      -e "s|^([[:space:]]*password: ).*$|\\1\\${DB_PASSWORD:}|" \
      -e "s|^([[:space:]]*client-id: ).*$|\\1\\${GITHUB_CLIENT_ID:}|" \
      -e "s|^([[:space:]]*client-secret: ).*$|\\1\\${GITHUB_CLIENT_SECRET:}|" \
      -e "s|^([[:space:]]*api-key: ).*$|\\1\\${AI_API_KEY:}|" \
      -e "s|^([[:space:]]*token: ).*$|\\1\\${GITHUB_TOKEN:}|" \
      backend/src/main/resources/application.yml
  fi
' --prune-empty --tag-name-filter cat -- --all