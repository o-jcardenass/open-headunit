#!/usr/bin/env bash
# Fake adb for rig-lib tests. State lives in $FAKE_ADB_DIR/<serial>.<key>.
# FAKE_ADB_STUCK=1 makes svc ignore every switch (a radio that will not change).
D=${FAKE_ADB_DIR:?}
[ "$1" = -s ] || { echo "fake adb: need -s" >&2; exit 1; }
S=$2; shift 2
get() { cat "$D/$S.$1" 2>/dev/null || echo "$2"; }
case "$*" in
  get-state) [ -f "$D/$S.absent" ] && exit 1; echo device ;;
  "shell svc wifi enable") [ -n "${FAKE_ADB_STUCK:-}" ] || echo on > "$D/$S.wifi" ;;
  "shell svc wifi disable") [ -n "${FAKE_ADB_STUCK:-}" ] || { echo off > "$D/$S.wifi"; rm -f "$D/$S.go"; } ;;
  "shell svc bluetooth enable") [ -n "${FAKE_ADB_STUCK:-}" ] || echo on > "$D/$S.bt" ;;
  "shell svc bluetooth disable") [ -n "${FAKE_ADB_STUCK:-}" ] || echo off > "$D/$S.bt" ;;
  "shell dumpsys bluetooth_manager")
    if [ "$(get bt off)" = on ]; then echo "  state: ON"; echo "Bonded devices:"; cat "$D/$S.bonds" 2>/dev/null
    else echo "  state: OFF"; echo "Bonded devices:"; fi ;;
  "shell dumpsys wifi") [ "$(get wifi off)" = on ] && echo "Wi-Fi is enabled" || echo "Wi-Fi is disabled" ;;
  "shell dumpsys wifip2p") echo "mWifiP2pInfo groupFormed: $(get go false) isGroupOwner: $(get go false)" ;;
  "shell pidof com.andrerinas.headunitrevived") get pid "" ;;
  "shell dumpsys battery") echo "  level: $(get battery 80)" ;;
  "shell settings get secure bluetooth_name") get name null ;;
  "shell settings get secure bluetooth_address") get addr null ;;
  logcat*) i=0; while :; do echo "10-29 12:00:00.000 1 1 I fake: line $i"; i=$((i+1)); sleep 0.2; done ;;
  *) : ;;
esac
