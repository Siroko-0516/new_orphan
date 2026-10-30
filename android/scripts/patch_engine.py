"""Guard two Darwin-only cache hints in the pinned engine for Android builds."""
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

if __name__ == '__main__':
    patch(Path(__file__).resolve().parents[1] / 'third_party/llama.cpp-omni')
