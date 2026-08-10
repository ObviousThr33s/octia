"""
Set a save's game mode, in place, without opening it.

Why: Octia has no survival features. Its content is things to find and look at,
and a dev world where the first ten minutes go on punching a tree is a dev world
where nobody looks at the ruins. New worlds are made creative by
tools/new-world.ps1; this is for the ones that already exist.

It also turns cheats on, which is not a separate favour: `/place feature
octia:derelict` is the only way to see a ruin without walking to one, and it
needs commands allowed.

WHAT IT WRITES, precisely - three fields of level.dat:

    Data.GameType             the mode new players arrive in
    Data.Player.playerGameType the mode the existing singleplayer host is in
    Data.allowCommands        cheats

The second one is the one that is easy to miss. A singleplayer save carries the
host's own player data inside level.dat, so setting GameType alone changes what
a *new* player would get and leaves you exactly as you were.

level.dat is copied to level.dat.octia-bak first. Minecraft's own level.dat_old
is left alone - it is Minecraft's rollback, not ours, and overwriting it would
take away the fallback this is most likely to need.

Usage:
    python tools/world-gamemode.py                    # every save, to creative
    python tools/world-gamemode.py --survival         # back again
    python tools/world-gamemode.py <save-or-saves-dir>
"""

import gzip
import os
import shutil
import struct
import sys

# ---- Typed NBT -------------------------------------------------------------
#
# A second codec, and deliberately not world-report.py's. That reader answers in
# plain Python - an int for a byte, a short and an int alike, a list for three
# different array tags - which is exactly right for reading and makes writing
# impossible: there is no way back to the tag that was on the wire. Here every
# value keeps its tag, so a file can be read, one field changed, and written back
# byte-for-byte identical everywhere else.

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE = 0, 1, 2, 3, 4, 5, 6
BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = 7, 8, 9, 10, 11, 12

FIXED = {BYTE: ">b", SHORT: ">h", INT: ">i", LONG: ">q", FLOAT: ">f", DOUBLE: ">d"}


class Reader:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def take(self, n):
        chunk = self.d[self.i:self.i + n]
        if len(chunk) != n:
            raise EOFError("truncated NBT")
        self.i += n
        return chunk

    def num(self, fmt):
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self):
        return self.take(self.num(">H")).decode("utf-8", "replace")

    def payload(self, kind):
        if kind in FIXED:
            return self.num(FIXED[kind])
        if kind == BYTE_ARRAY:
            return self.take(self.num(">i"))
        if kind == STRING:
            return self.string()
        if kind == LIST:
            inner = self.num(">b")
            count = self.num(">i")
            return (inner, [self.payload(inner) for _ in range(max(0, count))])
        if kind == COMPOUND:
            out = {}
            while True:
                tag = self.num(">b")
                if tag == END:
                    return out
                name = self.string()
                out[name] = (tag, self.payload(tag))
        if kind == INT_ARRAY:
            count = self.num(">i")
            return list(struct.unpack(">%di" % count, self.take(4 * count)))
        if kind == LONG_ARRAY:
            count = self.num(">i")
            return list(struct.unpack(">%dq" % count, self.take(8 * count)))
        raise ValueError("unknown NBT tag %d" % kind)


def write_payload(out, kind, value):
    if kind in FIXED:
        out += struct.pack(FIXED[kind], value)
    elif kind == BYTE_ARRAY:
        out += struct.pack(">i", len(value)) + value
    elif kind == STRING:
        raw = value.encode("utf-8")
        out += struct.pack(">H", len(raw)) + raw
    elif kind == LIST:
        inner, items = value
        out += struct.pack(">bi", inner, len(items))
        for item in items:
            write_payload(out, inner, item)
    elif kind == COMPOUND:
        for name, (tag, child) in value.items():
            raw = name.encode("utf-8")
            out += struct.pack(">b", tag) + struct.pack(">H", len(raw)) + raw
            write_payload(out, tag, child)
        out += struct.pack(">b", END)
    elif kind == INT_ARRAY:
        out += struct.pack(">i", len(value))
        out += struct.pack(">%di" % len(value), *value)
    elif kind == LONG_ARRAY:
        out += struct.pack(">i", len(value))
        out += struct.pack(">%dq" % len(value), *value)
    else:
        raise ValueError("unknown NBT tag %d" % kind)


def read_level(path):
    with open(path, "rb") as f:
        raw = f.read()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    r = Reader(raw)
    if r.num(">b") != COMPOUND:
        raise ValueError("level.dat root is not a compound")
    name = r.string()
    return name, r.payload(COMPOUND)


def write_level(path, name, root):
    out = bytearray()
    raw = name.encode("utf-8")
    out += struct.pack(">b", COMPOUND) + struct.pack(">H", len(raw)) + raw
    write_payload(out, COMPOUND, root)
    with open(path, "wb") as f:
        f.write(gzip.compress(bytes(out)))


# ---- The change ------------------------------------------------------------

CREATIVE, SURVIVAL = 1, 0


def is_open(save):
    """
    Whether Minecraft currently has this save open.

    <p>session.lock is held exclusively for as long as a world is loaded, so
    failing to open it is the answer. Checked rather than assumed: a level.dat
    written underneath a running game is either overwritten by the next autosave
    or corrupted, and the log is not a reliable second opinion - the integrated
    server does not announce loading a world the way a dedicated one does, so
    reading the log said "no world open" while a world was very much open.
    """
    lock = os.path.join(save, "session.lock")
    if not os.path.isfile(lock):
        return False

    # Attempt the same BYTE-RANGE lock Minecraft holds, not merely an open.
    #
    # This is the second version. The first just tried open(lock, "r+b") and
    # reported every world free, including one that was open at the time, so it
    # wrote a level.dat out from under a running game. Minecraft's DirectoryLock
    # uses FileChannel.tryLock, which is a region lock: the file stays perfectly
    # openable and only a competing lock attempt fails. Opening it proves
    # nothing at all.
    try:
        handle = open(lock, "r+b")
    except OSError:
        return True

    try:
        if os.name == "nt":
            import msvcrt
            try:
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
                return False
            except OSError:
                return True
        import fcntl
        try:
            fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
            return False
        except OSError:
            return True
    finally:
        handle.close()


def retune(save, mode):
    level = os.path.join(save, "level.dat")
    if not os.path.isfile(level):
        return "no level.dat"
    if is_open(save):
        return "SKIPPED - open in Minecraft right now"

    name, root = read_level(level)
    data = root.get("Data", (COMPOUND, {}))[1]

    before = data.get("GameType", (INT, None))[1]
    data["GameType"] = (INT, mode)
    data["allowCommands"] = (BYTE, 1)

    # The host's own player lives inside level.dat on a singleplayer save.
    # Without this the change applies to a player who never arrives.
    host = "no embedded player"
    if "Player" in data:
        player = data["Player"][1]
        player["playerGameType"] = (INT, mode)
        host = "host set too"

    shutil.copy2(level, level + ".octia-bak")
    write_level(level, name, root)
    return "GameType %s -> %s, cheats on, %s" % (before, mode, host)


def main():
    argv = [a for a in sys.argv[1:] if not a.startswith("--")]
    mode = SURVIVAL if "--survival" in sys.argv else CREATIVE

    root = argv[0] if argv else os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "run", "saves")
    if not os.path.isdir(root):
        print("no directory at %s" % root)
        return 1

    if os.path.isfile(os.path.join(root, "level.dat")):
        saves = [root]
    else:
        saves = [os.path.join(root, d) for d in sorted(os.listdir(root))
                 if os.path.isdir(os.path.join(root, d))]

    for save in saves:
        print("%-34s %s" % (os.path.basename(save), retune(save, mode)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
