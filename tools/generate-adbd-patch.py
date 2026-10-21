"""Generate the verified M638 adbd patch; developer-only keystone/capstone use."""
import argparse
import hashlib
import json
from pathlib import Path
from capstone import Cs, CS_ARCH_ARM, CS_MODE_THUMB
from keystone import Ks, KS_ARCH_ARM, KS_MODE_THUMB

BASE = Path(__file__).resolve().parent.parent
ORIGINAL_SHA256 = '8380fb3109225cae45335ede3653686a38a2ea8ad0b7c77699876b975e42ef0f'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('original', type=Path)
    args = parser.parse_args()
    original = args.original.read_bytes()
    if hashlib.sha256(original).hexdigest() != ORIGINAL_SHA256:
        raise RuntimeError('Unrecognized firmware binary')
    ks = Ks(KS_ARCH_ARM, KS_MODE_THUMB)
    cs = Cs(CS_ARCH_ARM, CS_MODE_THUMB)
    changes = []
    result = bytearray(original)

    def assemble(address, source, size, name):
        code = bytes(ks.asm(source, address)[0])
        if len(code) > size or (size - len(code)) % 2:
            raise RuntimeError('Patch slot overflow: ' + name)
        code += bytes.fromhex('00bf') * ((size - len(code)) // 2)
        offset = address - 0x8000
        changes.append({'name': name, 'offset': offset,
                        'before': original[offset:offset + size].hex(), 'after': code.hex()})
        result[offset:offset + size] = code
        print(name, hex(address), len(code), 'bytes')
        decoded = list(cs.disasm(code, address))
        if name == 'startup_property_gate':
            assert any(i.mnemonic == 'b.w' and i.op_str == '#0x92a0' for i in decoded)
            assert any(i.mnemonic == 'b.w' and i.op_str == '#0x921c' for i in decoded)
        for i in decoded:
            print('  %08x %-8s %s' % (i.address, i.mnemonic, i.op_str))

    # Preserve the original capability and UID/GID drop path for normal shell.
    assemble(0x9218, 'b.w 0x2a050', 4, 'startup_gate_branch')
    assemble(0x2a050, '''
        ldr r0, property_name
        add r1, sp, #0x38
        movs r2, #0
        bl 0x13d36
        ldrb.w r0, [sp, #0x38]
        cmp r0, #0x31
        bne drop_to_shell
        b.w 0x92a0
    drop_to_shell:
        movs r4, #0
        movs r5, #0
        b.w 0x921c
        .align 2
    property_name:
        .word 0x2a080
    ''', 48, 'startup_property_gate')
    # The removed production-denial code becomes the request restart path.
    # r4 must still point to "1" when the original property_set executes.
    assemble(0xfce6, '''
        ldr r4, one_string
        b 0xfd1e
        bl 0x195b4
        add r1, sp, #0x60
        mov r2, r0
        mov r0, r7
        bl 0xc334
        mov r0, r7
        blx 0x17d28
        movw r0, #0xa120
        movt r0, #7
        bl 0x1f878
        movs r0, #0
        blx 0x23908
        .align 2
    one_string:
        .word 0x2a04c
    ''', 56, 'allow_root_and_restart')
    assemble(0xfd3e, 'b 0xfcea', 2, 'root_success_restart_branch')
    metadata = {'model': 'M638_HAIER', 'original_sha256': ORIGINAL_SHA256,
                'patched_sha256': hashlib.sha256(result).hexdigest(),
                'root_property': 'service.adb.root', 'patches': changes}
    assets = BASE / 'app/src/main/assets'
    assets.mkdir(parents=True, exist_ok=True)
    (assets / 'adbd-root-patch.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
    output = BASE / 'build/adbd-with-root-command'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print('Patched SHA256:', metadata['patched_sha256'])
    print('Output:', output)


if __name__ == '__main__':
    main()
