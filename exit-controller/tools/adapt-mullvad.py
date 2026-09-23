#!/usr/bin/env python3
"""Reproducible adaptation of the pinned Mullvad renderer to Android Views.
The GL algorithms and geometry are upstream; Compose-only value types are replaced
by small platform-neutral types, and the Activity owns the GL lifecycle.
"""
from pathlib import Path
import shutil,json
root=Path(__file__).resolve().parents[1]
up=root/'vendor/mullvad/android/lib/map/src/main/kotlin'
out=root/'app/src/main/kotlin'
notice='// SPDX-License-Identifier: GPL-3.0-only\n// Adapted from Mullvad VPN; see THIRD_PARTY_NOTICES.md for revision and changes.\n'
for p in up.rglob('*.kt'):
 rel=p.relative_to(up)
 if '/preview/' in str(rel) or p.name in ('Map.kt','InteractiveMap.kt','CameraAnimation.kt','MapSurfaceView.kt'):continue
 s=p.read_text()
 s=s.replace('import androidx.compose.runtime.Immutable\n','').replace('@Immutable\n','')
 s=s.replace('import androidx.compose.ui.graphics.Color','import one.hbx.exitcontroller.map.Color')
 s=s.replace('import androidx.compose.ui.geometry.', 'import one.hbx.exitcontroller.map.')
 s=s.replace('import androidx.compose.animation.core.', 'import one.hbx.exitcontroller.map.')
 s=s.replace('import androidx.collection.LruCache','import android.util.LruCache')
 s=s.replace('import co.touchlab.kermit.Logger\n','').replace('Logger.e(', 'android.util.Log.e("ExitGlobe", ')
 s=s.replace('import net.mullvad.mullvadvpn.lib.map.R','import one.hbx.exitcontroller.R')
 if p.name=='GlobeColors.kt':
  s=s[:s.index('\nobject GlobeDefaults')]
  s=s.replace('import androidx.compose.material3.MaterialTheme\n','').replace('import androidx.compose.runtime.Composable\n','')
 if p.name=='Globe.kt':
  s=s.replace('varying lowp vec4 vColor;', 'varying lowp vec4 vColor;\n            varying mediump vec3 vNormal;')
  s=s.replace('vColor = uColor;', 'vColor = uColor;\n                vNormal = normalize(mat3(uModelViewMatrix) * aVertexPosition);')
  s=s.replace('gl_FragColor = vColor;', 'mediump vec3 normal = normalize(vNormal);\n                mediump float light = 0.72 + 0.28 * max(dot(normal, normalize(vec3(-0.4, 0.65, 1.0))), 0.0);\n                mediump float rim = pow(1.0 - abs(normal.z), 3.0);\n                gl_FragColor = vec4(mix(vColor.rgb * light, vec3(0.38, 0.66, 0.85), rim * 0.38), vColor.a);')
 dest=out/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(notice+s)
for p in (root/'vendor/mullvad/android/lib/model/src/main/kotlin').rglob('*.kt'):
 rel=p.relative_to(root/'vendor/mullvad/android/lib/model/src/main/kotlin')
 s=p.read_text().replace('import android.os.Parcelable\n','').replace('import kotlinx.parcelize.Parcelize\n','').replace('@Parcelize\n','').replace(' : Parcelable','')
 dest=out/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(notice+s)
raw=root/'app/src/main/res/raw';raw.mkdir(exist_ok=True)
for p in (root/'vendor/mullvad/dist-assets/geo').iterdir():shutil.copyfile(p,raw/p.name)
shutil.copyfile(root/'vendor/mullvad/LICENSE.md',root/'LICENSE.md')
