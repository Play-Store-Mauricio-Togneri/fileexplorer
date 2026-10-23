"""
Uploads the phone screenshots play_screenshots.py rendered to the Play Store listing: every listing
language that has a folder gets its current phone screenshots replaced by that folder's eight, in
slot order, all in one change.

Without --commit it only rehearses. Play stages every upload in a draft change and checks it, then
the draft is thrown away, so nothing reaches the listing. With --commit the draft is published, and
Google reviews the listing change as it reviews any other. Publishing fails, changing nothing, while
other changes such as an app update are in review.

It signs in as a Google Cloud service account that has access to the app in Play Console, with that
account's JSON key, which must stay outside the repository. One-time setup:
1. In Google Cloud, in any project, enable the Google Play Android Developer API.
2. Create a service account there (it needs no Cloud roles), then add a JSON key to it and save the
   file outside the repository.
3. In Play Console, Users and permissions, invite the service account's email address and give it
   access to File Explorer with permission to manage the store presence.
A newly invited account can take a while before the API accepts it; an error that says the caller
lacks permission right after setup usually means waiting.

Usage: python3 scripts/play_upload.py --key <service-account.json> [--commit] [screenshots]
The screenshots folder defaults to build/play-screenshots, one folder per Play language code.
It needs nothing beyond requests, PyJWT and cryptography.
"""

import argparse
import json
import pathlib
import sys
import time

import jwt
import requests

PACKAGE = "com.mauriciotogneri.fileexplorer"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"
UPLOAD_API = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
IMAGE_TYPE = "phoneScreenshots"
SLOTS = 8
REPOSITORY = pathlib.Path(__file__).resolve().parent.parent


def check(response: requests.Response, action: str) -> requests.Response:
    if not response.ok:
        sys.exit(f"Could not {action}: HTTP {response.status_code}\n{response.text}")
    return response


def access_token(key_path: pathlib.Path) -> str:
    """Trades a token signed with the service account's private key for an hour's access."""
    key = json.loads(key_path.read_text(encoding="utf-8"))
    now = int(time.time())
    assertion = jwt.encode(
        {"iss": key["client_email"], "scope": SCOPE, "aud": key["token_uri"], "iat": now, "exp": now + 3600},
        key["private_key"],
        algorithm="RS256",
    )
    response = requests.post(
        key["token_uri"],
        data={"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": assertion},
        timeout=30,
    )
    return check(response, "sign in with the service account key").json()["access_token"]


def screenshot_sets(root: pathlib.Path) -> dict[str, list[pathlib.Path]]:
    """Each language folder's screenshots in slot order; stops if any folder is not a full set."""
    sets = {}
    for folder in sorted(path for path in root.iterdir() if path.is_dir() and path.name != "raw"):
        files = sorted(folder.glob("*.png"), key=lambda path: int(path.name.split("-", 1)[0]))
        if len(files) != SLOTS:
            sys.exit(f"{folder} has {len(files)} screenshots, not {SLOTS}")
        sets[folder.name] = files
    if not sets:
        sys.exit(f"No screenshots in {root}: run scripts/play-screenshots.sh first")
    return sets


def upload(session: requests.Session, edit: str, sets: dict[str, list[pathlib.Path]]) -> None:
    listings = check(session.get(f"{API}/edits/{edit}/listings", timeout=30), "read the listing languages")
    languages = [listing["language"] for listing in listings.json().get("listings", [])]

    for language in languages:
        files = sets.get(language)
        if files is None:
            print(f"{language}: no screenshots for it here, left as it is")
            continue
        images = f"{API}/edits/{edit}/listings/{language}/{IMAGE_TYPE}"
        check(session.delete(images, timeout=30), f"remove {language}'s current screenshots")
        # Play shows a listing's screenshots in the order they were uploaded.
        for file in files:
            check(
                session.post(
                    f"{UPLOAD_API}/edits/{edit}/listings/{language}/{IMAGE_TYPE}",
                    params={"uploadType": "media"},
                    data=file.read_bytes(),
                    headers={"Content-Type": "image/png"},
                    timeout=120,
                ),
                f"upload {file}",
            )
        staged = check(session.get(images, timeout=30), f"read back {language}'s screenshots").json()
        if len(staged.get("images", [])) != len(files):
            sys.exit(f"{language} holds {len(staged.get('images', []))} screenshots after uploading {len(files)}")
        print(f"{language}: {len(files)} screenshots")

    for language in sorted(set(sets) - set(languages)):
        print(f"{language}: not a language of the Play listing, skipped")


def main() -> None:
    parser = argparse.ArgumentParser(description="Upload the Play Store phone screenshots.")
    parser.add_argument("--key", required=True, type=pathlib.Path, help="the service account's JSON key")
    parser.add_argument("--commit", action="store_true", help="publish instead of only rehearsing")
    parser.add_argument("screenshots", nargs="?", type=pathlib.Path, default=REPOSITORY / "build/play-screenshots")
    args = parser.parse_args()

    if args.key.resolve().is_relative_to(REPOSITORY):
        sys.exit(f"Move {args.key} outside the repository, where it cannot be committed by mistake")
    sets = screenshot_sets(args.screenshots)

    session = requests.Session()
    session.headers["Authorization"] = f"Bearer {access_token(args.key)}"
    edit = check(session.post(f"{API}/edits", timeout=30), "open a draft change").json()["id"]
    published = False
    try:
        upload(session, edit, sets)
        check(session.post(f"{API}/edits/{edit}:validate", timeout=60), "validate the draft change")
        if args.commit:
            # Play's default cancels whatever is in review, such as an app update, and resubmits it
            # with this change. Refuse instead: run again once that review is over.
            check(
                session.post(
                    f"{API}/edits/{edit}:commit",
                    params={"changesInReviewBehavior": "ERROR_IF_IN_REVIEW"},
                    timeout=120,
                ),
                "publish the draft change (it fails while other changes are in review)",
            )
            published = True
            print("Published. Google reviews the listing change before it shows on Play.")
        else:
            print("Rehearsal passed and nothing was published. Run again with --commit to publish.")
    finally:
        # A rehearsal or a failure leaves no draft behind.
        if not published:
            session.delete(f"{API}/edits/{edit}", timeout=30)


if __name__ == "__main__":
    main()
