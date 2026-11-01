"""Check ELF PT_LOAD alignment in the actual APK/AAB; does not replace 16 KB runtime tests."""
import struct
import sys
import zipfile

failures = []
checked = 0
with zipfile.ZipFile(sys.argv[1]) as archive:
    for name in archive.namelist():
        if not name.endswith('.so') or '/arm64-v8a/' not in name and '/x86_64/' not in name:
            continue
        data = archive.read(name)
        checked += 1
        if data[:5] != b'\x7fELF\x02' or data[5] != 1:
            failures.append(f'{name}: unsupported ELF format')
            continue
        offset = struct.unpack_from('<Q', data, 32)[0]
        entry_size, count = struct.unpack_from('<HH', data, 54)
        for index in range(count):
            position = offset + entry_size * index
            kind = struct.unpack_from('<I', data, position)[0]
            alignment = struct.unpack_from('<Q', data, position + 48)[0]
            if kind == 1 and alignment < 16384:
                failures.append(f'{name}: PT_LOAD alignment {alignment} < 16384')
print(f'Checked {checked} 64-bit native libraries')
for failure in failures:
    print(failure)
if not checked:
    print('No 64-bit libraries found; confirm the artifact and intended ABIs.')
sys.exit(1 if failures else 0)
