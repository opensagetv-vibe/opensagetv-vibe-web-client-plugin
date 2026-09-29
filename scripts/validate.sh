#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
node --check src/main/webapp/js/app.js
node --check src/main/webapp/js/advanced-ui.js
node --check src/main/webapp/js/session-state.js
node --check src/main/webapp/js/library-utils.js
node --check src/main/webapp/js/atsc-captions.js
node --check src/main/webapp/js/wasm-decoder-adapter.js
node --check src/main/webapp/js/performance-watchdog.js
node --check src/main/webapp/js/fmp4-fallback.js
node --check src/main/webapp/js/commercial-skip.js
node --check src/main/webapp/js/remote-navigation.js
node --check src/main/webapp/js/pwa.js
node --check src/main/webapp/js/miniclient-core.js
node --check src/main/webapp/js/miniclient-streaming.js
node --check src/main/webapp/js/miniclient-caption-authority.js
node --check src/main/webapp/js/miniclient-captions.js
node --check src/main/webapp/js/miniclient-subtitles.js
node --check src/main/webapp/js/miniclient-dvd-spu.js
node --check src/main/webapp/js/miniclient-playback.js
node --check src/main/webapp/js/miniclient-support.js
node --check src/main/webapp/js/miniclient.js
node --check src/main/webapp/sw.js
node tests/test-captions608.js
node tests/test-captions708.js
node tests/test-performance.js
node tests/test-session-state.js
node tests/test-session-state-v2.js
node tests/test-library-utils.js
node tests/test-commercial-skip.js
node tests/test-miniclient-core.js
node tests/test-miniclient-display.js
python scripts/verify-vibe-icons.py
node tests/test-miniclient-playback.js
node tests/test-caption-authority.js
node tests/test-stv-caption-control.js
node tests/test-ordinary-subtitles.js
node tests/test-p14-support.js
python - <<'PY'
import hashlib, pathlib, xml.etree.ElementTree as ET, zipfile, re
root=pathlib.Path('.')
ET.parse(root/'src/main/webapp/WEB-INF/web.xml')
import json
json.loads((root/'src/main/webapp/manifest.webmanifest').read_text())
index=(root/'src/main/webapp/index.html').read_text()
for removed in ['Server Watch History','Tuners / Encoder Health','SageTV Favorites / Recording Rules','DVR Schedule']:
    assert removed not in index, removed
assert 'simple-recording-listbox' in index
assert 'src="js/advanced-ui.js"' not in index
mc=(root/'src/main/webapp/js/miniclient.js').read_text()
assert 'const dw=Math.abs(w),dh=Math.abs(h)' in mc
playback=(root/'src/main/webapp/js/miniclient-playback.js').read_text()
assert 'async waitServerBuffer' in playback and 'async hlsHealth' in playback and 'async recover' in playback
assert "requestJson('api/hls'" in mc
assert "Core.logicalSize(rect.width,rect.height,settings.uiResolution)" in mc
assert "queueInput('resize'" in mc
assert 'function layoutStage()' in mc
assert "Core.stageRect(sr.width,sr.height,uiW,uiH," in mc
assert "visible.style.width=d.width+'px'" in mc
css=(root/'src/main/webapp/css/miniclient.css').read_text(); assert 'max-width:1280px' not in css and 'height:100dvh' in css
assert "c.scale(w<0?-1:1,h<0?-1:1)" not in mc
assert "if(h<0){" in mc and "globalCompositeOperation='copy'" in mc
manifest_path=root/'dist/local-install/SageTVPluginsDev.d/sagetv-webplayer.xml'
ET.parse(manifest_path)
manifest=manifest_path.read_text()
windows_manifest_path=root/'dist/local-install/SageTVPluginsDev.d/sagetv-webplayer-windows.xml'
ET.parse(windows_manifest_path)
windows_manifest=windows_manifest_path.read_text()
m=re.search(r'<Version(?:\s+[^>]*)?>([^<]+)</Version>',manifest); assert m and m.group(1)=='3.2.30'
m2=re.search(r'<MD5>([0-9a-f]+)</MD5>',manifest)
pkg=root/'dist/local-install/SageTVPluginsDev.d/sagetv-webplayer-3.2.30.zip'
actual=hashlib.md5(pkg.read_bytes()).hexdigest()
assert m2 and m2.group(1)==actual,(m2.group(1) if m2 else None,actual)
with zipfile.ZipFile(pkg) as z:
    assert 'jetty/webapps/SageTVWebPlayer.war' in z.namelist()
with zipfile.ZipFile(root/'dist/SageTVWebPlayer.war') as z:
    names=set(z.namelist())
    for required in ['index.html','miniclient.html','css/miniclient.css','js/miniclient.js','js/miniclient-core.js','js/miniclient-playback.js','js/miniclient-streaming.js','js/miniclient-caption-authority.js','js/miniclient-captions.js','js/miniclient-subtitles.js','js/miniclient-dvd-spu.js','js/miniclient-support.js','js/app.js','js/advanced-ui.js','js/session-state.js','js/library-utils.js','js/atsc-captions.js','js/wasm-decoder-adapter.js','js/performance-watchdog.js','js/fmp4-fallback.js','js/commercial-skip.js','js/remote-navigation.js','js/pwa.js','sw.js','codec-test.html','manifest.webmanifest','icons/icon-192.png','icons/icon-512.png','WEB-INF/web.xml']:
        assert required in names, required
    for cls in ['MiniClientCrypto','ContinuousStreamServlet','StreamOptions','StreamPlan','StreamCommand','A53CaptionTap','CaptionsServlet','MediaProbe','ContinuousFilePump','PlaybackSessionContext','SubtitleCue','SubtitleCueQueue','SubtitleClock','SubtitleCapabilities','SubtitleSourceBranch','TsServiceInventoryProbe','SubtitleCueServlet','SubtitleLimits','SubtitleWorkBudget','TeletextSubtitleSession','TeletextSourcePump','DvbBitmapDecoder','DvbSubtitleSession','DvbSourcePump','LegacyExtenderCaptionBridge','TeletextCea608Bridge','OrdinarySubtitleSession','DvdPtsClock','DvdPushBuffer','DvdNativeSession','DvdAudioStreamCode','DvdPsInspector','MiniClientCapabilityPolicy','MediaUrlPath','AuthorizedPathMatcher','AuthorizedMediaResolver','DiscCapabilityProbe','DiscSourceInfo','DiscMetadataHealth','DiscTitleProbe','DiscInspection','DiscInfoServlet','MpegTsSeekIndex','AudioTrackPolicy','GfxOpcodeLedger','DiagnosticSupport','DiagnosticSupportServlet','CodecHlsTestManager','CodecHlsTestServlet','CodecHlsTestMediaServlet']:
        assert 'WEB-INF/classes/org/opensagetv/webplayer/'+cls+'.class' in names,cls
    assert '3.2.30' in z.read('miniclient.html').decode()
    assert '<Plugin>SageTVFFmpegPluginLinux</Plugin>' in manifest
    assert '<OS>Linux</OS>' in manifest
    assert '<Plugin>SageTVFFmpegPluginWinx64</Plugin>' in windows_manifest
    assert '<OS>Windows</OS>' in windows_manifest
print('XML/package/WAR/MD5/new streaming classes OK')
PY
first_artifact_hashes="$(sha256sum \
  dist/SageTVWebPlayer.war \
  dist/local-install/SageTVPluginsDev.d/sagetv-webplayer-3.2.30.zip \
  dist/local-install/SageTVPluginsDev.d/sagetv-webplayer.xml \
  dist/local-install/SageTVPluginsDev.d/sagetv-webplayer-windows.xml)"
bash scripts/build-local.sh >/dev/null
python3 scripts/package-local-plugin.py >/dev/null
second_artifact_hashes="$(sha256sum \
  dist/SageTVWebPlayer.war \
  dist/local-install/SageTVPluginsDev.d/sagetv-webplayer-3.2.30.zip \
  dist/local-install/SageTVPluginsDev.d/sagetv-webplayer.xml \
  dist/local-install/SageTVPluginsDev.d/sagetv-webplayer-windows.xml)"
if [[ "$first_artifact_hashes" != "$second_artifact_hashes" ]]; then
  echo 'Artifact reproducibility check failed.' >&2
  diff <(printf '%s\n' "$first_artifact_hashes") <(printf '%s\n' "$second_artifact_hashes") >&2 || true
  exit 1
fi
echo 'PASS deterministic WAR, plugin ZIP, and Linux/Windows manifests'
javap -verbose build/manual/classes/org/opensagetv/webplayer/HealthServlet.class | grep 'major version: 52'
mkdir -p build/manual/test-classes
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/PlaybackArchitectureSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.PlaybackArchitectureSmoke
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/HardwarePipelineSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.HardwarePipelineSmoke
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/TranscoderPluginResolutionSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.TranscoderPluginResolutionSmoke
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/DiscSourceSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.DiscSourceSmoke "${TEST_FFMPEG:-/usr/bin/ffmpeg}"
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/P11TimelineSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.P11TimelineSmoke "${TEST_FFMPEG:-/usr/bin/ffmpeg}"
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/P13GfxLifecycleSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.P13GfxLifecycleSmoke
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/P14DiagnosticSupportSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.P14DiagnosticSupportSmoke
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/CodecHlsTestSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.CodecHlsTestSmoke "${TEST_FFMPEG:-/usr/bin/ffmpeg}"
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/P12AudioPolicySmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.P12AudioPolicySmoke
node tests/test-p12-streaming.js
if [[ -x "${TEST_FFMPEG:-/usr/bin/ffmpeg}" ]]; then
  javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/P12AvCalibrationSmoke.java
  java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.P12AvCalibrationSmoke "${TEST_FFMPEG:-/usr/bin/ffmpeg}"
fi
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/DvdNativeProtocolSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.DvdNativeProtocolSmoke
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/DvdSpuSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.DvdSpuSmoke
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/MiniClientDvdWireSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.MiniClientDvdWireSmoke "${TEST_FFMPEG:-/usr/bin/ffmpeg}"
if [[ -x "${TEST_FFMPEG:-/usr/bin/ffmpeg}" ]]; then
  javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/DvdPlaybackRuntimeSmoke.java
  java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.DvdPlaybackRuntimeSmoke "${TEST_FFMPEG:-/usr/bin/ffmpeg}"
  javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/DvdStartupFallbackSmoke.java
  java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.DvdStartupFallbackSmoke "${TEST_FFMPEG:-/usr/bin/ffmpeg}"
else
  echo 'SKIP P08 DVD A/V runtime: FFmpeg missing'
fi
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/LegacyCaptionBridgeSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.LegacyCaptionBridgeSmoke
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/MiniClientProtocolSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.MiniClientProtocolSmoke
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/MiniClientRecoverySmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.MiniClientRecoverySmoke
javac --release 8 -cp build/manual/classes:build/manual/test-classes -d build/manual/test-classes tests/MiniClientStartupSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.MiniClientStartupSmoke
javac --release 8 -cp build/manual/classes:build/manual/test-classes -d build/manual/test-classes tests/MiniClientAuthenticationSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.MiniClientAuthenticationSmoke
if [[ -x "${TEST_FFMPEG:-/usr/bin/ffmpeg}" ]]; then
  javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/HlsRuntimeSmoke.java
  java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.HlsRuntimeSmoke
else
  echo 'SKIP HLS runtime integration: set TEST_FFMPEG to an executable FFmpeg'
fi
SERVLET_JAR="${SERVLET_API_JAR:-/usr/share/java/servlet-api.jar}"
javac --release 8 -cp "build/manual/classes:$SERVLET_JAR" -d build/manual/test-classes tests/StreamingServletSmoke.java
java -cp "build/manual/classes:build/manual/test-classes:$SERVLET_JAR" org.opensagetv.webplayer.StreamingServletSmoke
if [[ -x "${TEST_FFMPEG:-/usr/bin/ffmpeg}" ]]; then
  javac --release 8 -cp "build/manual/classes:$SERVLET_JAR" -d build/manual/test-classes tests/StreamingRuntimeSmoke.java
  java -cp "build/manual/classes:build/manual/test-classes:$SERVLET_JAR" org.opensagetv.webplayer.StreamingRuntimeSmoke
else
  echo 'SKIP real TS/CC integration: FFmpeg missing'
fi
node tests/test-mpegts-streaming.js

javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/TeletextSubtitleSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.TeletextSubtitleSmoke
node tests/test-teletext-controller.js
javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/DvbSubtitleSmoke.java
java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.DvbSubtitleSmoke
node tests/test-dvb-controller.js
if [[ -x "${TEST_FFMPEG:-/usr/bin/ffmpeg}" ]]; then
  javac --release 8 -cp build/manual/classes -d build/manual/test-classes tests/OrdinarySubtitleSmoke.java
  java -cp build/manual/classes:build/manual/test-classes org.opensagetv.webplayer.OrdinarySubtitleSmoke tests/parity/fixtures "${TEST_FFMPEG:-/usr/bin/ffmpeg}"
else
  echo 'SKIP P06 ordinary subtitle integration: FFmpeg missing'
fi
if [[ "${RUN_BROWSER_TESTS:-0}" == 1 ]]; then
  python tests/browser-smoke.py
  python tests/browser-layout-smoke.py
  python tests/browser-streaming-smoke.py
  python tests/browser-teletext-smoke.py
  python tests/browser-dvb-smoke.py
  python tests/browser-ordinary-subtitles-smoke.py
  python tests/browser-dvd-smoke.py
  python tests/browser-recovery-smoke.py
  python tests/browser-vibe-display-smoke.py
  python tests/browser-p13-smoke.py
  python tests/browser-p14-smoke.py
else
  echo 'SKIP offline Chromium smoke: use RUN_BROWSER_TESTS=1 with playwright/Chromium installed'
fi
echo 'Validation OK'
