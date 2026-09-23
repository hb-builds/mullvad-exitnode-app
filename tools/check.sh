#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 -m unittest discover -s oci-exit-node/tests -v
# Validation must also work when Python assertions are optimized away.
python3 -O -m unittest discover -s oci-exit-node/tests -q
for script in tools/*.sh exit-controller/build.sh oci-exit-node/scripts/{install,install-controller,network,tunnel,check-forwarding}; do
    bash -n "$script"
done
mkdir -p build
TEST_OUT=$(mktemp -d "$PWD/build/check.XXXXXX")
trap 'rm -rf "$TEST_OUT"' EXIT
javac -d "$TEST_OUT" exit-controller/app/src/main/java/one/hbx/exitcontroller/PairingEndpoint.java exit-controller/tests/PairingEndpointTest.java
java -cp "$TEST_OUT" one.hbx.exitcontroller.PairingEndpointTest
