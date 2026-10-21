"""Build Haier Root with local JDK/Android SDK tools; no Gradle or pip packages."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

BASE = Path(__file__).resolve().parent
ANDROID = 'http://schemas.android.com/apk/res/android'
EXE = '.exe' if os.name == 'nt' else ''


def run(command, env=None):
    result = subprocess.run([str(value) for value in command], cwd=str(BASE),
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env)
    output = result.stdout.decode('utf-8', errors='replace')
    if result.returncode:
        log = BASE / 'build/standalone-error.log'
        log.write_text(output, encoding='utf-8')
        raise RuntimeError('\n'.join(output.splitlines()[:12]) + '\nFull output: ' + str(log))
    return output


def find_jdk(explicit):
    candidates = [explicit, os.environ.get('JAVA_HOME')]
    javac = shutil.which('javac')
    if javac:
        candidates.append(Path(javac).resolve().parent.parent)
    for value in candidates:
        if value and (Path(value) / ('bin/javac' + EXE)).is_file():
            return Path(value).resolve()
    raise RuntimeError('Set JAVA_HOME to JDK 17 or pass --jdk')


def find_sdk(explicit):
    candidates = [explicit, os.environ.get('ANDROID_HOME'), os.environ.get('ANDROID_SDK_ROOT')]
    properties = BASE / 'local.properties'
    if properties.is_file():
        for line in properties.read_text(encoding='utf-8').splitlines():
            if line.strip().startswith('sdk.dir='):
                candidates.append(line.split('=', 1)[1].strip().replace(chr(92) * 2, chr(92)).replace(chr(92) + ':', ':'))
    if os.environ.get('LOCALAPPDATA'):
        candidates.append(Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk')
    candidates.extend([Path.home() / 'Android/Sdk', Path.home() / 'Library/Android/sdk'])
    for value in candidates:
        if value and (Path(value) / 'platforms').is_dir():
            return Path(value).resolve()
    raise RuntimeError('Set ANDROID_HOME to the Android SDK or pass --sdk')


def build_tools(sdk, explicit):
    def usable(path):
        return all((path / name).is_file() for name in
                   ['aapt' + EXE, 'zipalign' + EXE, 'lib/d8.jar', 'lib/apksigner.jar'])
    if explicit:
        if not usable(explicit):
            raise RuntimeError('Build Tools must contain aapt, zipalign, d8.jar and apksigner.jar')
        return explicit.resolve()
    candidates = [path for path in (sdk / 'build-tools').glob('*')
                  if re.fullmatch(r'\d+\.\d+\.\d+', path.name) and usable(path)]
    if not candidates:
        raise RuntimeError('Install Android SDK Build Tools 35+ or pass --build-tools')
    return max(candidates, key=lambda path: tuple(map(int, path.name.split('.'))))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', type=Path)
    parser.add_argument('--sdk', type=Path)
    parser.add_argument('--build-tools', type=Path)
    parser.add_argument('--android-jar', type=Path)
    args = parser.parse_args()
    jdk = find_jdk(args.jdk)
    sdk = find_sdk(args.sdk)
    tools = build_tools(sdk, args.build_tools)
    platforms = [path for path in (sdk / 'platforms').glob('android-*')
                 if re.match(r'android-(\d+)', path.name)
                 and int(re.match(r'android-(\d+)', path.name).group(1)) >= 19
                 and (path / 'android.jar').is_file()]
    if not platforms and not args.android_jar:
        raise RuntimeError('Install an Android SDK Platform with API 19 or higher')
    android = args.android_jar or min(platforms, key=lambda path:
                                    int(re.match(r'android-(\d+)', path.name).group(1))) / 'android.jar'
    if not android.is_file():
        raise RuntimeError('Missing android.jar: ' + str(android))
    config = (BASE / 'app/build.gradle').read_text(encoding='utf-8')
    def value(name):
        match = re.search(r'\b' + name + r"\s+(?:'([^']+)'|(\d+))", config)
        if not match:
            raise RuntimeError('Missing Android build value: ' + name)
        return match.group(1) or match.group(2)
    package, minimum = value('applicationId'), value('minSdk')
    build = BASE / 'build'
    build.mkdir(exist_ok=True)
    signing_env = dict(os.environ)
    signing_names = ['ANDROID_KEYSTORE_FILE', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD']
    if any(signing_env.get(name) for name in signing_names):
        if not all(signing_env.get(name) for name in signing_names):
            raise RuntimeError('Custom signing requires all four ANDROID_KEYSTORE_* / ANDROID_KEY_* variables')
        key = Path(signing_env['ANDROID_KEYSTORE_FILE']).resolve()
        alias = signing_env['ANDROID_KEY_ALIAS']
        if not key.is_file():
            raise RuntimeError('Signing keystore does not exist: ' + str(key))
    else:
        existing = Path.home() / '.android/debug.keystore'
        key = existing if existing.is_file() else BASE / '.signing/debug.keystore'
        alias = 'androiddebugkey'
        signing_env.update(ANDROID_KEYSTORE_PASSWORD='android', ANDROID_KEY_PASSWORD='android')
        if not key.is_file():
            key.parent.mkdir(parents=True, exist_ok=True)
            run([jdk / ('bin/keytool' + EXE), '-genkeypair', '-keystore', key, '-alias', alias,
                 '-storepass:env', 'ANDROID_KEYSTORE_PASSWORD', '-keypass:env', 'ANDROID_KEY_PASSWORD',
                 '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Android Debug', '-noprompt'], signing_env)
    with tempfile.TemporaryDirectory(prefix='standalone-', dir=str(build)) as temporary:
        work = Path(temporary)
        if build.resolve() not in work.resolve().parents:
            raise RuntimeError('Temporary directory escaped build directory')
        generated, classes, dex = work / 'generated', work / 'classes', work / 'dex'
        for path in (generated, classes, dex):
            path.mkdir()
        ET.register_namespace('android', ANDROID)
        manifest = ET.parse(BASE / 'app/src/main/AndroidManifest.xml')
        node = manifest.getroot()
        node.set('package', package)
        node.set('{%s}versionCode' % ANDROID, value('versionCode'))
        node.set('{%s}versionName' % ANDROID, value('versionName'))
        uses_sdk = ET.Element('uses-sdk', {'{%s}minSdkVersion' % ANDROID: minimum,
                                         '{%s}targetSdkVersion' % ANDROID: value('targetSdk')})
        node.insert(0, uses_sdk)
        node.find('application').set('{%s}debuggable' % ANDROID, 'false')
        manifest_path = work / 'AndroidManifest.xml'
        manifest.write(manifest_path, encoding='utf-8', xml_declaration=True)
        unsigned = work / 'unsigned.apk'
        run([tools / ('aapt' + EXE), 'package', '-f', '-m', '-M', manifest_path,
             '-S', BASE / 'app/src/main/res', '-A', BASE / 'app/src/main/assets',
             '-I', android, '-J', generated, '-F', unsigned])
        sources = [*sorted((BASE / 'app/src/main/java').rglob('*.java')), *sorted(generated.rglob('*.java'))]
        run([jdk / ('bin/javac' + EXE), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
             '-bootclasspath', android, '-d', classes, *sources])
        java = jdk / ('bin/java' + EXE)
        run([java, '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', minimum,
             '--lib', android, '--output', dex, *sorted(classes.rglob('*.class'))])
        with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(dex.glob('*.dex')):
                archive.write(path, path.name)
        aligned = work / 'aligned.apk'
        run([tools / ('zipalign' + EXE), '-f', '4', unsigned, aligned])
        apk = build / 'haier-root.apk'
        run([java, '-jar', tools / 'lib/apksigner.jar', 'sign', '--ks', key, '--ks-key-alias', alias,
             '--ks-pass', 'env:ANDROID_KEYSTORE_PASSWORD', '--key-pass', 'env:ANDROID_KEY_PASSWORD',
             '--min-sdk-version', minimum, '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true',
             '--v3-signing-enabled', 'false', '--out', apk, aligned], signing_env)
        signature = run([java, '-jar', tools / 'lib/apksigner.jar', 'verify', '--verbose', '--min-sdk-version', minimum, apk])
        if 'Verified using v1 scheme (JAR signing): true' not in signature:
            raise RuntimeError('APK is missing its Android 4.4-compatible v1 signature')
        run([sys.executable, BASE / 'tools/verify-apk.py', apk])
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    (build / 'SHA256SUMS').write_text(digest + '  ' + apk.name + '\n', encoding='ascii')
    print('JDK:', jdk)
    print('Android platform:', android.parent.name)
    print('Build tools:', tools.name)
    print('Signing key:', key)
    print('APK:', apk)
    print('APK bytes:', apk.stat().st_size)
    print('SHA-256:', digest)
    print('Verified v1/v2 signatures and vendor class isolation.')


if __name__ == '__main__':
    try:
        main()
    except (OSError, RuntimeError) as error:
        raise SystemExit(str(error))
