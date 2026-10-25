#!/usr/bin/env bash
# Create a new Paper plugin project from the SynthetixLib consumer template.
#   new-plugin.sh <target-folder> <package> <Name> [id]
#   new-plugin.sh ../auction com.acme.auction Auction
# id defaults to the lower-cased Name (plugin id, command name, jar name).
# Copies templates/consumer-plugin, renames the package, the MyPlugin* classes and the id, and removes the
# "@snippet:" / "@end" marker lines (they only matter inside the SynthetixLib repo).
set -euo pipefail

fail() { echo "new-plugin: $1" >&2; exit 1; }

[ $# -ge 3 ] && [ $# -le 4 ] || fail "usage: new-plugin.sh <target-folder> <package> <Name> [id]"
target=$1
package=$2
name=$3
id=${4:-$(printf '%s' "$name" | tr '[:upper:]' '[:lower:]')}

[[ $package =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]] || fail "package '$package' must be lower-case with at least two parts (com.acme.auction)"
case "$package" in
  com.github.dewarepk.synthetixlib|com.github.dewarepk.synthetixlib.*)
    fail "package '$package' must not start with com.github.dewarepk.synthetixlib (relocation would rewrite it)" ;;
esac
[[ $name =~ ^[A-Z][A-Za-z0-9]*$ ]] || fail "name '$name' must be a Java class prefix starting with a capital (Auction)"
[[ $id =~ ^[a-z][a-z0-9_-]*$ ]] || fail "id '$id' must be lower-case letters, digits, - or _"

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
template="$here/../templates/consumer-plugin"
[ -d "$template" ] || fail "template not found: $template"
if [ -e "$target" ] && [ -n "$(ls -A "$target")" ]; then fail "target '$target' exists and is not empty"; fi
mkdir -p "$target"

# Copy without build output, then rename; text files are rewritten in one sed pass (placeholders, so a replacement
# is never replaced again).
(cd "$template" && tar --exclude=./build --exclude=./.gradle --exclude=./.kotlin --exclude=./.idea --exclude=./run \
  --exclude=./out -cf - .) | (cd "$target" && tar -xf -)

old_dir="$target/src/main/java/com/example/myplugin"
new_dir="$target/src/main/java/${package//.//}"
mkdir -p "$new_dir"
mv "$old_dir"/* "$new_dir"/
rmdir "$old_dir" "$target/src/main/java/com/example" 2>/dev/null || true
rmdir "$target/src/main/java/com" 2>/dev/null || true
for f in "$new_dir"/MyPlugin*.java; do
  mv "$f" "$new_dir/${name}$(basename "$f" | sed 's/^MyPlugin//')"
done

find "$target" -type f \( -name '*.java' -o -name '*.kts' -o -name '*.yml' -o -name '*.yaml' -o -name '*.properties' -o -name '*.md' \) \
  -not -path '*/gradle/wrapper/*' | while IFS= read -r file; do
  sed -E \
    -e '/^[[:space:]]*(\/\/|#)[[:space:]]*@(snippet:[^[:space:]]*|end)[[:space:]]*$/d' \
    -e 's/com\.example\.myplugin/@@PKG@@/g' \
    -e 's/MyPlugin/@@NAME@@/g' \
    -e 's/myplugin/@@ID@@/g' \
    -e "s/@@PKG@@/$package/g" \
    -e "s/@@NAME@@/$name/g" \
    -e "s/@@ID@@/$id/g" \
    "$file" > "$file.tmp"
  mv "$file.tmp" "$file"
done
chmod +x "$target/gradlew"

echo "Created $target  (package $package, plugin $name, id $id)"
echo "Next: cd \"$target\" && ./gradlew build     (add -PsynthetixLocal=true for a local SynthetixLib build)"
