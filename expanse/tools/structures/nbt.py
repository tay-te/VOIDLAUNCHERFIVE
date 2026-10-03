"""Tiny, dependency-free NBT (Java edition, big-endian) reader/writer.

Values are represented with small wrapper classes so that the exact tag type
round-trips (Minecraft is strict about e.g. TAG_Int vs TAG_Byte in templates).

    Byte, Short, Int, Long, Float, Double  -> numeric wrappers
    str                                    -> TAG_String
    list subclass List(elem_type, items)   -> TAG_List
    dict (insertion ordered)               -> TAG_Compound
    ByteArray / IntArray / LongArray       -> array tags
"""
import gzip
import io
import struct

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = 0, 1, 2, 3, 4, 5, 6
TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 7, 8, 9, 10, 11, 12


class _Num:
    tag = None
    fmt = None
    __slots__ = ("v",)

    def __init__(self, v):
        self.v = v

    def __eq__(self, o):
        return type(o) is type(self) and o.v == self.v

    def __hash__(self):
        return hash((self.tag, self.v))

    def __repr__(self):
        return f"{type(self).__name__}({self.v!r})"


class Byte(_Num):
    tag, fmt = TAG_BYTE, ">b"


class Short(_Num):
    tag, fmt = TAG_SHORT, ">h"


class Int(_Num):
    tag, fmt = TAG_INT, ">i"


class Long(_Num):
    tag, fmt = TAG_LONG, ">q"


class Float(_Num):
    tag, fmt = TAG_FLOAT, ">f"


class Double(_Num):
    tag, fmt = TAG_DOUBLE, ">d"


class ByteArray(list):
    tag = TAG_BYTE_ARRAY


class IntArray(list):
    tag = TAG_INT_ARRAY


class LongArray(list):
    tag = TAG_LONG_ARRAY


class List(list):
    """TAG_List with an explicit element tag type."""
    tag = TAG_LIST

    def __init__(self, elem_tag=TAG_END, items=()):
        super().__init__(items)
        self.elem_tag = elem_tag

    def __repr__(self):
        return f"List({self.elem_tag}, {list.__repr__(self)})"


_NUM_BY_TAG = {c.tag: c for c in (Byte, Short, Int, Long, Float, Double)}


def tag_of(v):
    if isinstance(v, _Num):
        return v.tag
    if isinstance(v, str):
        return TAG_STRING
    if isinstance(v, List):
        return TAG_LIST
    if isinstance(v, dict):
        return TAG_COMPOUND
    if isinstance(v, (ByteArray, IntArray, LongArray)):
        return v.tag
    if isinstance(v, bool):
        return TAG_BYTE
    if isinstance(v, int):
        return TAG_INT
    if isinstance(v, float):
        return TAG_DOUBLE
    if isinstance(v, list):
        return TAG_LIST
    raise TypeError(f"cannot NBT-encode {type(v)}: {v!r}")


# ---------------------------------------------------------------- reading
class _Reader:
    def __init__(self, data):
        self.b = data
        self.i = 0

    def read(self, fmt):
        r = struct.unpack_from(fmt, self.b, self.i)
        self.i += struct.calcsize(fmt)
        return r[0]

    def string(self):
        n = self.read(">H")
        s = self.b[self.i:self.i + n].decode("utf-8", errors="surrogateescape")
        self.i += n
        return s

    def payload(self, t):
        if t in _NUM_BY_TAG:
            c = _NUM_BY_TAG[t]
            return c(self.read(c.fmt))
        if t == TAG_STRING:
            return self.string()
        if t == TAG_BYTE_ARRAY:
            n = self.read(">i")
            return ByteArray(self.read(">b") for _ in range(n))
        if t == TAG_INT_ARRAY:
            n = self.read(">i")
            return IntArray(self.read(">i") for _ in range(n))
        if t == TAG_LONG_ARRAY:
            n = self.read(">i")
            return LongArray(self.read(">q") for _ in range(n))
        if t == TAG_LIST:
            et = self.read(">b")
            n = self.read(">i")
            return List(et, [self.payload(et) for _ in range(n)])
        if t == TAG_COMPOUND:
            d = {}
            while True:
                ct = self.read(">b")
                if ct == TAG_END:
                    return d
                name = self.string()
                d[name] = self.payload(ct)
        raise ValueError(f"bad tag {t} at {self.i}")


def loads(data):
    """Parse (optionally gzipped) NBT bytes -> (root_name, compound)."""
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    r = _Reader(data)
    t = r.read(">b")
    if t != TAG_COMPOUND:
        raise ValueError("root is not a compound")
    name = r.string()
    return name, r.payload(TAG_COMPOUND)


def load(path):
    with open(path, "rb") as f:
        return loads(f.read())


# ---------------------------------------------------------------- writing
def _w_string(out, s):
    b = s.encode("utf-8")
    out.write(struct.pack(">H", len(b)))
    out.write(b)


def _w_payload(out, v, t):
    if t in _NUM_BY_TAG:
        if isinstance(v, _Num):
            v = v.v
        out.write(struct.pack(_NUM_BY_TAG[t].fmt, v))
    elif t == TAG_STRING:
        _w_string(out, v)
    elif t == TAG_BYTE_ARRAY:
        out.write(struct.pack(">i", len(v)))
        out.write(struct.pack(f">{len(v)}b", *v))
    elif t == TAG_INT_ARRAY:
        out.write(struct.pack(">i", len(v)))
        out.write(struct.pack(f">{len(v)}i", *v))
    elif t == TAG_LONG_ARRAY:
        out.write(struct.pack(">i", len(v)))
        out.write(struct.pack(f">{len(v)}q", *v))
    elif t == TAG_LIST:
        if isinstance(v, List):
            et = v.elem_tag if len(v) or v.elem_tag else TAG_END
            if len(v) and et == TAG_END:
                et = tag_of(v[0])
        else:
            et = tag_of(v[0]) if v else TAG_END
        for e in v:
            if tag_of(e) != et and not (et in _NUM_BY_TAG and isinstance(e, (int, float))):
                raise TypeError(f"heterogeneous list: {tag_of(e)} vs {et}")
        out.write(struct.pack(">bi", et, len(v)))
        for e in v:
            _w_payload(out, e, et)
    elif t == TAG_COMPOUND:
        for k, e in v.items():
            et = tag_of(e)
            out.write(struct.pack(">b", et))
            _w_string(out, k)
            _w_payload(out, e, et)
        out.write(b"\x00")
    else:
        raise ValueError(t)


def dumps(root, name="", compress=True):
    out = io.BytesIO()
    out.write(struct.pack(">b", TAG_COMPOUND))
    _w_string(out, name)
    _w_payload(out, root, TAG_COMPOUND)
    raw = out.getvalue()
    if not compress:
        return raw
    # mtime=0 keeps output byte-for-byte deterministic across runs
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0, filename="") as g:
        g.write(raw)
    return buf.getvalue()


def dump(root, path, name=""):
    with open(path, "wb") as f:
        f.write(dumps(root, name))


def to_py(v):
    """Strip wrappers for pretty-printing / comparisons."""
    if isinstance(v, _Num):
        return v.v
    if isinstance(v, dict):
        return {k: to_py(x) for k, x in v.items()}
    if isinstance(v, list):
        return [to_py(x) for x in v]
    return v


def describe(v, indent=0, max_list=6):
    """Typed, human-readable dump (shows tag types)."""
    pad = "  " * indent
    if isinstance(v, dict):
        lines = []
        for k, x in v.items():
            if isinstance(x, (dict, list)) and not isinstance(x, (ByteArray, IntArray, LongArray)):
                lines.append(f"{pad}{k}: {type(x).__name__}" + (f"<{x.elem_tag}>[{len(x)}]" if isinstance(x, List) else ""))
                lines.append(describe(x, indent + 1, max_list))
            else:
                lines.append(f"{pad}{k}: {x!r}")
        return "\n".join(lines)
    if isinstance(v, list):
        lines = []
        for i, x in enumerate(v[:max_list]):
            if isinstance(x, (dict, list)):
                lines.append(f"{pad}[{i}]")
                lines.append(describe(x, indent + 1, max_list))
            else:
                lines.append(f"{pad}[{i}] {x!r}")
        if len(v) > max_list:
            lines.append(f"{pad}... ({len(v)} total)")
        return "\n".join(lines)
    return pad + repr(v)
