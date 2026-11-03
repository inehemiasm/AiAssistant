"""Publish a verified AAB to Chevere's internal track only, using Google's API."""
import argparse
from pathlib import Path
import re
import subprocess
import sys

PACKAGE = "dev.neo.chevereai"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
MAX_CODE = 2_100_000_000


class PublishValidationError(ValueError):
    """An app-owned, safe-to-display validation failure."""


def fingerprint(value):
    normalized = value.replace(":", "").strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", normalized):
        raise PublishValidationError("Set the SHA-256 upload certificate fingerprint from Play Console")
    return normalized


def verify_signature(aab, expected):
    if aab.suffix != ".aab" or not aab.is_file():
        raise PublishValidationError("A signed .aab file is required")
    expected = fingerprint(expected)
    flags = ["-J-Duser.language=en", "-J-Duser.country=US"]
    signed = subprocess.run(["jarsigner", *flags, "-verify", str(aab)], capture_output=True, text=True, check=True)
    if "jar verified." not in signed.stdout.lower():
        raise PublishValidationError("The AAB has no verified JAR signature")
    cert = subprocess.run(["keytool", *flags, "-printcert", "-jarfile", str(aab)], capture_output=True, text=True, check=True)
    found = re.findall(r"SHA256:\s*([0-9A-Fa-f:]+)", cert.stdout)
    if len(found) != 1 or fingerprint(found[0]) != expected:
        raise PublishValidationError("AAB signer does not match the configured Play upload certificate")


def release_body(code, name, status):
    if not 1 <= code <= MAX_CODE or not re.fullmatch(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)", name):
        raise PublishValidationError("Invalid release version")
    if status not in ("draft", "completed"):
        raise PublishValidationError("Only draft or completed internal releases are supported")
    return {"track": "internal", "releases": [{"name": f"{name} ({code})", "versionCodes": [str(code)], "status": status}]}


def publish(service, aab, code, name, status, media_factory):
    body = release_body(code, name, status)  # Validate before creating an edit.
    edits = service.edits()
    edit = None
    committed = False
    try:
        edit = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
        existing = edits.bundles().list(packageName=PACKAGE, editId=edit).execute().get("bundles", [])
        tracks = edits.tracks().list(packageName=PACKAGE, editId=edit).execute().get("tracks", [])
        internal = next((track for track in tracks if track.get("track") == "internal"), {})
        current_releases = internal.get("releases", [])
        if any(release.get("status") == "draft" for release in current_releases):
            raise PublishValidationError("An internal draft already exists. Review or discard it in Console before uploading another build.")
        if status == "draft":
            # Preparing a draft must not remove the completed release used by testers.
            body["releases"] = current_releases + body["releases"]
        codes = [int(bundle["versionCode"]) for bundle in existing]
        codes += [int(c) for track in tracks for release in track.get("releases", []) for c in release.get("versionCodes", [])]
        if codes and code <= max(codes):
            raise PublishValidationError("Version code is already used or older than Play. Build again with a new code.")
        request = edits.bundles().upload(packageName=PACKAGE, editId=edit,
            media_body=media_factory(str(aab), mimetype="application/octet-stream", chunksize=8 * 1024 * 1024, resumable=True))
        response = None
        while response is None:
            _, response = request.next_chunk(num_retries=3)
        if int(response["versionCode"]) != code:
            raise PublishValidationError("Uploaded AAB version differs from the requested version; edit will not be committed")
        edits.tracks().update(packageName=PACKAGE, editId=edit, track="internal", body=body).execute()
        edits.validate(packageName=PACKAGE, editId=edit).execute()
        # Do not automatically retry commit: a timeout can leave its outcome uncertain.
        edits.commit(packageName=PACKAGE, editId=edit).execute()
        committed = True
    finally:
        if edit and not committed:
            try:
                edits.delete(packageName=PACKAGE, editId=edit).execute()
            except Exception:
                print("Could not confirm edit cleanup; check Play Console before retrying.", file=sys.stderr)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aab", type=Path, required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--expected-certificate", required=True)
    parser.add_argument("--status", choices=("draft", "completed"), default="draft")
    args = parser.parse_args()
    try:
        release_body(args.version_code, args.version_name, args.status)
        verify_signature(args.aab, args.expected_certificate)
        import google.auth
        import google_auth_httplib2
        import httplib2
        from googleapiclient.discovery import build
        from googleapiclient.http import MediaFileUpload
        credentials, _ = google.auth.default(scopes=[SCOPE])
        http = google_auth_httplib2.AuthorizedHttp(credentials, http=httplib2.Http(timeout=120))
        with build("androidpublisher", "v3", http=http, cache_discovery=False) as service:
            publish(service, args.aab, args.version_code, args.version_name, args.status, MediaFileUpload)
        print(f"Committed {args.version_name} ({args.version_code}) to internal as {args.status}.")
        return 0
    except PublishValidationError as error:
        print(str(error), file=sys.stderr)
    except Exception as error:
        # Never dump credentials, token responses or raw API error bodies into CI logs.
        status = getattr(getattr(error, "resp", None), "status", None)
        print(f"Play upload failed ({type(error).__name__}, HTTP {status}). Check app setup, permissions and Console state before retrying.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
