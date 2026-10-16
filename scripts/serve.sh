#!/usr/bin/env bash

set -e

cd "$(dirname "$0")/.."

# Serves website/ through the Firebase Hosting emulator, which applies firebase.json the way
# production does (clean URLs, the 404 page). Stop it with Ctrl+C.
firebase emulators:start --only hosting
