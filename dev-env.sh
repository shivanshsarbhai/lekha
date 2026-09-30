# Project-scoped dev environment for Lekha.
# Usage (from repo root, per terminal session):  source ./dev-env.sh
# Nothing here touches ~/.zshrc or other global config; it only affects the current shell.

LEKHA_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-${(%):-%x}}")" && pwd)"
export LEKHA_ROOT

# Keep unrelated work credentials out of this project's processes.
unset GOOGLE_APPLICATION_CREDENTIALS GOOGLE_CLOUD_PROJECT

export JAVA_HOME="/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# Isolated Gradle home (caches, wrapper dists, gradle.properties, init scripts),
# so nothing from a global ~/.gradle can leak in.
export GRADLE_USER_HOME="$LEKHA_ROOT/.tooling/gradle-home"

# Isolated npm user config + cache.
export NPM_CONFIG_USERCONFIG="$LEKHA_ROOT/.tooling/npmrc"
export NPM_CONFIG_CACHE="$LEKHA_ROOT/.tooling/npm-cache"

# Secrets and local settings from the git-ignored .env file.
if [[ -f "$LEKHA_ROOT/.env" ]]; then
  set -a
  source "$LEKHA_ROOT/.env"
  set +a
else
  echo "Warning: $LEKHA_ROOT/.env not found. Create it with: cp .env.example .env"
fi

echo "Lekha dev env loaded: $(java -version 2>&1 | head -1), node $(node -v)"
