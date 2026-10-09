#!/usr/bin/env bash
# Attaches a public APK and its checksum to a GitHub release, after proving the APK carries no
# Android Auto head-unit identity.
#
# Releases are published by hand, so this is the last place an APK built with
# -PincludeAndroidAutoIdentity=true could reach the public by mistake. `gh release upload` takes
# whatever file it is given; this refuses the wrong one.
#
# Usage: tooling/publish-release.sh <tag> <path-to-apk>
set -euo pipefail

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <tag> <path-to-apk>" >&2
    exit 2
fi
tag="$1"
apk="$2"

[ -f "$apk" ] || { echo "No such file: $apk" >&2; exit 1; }
case "$(basename "$apk")" in
    *android-auto*|*private*)
        echo "Refusing $(basename "$apk"): the name marks it as an Android Auto or private build." >&2
        exit 1 ;;
esac
# The identity is packaged as res/raw/aa_cert and res/raw/aa_identity_data. Resource shrinking
# keeps both names (see motohub_android_auto_identity_keep.xml), so the entry list is enough.
if unzip -Z1 "$apk" | grep -q -E '(^|/)aa_(cert|identity_data)(\.|$)'; then
    echo "Refusing $(basename "$apk"): it contains an Android Auto identity." >&2
    echo "Rebuild it with './gradlew exportPublicApk' and no identity flag." >&2
    exit 1
fi

repo="$(git config --get remote.origin.url | sed -E 's#^(https://github.com/|git@github.com:)##; s#\.git$##')"
checksum="$apk.sha256"
( cd "$(dirname "$apk")" && sha256sum "$(basename "$apk")" > "$(basename "$checksum")" )
gh release upload "$tag" "$apk" "$checksum" --repo "$repo" --clobber
echo "Uploaded $(basename "$apk") and its checksum to $repo $tag."
