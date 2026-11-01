"""Guard Darwin cache hints and select four runtime-dispatched Android CPU variants."""
from pathlib import Path

def patch(root):
    path = root / 'src/llama-moe-slot.cpp'
    source = path.read_text(encoding='utf-8')
    old = 'void apply_experts_nocache(bool on) {\n#ifndef _WIN32'
    new = 'void apply_experts_nocache(bool on) {\n#if defined(F_NOCACHE) && !defined(_WIN32)'
    if old in source:
        source = source.replace(old, new, 1)
    elif new not in source:
        raise RuntimeError('Unexpected engine cache-hint implementation')
    old2 = '''        if (experts_nocache_desired() && fcntl(fd, F_NOCACHE, 1) != 0) {
            moe_log("F_NOCACHE 设置失败: %s", std::strerror(errno));
        }'''
    new2 = '#if defined(F_NOCACHE)\n' + old2 + '\n#endif'
    if new2 not in source:
        if old2 not in source:
            raise RuntimeError('Unexpected engine fd cache-hint implementation')
        source = source.replace(old2, new2, 1)
    path.write_text(source, encoding='utf-8')
    # Keep baseline, dot-product, FP16 and i8mm; do not require SVE/SME for phone acceleration.
    cmake = root / 'ggml/src/CMakeLists.txt'
    source = cmake.read_text(encoding='utf-8')
    for variant in ('android_armv9.0_1', 'android_armv9.2_1', 'android_armv9.2_2'):
        lines = source.splitlines(keepends=True)
        source = ''.join(line for line in lines if 'ggml_add_cpu_backend_variant(' + variant not in line)
    for variant in ('android_armv8.0_1', 'android_armv8.2_1', 'android_armv8.2_2', 'android_armv8.6_1'):
        if 'ggml_add_cpu_backend_variant(' + variant not in source:
            raise RuntimeError('Missing expected Android CPU variant: ' + variant)
    cmake.write_text(source, encoding='utf-8')
    cpu = root / 'ggml/src/ggml-cpu/CMakeLists.txt'
    source = cpu.read_text(encoding='utf-8')
    source = source.replace('URL_HASH MD5=${KLEIDIAI_RELEASE_ARCHIVE_MD5}', 'URL_HASH SHA256=9348b969e042d8890a54b01a463dbe71f5a4c074b5329e9c26a85ef3b68aa19b')
    if 'URL_HASH SHA256=9348b969e042d8890a54b01a463dbe71f5a4c074b5329e9c26a85ef3b68aa19b' not in source:
        raise RuntimeError('Unexpected KleidiAI archive pin')
    cpu.write_text(source, encoding='utf-8')

if __name__ == '__main__':
    patch(Path(__file__).resolve().parents[1] / 'third_party/llama.cpp-omni')
