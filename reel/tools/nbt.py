"""
A small reader for Minecraft's NBT (gzipped or not) into plain Python values, for reading mods' saved data
(e.g. a Hearth settlement, <world>/data/hearth/settlements/<id>.dat) into Blender scenes and story notes.

    import nbt; root = nbt.load(path)        # dict, list, int, float, str; arrays as lists
"""
import gzip
import struct


def load(path):
    raw = open(path, "rb").read()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    r = _Reader(raw)
    kind = r.u8()
    if kind == 0:
        return {}
    r.string()  # the root's name
    return r.value(kind)


class _Reader:
    def __init__(self, raw):
        self.raw, self.at = raw, 0

    def take(self, fmt):
        v = struct.unpack_from(">" + fmt, self.raw, self.at)
        self.at += struct.calcsize(">" + fmt)
        return v[0]

    def u8(self):
        return self.take("B")

    def string(self):
        n = self.take("H")
        s = self.raw[self.at:self.at + n].decode("utf-8", "replace")
        self.at += n
        return s

    def value(self, kind):
        if kind == 1:
            return self.take("b")
        if kind == 2:
            return self.take("h")
        if kind == 3:
            return self.take("i")
        if kind == 4:
            return self.take("q")
        if kind == 5:
            return self.take("f")
        if kind == 6:
            return self.take("d")
        if kind == 7:
            n = self.take("i")
            v = list(self.raw[self.at:self.at + n])
            self.at += n
            return v
        if kind == 8:
            return self.string()
        if kind == 9:
            inner, n = self.u8(), self.take("i")
            return [self.value(inner) for _ in range(n)]
        if kind == 10:
            out = {}
            while True:
                k = self.u8()
                if k == 0:
                    return out
                name = self.string()
                out[name] = self.value(k)
        if kind == 11:
            return [self.take("i") for _ in range(self.take("i"))]
        if kind == 12:
            return [self.take("q") for _ in range(self.take("i"))]
        raise ValueError("unknown NBT tag %d at %d" % (kind, self.at))
