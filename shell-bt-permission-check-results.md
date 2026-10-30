# Shell Bluetooth permission check, 2026-10-30

All five units were on adb. "Granted" is read only from a line naming the permission with `granted=`; a permission no line names is absent. D_SAM and D_HP (SDK 19 and 17) printed no matching line at all, so both permissions are absent there.

| Unit | SDK | BLUETOOTH_PRIVILEGED | BLUETOOTH_CONNECT | hidden_api_policy | id uid |
|---|---|---|---|---|---|
| D_HU | 34 | true | true | null | 0 (root) |
| D_POCO | 35 | true | true | null | 2000 (shell) |
| D_MOTO | 34 | true | true | null | 2000 (shell) |
| D_SAM | 19 | absent | absent | null | 2000 (shell) |
| D_HP | 17 | absent | absent | null | 2000 (shell) |

Notes: D_HU's adbd runs as root, so `id` shows uid 0, not shell. On D_POCO and D_MOTO the BLUETOOTH_CONNECT line appears once, in the per-user runtime-permission block, not in the install-permission block. D_POCO reports SDK 35 while its fingerprint says Android 12; both are recorded as printed.

No unit was off adb.

## Raw output

### D_HU (27870808938846)
```
$ getprop ro.build.version.sdk
34
$ getprop ro.build.fingerprint
UNISOC/uis7861_6h10_Natv/uis7861_6h10:14/UP1A.231005.007/eng.wming.20260518.231016:user/release-keys
$ dumpsys package com.android.shell | grep -E 'BLUETOOTH(_PRIVILEGED|_CONNECT|_ADMIN)?[:" ]'
      android.permission.BLUETOOTH_CONNECT: granted=true, flags=[ SYSTEM_FIXED|GRANTED_BY_DEFAULT]
      android.permission.BLUETOOTH: granted=true
      android.permission.BLUETOOTH_ADMIN: granted=true
      android.permission.BLUETOOTH_PRIVILEGED: granted=true
      android.permission.BLUETOOTH_CONNECT: granted=true, flags=[ SYSTEM_FIXED|GRANTED_BY_DEFAULT]
      android.permission.BLUETOOTH: granted=true
      android.permission.BLUETOOTH_ADMIN: granted=true
      android.permission.BLUETOOTH_PRIVILEGED: granted=true
$ settings get global hidden_api_policy
null
$ id
uid=0(root) gid=0(root) groups=0(root),1004(input),1007(log),1011(adb),1015(sdcard_rw),1028(sdcard_r),1078(ext_data_rw),1079(ext_obb_rw),3001(net_bt_admin),3002(net_bt),3003(inet),3006(net_bw_stats),3009(readproc),3011(uhid),3012(readtracefs) context=u:r:su:s0
```

### D_POCO (4f4027e9)
```
$ getprop ro.build.version.sdk
35
$ getprop ro.build.fingerprint
POCO/surya_eea/surya:12/RKQ1.211019.001/V14.0.5.0.SJGEUXM:user/release-keys
$ dumpsys package com.android.shell | grep -E 'BLUETOOTH(_PRIVILEGED|_CONNECT|_ADMIN)?[:" ]'
      android.permission.BLUETOOTH: granted=true
      android.permission.BLUETOOTH_ADMIN: granted=true
      android.permission.BLUETOOTH_PRIVILEGED: granted=true
      android.permission.BLUETOOTH: granted=true
      android.permission.BLUETOOTH_ADMIN: granted=true
      android.permission.BLUETOOTH_PRIVILEGED: granted=true
        android.permission.BLUETOOTH_CONNECT: granted=true, flags=[ SYSTEM_FIXED|GRANTED_BY_DEFAULT|RESTRICTION_SYSTEM_EXEMPT|RESTRICTION_UPGRADE_EXEMPT]
$ settings get global hidden_api_policy
null
$ id
uid=2000(shell) gid=2000(shell) groups=2000(shell),1004(input),1007(log),1011(adb),1015(sdcard_rw),1028(sdcard_r),1078(ext_data_rw),1079(ext_obb_rw),3001(net_bt_admin),3002(net_bt),3003(inet),3006(net_bw_stats),3009(readproc),3011(uhid),3012(readtracefs) context=u:r:shell:s0
```

### D_MOTO (ZY22GC3BM4)
```
$ getprop ro.build.version.sdk
34
$ getprop ro.build.fingerprint
motorola/miami_g/miami:14/U1SSMS34.31-64-10-9/b49a1d-203708:user/release-keys
$ dumpsys package com.android.shell | grep -E 'BLUETOOTH(_PRIVILEGED|_CONNECT|_ADMIN)?[:" ]'
      android.permission.BLUETOOTH: granted=true
      android.permission.BLUETOOTH_ADMIN: granted=true
      android.permission.BLUETOOTH_PRIVILEGED: granted=true
      android.permission.BLUETOOTH: granted=true
      android.permission.BLUETOOTH_ADMIN: granted=true
      android.permission.BLUETOOTH_PRIVILEGED: granted=true
        android.permission.BLUETOOTH_CONNECT: granted=true, flags=[ SYSTEM_FIXED|GRANTED_BY_DEFAULT|RESTRICTION_SYSTEM_EXEMPT|RESTRICTION_UPGRADE_EXEMPT]
$ settings get global hidden_api_policy
null
$ id
uid=2000(shell) gid=2000(shell) groups=2000(shell),1004(input),1007(log),1011(adb),1015(sdcard_rw),1028(sdcard_r),1078(ext_data_rw),1079(ext_obb_rw),3001(net_bt_admin),3002(net_bt),3003(inet),3006(net_bw_stats),3009(readproc),3011(uhid),3012(readtracefs) context=u:r:shell:s0
```

### D_SAM (30041c35642d2200)
```
$ getprop ro.build.version.sdk
19
$ getprop ro.build.fingerprint
samsung/degaswifixx/degaswifi:4.4.2/KOT49H/T230XXU0ANJ4:user/release-keys
$ dumpsys package com.android.shell | grep -E 'BLUETOOTH(_PRIVILEGED|_CONNECT|_ADMIN)?[:" ]'
$ settings get global hidden_api_policy
null
$ id
uid=2000(shell) gid=2000(shell) groups=1003(graphics),1004(input),1007(log),1011(adb),1015(sdcard_rw),1028(sdcard_r),3001(net_bt_admin),3002(net_bt),3003(inet),3006(net_bw_stats) context=u:r:shell:s0
```

### D_HP (CNU350BGBJ)
```
$ getprop ro.build.version.sdk
17
$ getprop ro.build.fingerprint
hp/white/birch:4.2.2/JDQ39/0.0.43:user/release-keys
$ dumpsys package com.android.shell | grep -E 'BLUETOOTH(_PRIVILEGED|_CONNECT|_ADMIN)?[:" ]'
$ settings get global hidden_api_policy
null
$ id
uid=2000(shell) gid=2000(shell) groups=1003(graphics),1004(input),1007(log),1009(mount),1011(adb),1015(sdcard_rw),1028(sdcard_r),3001(net_bt_admin),3002(net_bt),3003(inet),3006(net_bw_stats)
```
