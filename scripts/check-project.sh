#!/usr/bin/env sh
# Architecture gate, run by CI before the tests and the build. Each check guards an
# invariant a refactor could silently break without failing compilation; ARCHITECTURE.md
# says why each one matters. A failing check prints what it was guarding.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
APP="$ROOT/app"
SRC="$APP/src/main/kotlin/my/github/MrxSiN/modeevolved"
META="$APP/src/main/resources/META-INF/xposed"
MANIFEST="$APP/src/main/AndroidManifest.xml"

FAILED=0
fail() { echo "check-project: $*" >&2; FAILED=1; }
# Fails with [message] when [pattern] is found in code (not comments) in any of the files that follow.
forbid() {
    MESSAGE=$1; PATTERN=$2; shift 2
    if grep -rnE -- "$PATTERN" "$@" | grep -vE '^[^:]+:[0-9]+:[[:space:]]*(\*|//|/\*|#)' >&2; then fail "$MESSAGE"; fi
}
# Fails with [message] unless [pattern] is found in [file].
require() { grep -qE -- "$2" "$3" || fail "$1 ($3)"; }

# Modern Xposed metadata: the framework finds the module and its two hosts through these.
require "minimum libxposed API" '^minApiVersion=101$' "$META/module.prop"
require "static scope" '^staticScope=true$' "$META/module.prop"
require "system_server hot reload" '^autoHotReload=true$' "$META/module.prop"
require "entry class" '^my\.github\.MrxSiN\.modeevolved\.ModeEvolvedModule$' "$META/java_init.list"
require "system_server scope" '^system$' "$META/scope.list"
require "Settings scope" '^com\.android\.settings$' "$META/scope.list"
[ "$(grep -c . "$META/scope.list")" -eq 2 ] || fail "the module is scoped to system_server and Settings only"

# 1. The Xposed entry point only routes host processes to their parts.
ENTRY="$SRC/ModeEvolvedModule.kt"
require "the entry point routes system_server" 'SystemServerPart' "$ENTRY"
require "the entry point routes Settings" 'SettingsPart' "$ENTRY"
ROUTER_IMPORTS='^import my\.github\.MrxSiN\.modeevolved\.(compat\.Host|core\.AndroidLogger|page\.SettingsPart|system\.SystemServerPart)$'
if grep -E '^import my\.github\.MrxSiN\.modeevolved\.' "$ENTRY" | grep -vE "$ROUTER_IMPORTS" >&2; then
    fail "the entry point imports only the host enum, the logger and the two host parts"
fi
forbid "the entry point installs no hooks and builds no features" '\.hook\(|Hooks\(|FeatureInstaller|HostResolver' "$ENTRY"
[ "$(wc -l < "$ENTRY")" -le 60 ] || fail "the entry point grew past a router ($ENTRY)"

# 2. Pure decision logic stays free of Android, the hook framework and the dex search.
PURE="$SRC/trigger $SRC/engine $SRC/rule $SRC/action $SRC/compat $SRC/security $SRC/calendar/EventFilter.kt $SRC/core/Concurrency.kt $SRC/core/Contracts.kt $SRC/core/Health.kt $SRC/core/Types.kt"
# shellcheck disable=SC2086
forbid "pure logic must not depend on Android" '^import android\.' $PURE
# shellcheck disable=SC2086
forbid "pure logic must not depend on the hook framework or dex search" '^import (io\.github\.libxposed|org\.luckypray)' $PURE
forbid "the resolver layer must not depend on Android" '^import android\.' "$SRC/dex/HostResolver.kt" "$SRC/dex/ClassFinder.kt"

# 3. Private host names live in the contract catalogs; reflection lives in the resolver.
PRIVATE='"[^"]*(com\.android\.(settings|settingslib|server)\.|androidx\.(preference|fragment|lifecycle|core\.view)|androidx\.activity\.ComponentActivity|com\.google\.android\.material|android\.service\.notification\.ZenModeConfig)'
forbid "private host names belong in compat/SettingsApis.kt or compat/SystemServerApis.kt" "$PRIVATE" \
    $(find "$SRC" -name '*.kt' ! -path '*/compat/*')
forbid "host members are resolved from contracts by dex/HostResolver, not looked up ad hoc" \
    'getDeclaredMethod|getMethod\(|getDeclaredField|getField\(|getDeclaredConstructor|getConstructor\(|Class\.forName|loadClass\(' \
    $(find "$SRC" -name '*.kt' ! -path '*/compat/*' ! -path '*/dex/*')
forbid "only the libxposed hook call may name hook internals" 'XposedHelpers|findAndHook' "$SRC"
# R8 renames Kotlin classes named in strings to the module's own shrunk copy.
forbid "a host lookup must never name a kotlin.* class" '"kotlin\.' "$SRC"

# 4 and 5. Every feature declares its contract, and every declared member is used.
CONTRACTS="$SRC/compat/FeatureContracts.kt"
for FEATURE in $(sed -n 's/^    \([A-Z_]*\)("[a-zA-Z]*", ".*", Host\.[A-Z_]*),$/\1/p' "$SRC/core/Health.kt"); do
    # A system setting's contract is generated from the catalogue entry that names its feature.
    grep -qE "Feature\.$FEATURE\b" "$CONTRACTS" "$SRC/action/SystemSettings.kt" || fail "feature $FEATURE declares its contract ($CONTRACTS)"
    grep -rqE "Feature\.$FEATURE\b" $(find "$SRC" -name '*.kt' ! -path '*/compat/*' ! -path '*/core/*' ! -path '*/presentation/*') \
        || fail "feature $FEATURE has no implementation naming it"
done
for CATALOG in "$SRC/compat/SettingsApis.kt" "$SRC/compat/SystemServerApis.kt"; do
    ALIAS=S; [ "$(basename "$CATALOG")" = SystemServerApis.kt ] && ALIAS=SS
    for NAME in $(sed -n 's/^    val \([A-Z_]*\) = .*/\1/p; s/^    val \([A-Z_]*\): .*/\1/p' "$CATALOG"); do
        if grep -qE "^    val $NAME = type\(" "$CATALOG"; then
            # A class is used through the members it owns, or by name in code.
            [ "$(grep -cE "\b$NAME\b" "$CATALOG")" -ge 2 ] || grep -rqE "\b$ALIAS\.$NAME\b" "$SRC" --include=*.kt \
                || fail "$NAME in $(basename "$CATALOG") is declared but nothing uses it"
        else
            grep -rqE "\b$ALIAS\.$NAME\b" $(find "$SRC" -name '*.kt' ! -path '*/compat/*') \
                || fail "$NAME in $(basename "$CATALOG") is declared but no implementation resolves it"
        fi
    done
done
require "the Settings part gates each feature on its contract" 'FeatureContracts\.of\(feature\)' "$SRC/page/SettingsPart.kt"
require "the system_server part gates each feature on its contract" 'FeatureContracts\.of\(feature\)' "$SRC/system/SystemServerPart.kt"

# Host fixtures: every recorded build has both hosts and is verified; names only, never a Google binary.
for BUILD_DIR in "$APP"/src/test/resources/hosts/*/; do
    BUILD=$(basename "$BUILD_DIR")
    for HOST in com.android.settings system_server; do
        [ -s "$BUILD_DIR/$HOST.txt" ] || fail "$BUILD has no description of $HOST"
        head -n 1 "$BUILD_DIR/$HOST.txt" | grep -q 'no host code' || fail "$BUILD/$HOST.txt was not written by the capture script"
    done
    grep -q "\"$BUILD\"" "$SRC/compat/VerifiedBuilds.kt" || fail "$BUILD is recorded but not in VerifiedBuilds"
done
if (cd "$ROOT" && git ls-files) | grep -iE '\.(apk|apks|dex|odex|vdex|jar|oat)$' | grep -v '^gradle/wrapper/gradle-wrapper\.jar$' >&2; then
    fail "no host binaries in the repository; fixtures hold names only"
fi

# 6. Exported screens answer Settings only, from what the system recorded about the caller.
[ "$(grep -c 'android:exported="true"' "$MANIFEST")" -eq 2 ] || fail "only the editor and the compatibility page are exported"
forbid "no exported service, receiver or provider" '<(service|receiver|provider)' "$MANIFEST"
for SCREEN in "$SRC/editor/EditorActivity.kt" "$SRC/diagnostics/DiagnosticsActivity.kt"; do
    require "an exported screen checks its caller before anything else" \
        'if \(!SettingsCaller\.isSettings\(callingPackage, launchedFromPackage\)\) return finish\(\)' "$SCREEN"
done
forbid "a caller is never judged by what the intent claims about it" '\breferrer\b|EXTRA_REFERRER|getReferrer' "$SRC"
require "the caller check compares against Settings itself" 'callingPackage == PACKAGE \|\| launchedFromPackage == PACKAGE' "$SRC/security/SettingsCaller.kt"
require "Settings shares its identity with the screens it opens" 'setShareIdentityEnabled\(true\)' "$SRC/page/EditorOpener.kt"
forbid "screens are opened only through EditorOpener" 'startActivity(ForResult)?\(' $(find "$SRC/page" -name '*.kt' ! -name 'EditorOpener.kt')

# 7. What system_server sends has passed the action check; nothing reaches the send around it.
[ "$(grep -rn 'sendBroadcastAsUser' "$SRC" | wc -l)" -eq 1 ] || fail "system_server broadcasts from one place"
require "the one broadcast path takes a checked action" 'private fun send\(action: SendableAction' "$SRC/system/IntentActionRunner.kt"
require "a checked action is only made by the check" 'class SendableAction private constructor' "$SRC/action/IntentActions.kt"
require "a checked action is only made by the check" 'if \(IntentActions\.isSendable\(action\)\) SendableAction\(action\) else null' "$SRC/action/IntentActions.kt"
forbid "no broadcast is built from a stored action name directly" 'Intent\([a-z]+\.intentAction' "$SRC"
[ "$(grep -rln 'startActivityAsUser\|START_ACTIVITY_AS_USER' "$SRC" | grep -v '/compat/' | wc -l)" -eq 1 ] \
    || fail "system_server starts activities from one place"

# 8. Settings can run one build's hook code against the next build's resources: IDs must not move.
require "resource IDs are pinned" '"--stable-ids"' "$APP/build.gradle.kts"
[ -s "$APP/resource-ids.txt" ] || fail "resource-ids.txt is missing"
LC_ALL=C sort -c "$APP/resource-ids.txt" 2>/dev/null || fail "resource-ids.txt is sorted, so it is the same file on every host"
for NAME in $(sed -n 's/.*<string name="\([^"]*\)".*/\1/p' "$APP/src/main/res/values/strings.xml"); do
    grep -q ":string/$NAME = " "$APP/resource-ids.txt" || fail "string $NAME has no pinned ID; run ./gradlew assembleRelease and commit resource-ids.txt"
done
for FILE in "$APP"/src/main/res/drawable/*.xml; do
    NAME=$(basename "$FILE" .xml)
    grep -q ":drawable/$NAME = " "$APP/resource-ids.txt" || fail "drawable $NAME has no pinned ID; run ./gradlew assembleRelease and commit resource-ids.txt"
done

# Concurrency: notifications coalesce, expensive latest-state work replaces stale requests.
forbid "a change notification requests a coalesced look, not one queued run per event" 'observe \{[^}]*\.execute\(' "$SRC"
[ "$(grep -rln 'Executors\.new' "$SRC" | wc -l)" -le 1 ] || fail "one background reader in Settings, EditorOpener's"
require "rule reloads coalesce" 'CoalescingTask' "$SRC/engine/ModeAutomation.kt"
require "action checks coalesce" 'CoalescingTask' "$SRC/action/ActionAutomation.kt"
require "editor launches keep only the newest request" 'LatestOnly' "$SRC/page/EditorOpener.kt"

# Release build: shrunk, signed from the environment, entry class kept by name.
require "release is minified" 'isMinifyEnabled = true' "$APP/build.gradle.kts"
require "release signs from the environment" 'envKeystorePath' "$APP/build.gradle.kts"
require "the entry class survives R8" 'ModeEvolvedModule' "$APP/proguard-rules.pro"
require "java_init.list follows R8" 'adaptresourcefilecontents META-INF/xposed/java_init.list' "$APP/proguard-rules.pro"

VERSION="$(sed -n 's/^val appVersion = "\([^"]*\)"/\1/p' "$APP/build.gradle.kts")"
require "release notes name the version being built" "v$VERSION" "$ROOT/RELEASE_NOTES.md"

[ "$FAILED" -eq 0 ] || exit 1
echo "Static project checks passed."
