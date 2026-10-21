"""Check the built DEX defines app classes but excludes the vendor stub."""
import argparse
import struct
import zipfile


def u32(data, offset):
    return struct.unpack_from('<I', data, offset)[0]


def defined_classes(data):
    if not data.startswith(b'dex\n'):
        raise ValueError('Expected a standard DEX file')
    strings = []
    for index in range(u32(data, 0x38)):
        offset = u32(data, u32(data, 0x3c) + index * 4)
        # Skip the ULEB128 UTF-16 length; class descriptors are ASCII.
        while data[offset] & 0x80:
            offset += 1
        offset += 1
        strings.append(data[offset:data.index(b'\0', offset)].decode('utf-8', errors='replace'))
    types = [strings[u32(data, u32(data, 0x44) + index * 4)]
             for index in range(u32(data, 0x40))]
    return {types[u32(data, u32(data, 0x64) + index * 32)]
            for index in range(u32(data, 0x60))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk')
    args = parser.parse_args()
    with zipfile.ZipFile(args.apk) as archive:
        classes = set()
        for name in archive.namelist():
            if name.startswith('classes') and name.endswith('.dex'):
                classes.update(defined_classes(archive.read(name)))
        if 'Lio/github/sunlho/haierroot/MainActivity;' not in classes:
            raise SystemExit('APK is missing MainActivity')
        if any(name.startswith('Lcom/mstar/') for name in classes):
            raise SystemExit('Compile-only vendor stubs were packaged into the APK')
        if 'assets/adbd-root-patch.json' not in archive.namelist():
            raise SystemExit('APK is missing the firmware patch metadata')
    print('Verified: app classes and patch present; vendor stubs excluded')


if __name__ == '__main__':
    main()
