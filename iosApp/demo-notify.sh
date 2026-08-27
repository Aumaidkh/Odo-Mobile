#!/bin/bash
# Fires the Smart Refuel demo "detected payment" notification on a real device.
# Usage: ./demo-notify.sh [amount] [station]
#   ./demo-notify.sh                     -> 1500, "HP Petrol Pump"
#   ./demo-notify.sh 850 "Shell Pump"    -> custom amount/station
#
# See AppDelegate.swift's postDemoDetectedFillNotification KDoc for why this exists
# and why the notification fires 10s after this command runs (background/close the
# app in that window) instead of instantly.

set -euo pipefail

DEVICE_ID="00008150-0012341C0ABB401C"
AMOUNT="${1:-1500}"
STATION="${2:-HP Petrol Pump}"
ENCODED_STATION=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "$STATION")

xcrun devicectl device process launch \
  --device "$DEVICE_ID" \
  --payload-url "odo://debug/post-detected-fill?amount=${AMOUNT}&station=${ENCODED_STATION}" \
  com.hopcape.odo.Odo

echo "Triggered — background/close the app now, notification fires in ~10s."
