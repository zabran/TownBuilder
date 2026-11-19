"""Send commands to the LOCAL test server over RCON and print the answers.
python -I tools/rcon.py "plot reload" "list"
The password is read from ~/.secrets/mc-test-rcon.txt (never printed); the server listens on 127.0.0.1 only."""
import os
import socket
import struct
import sys

HOST, PORT = "127.0.0.1", 25575


def _pkt(rid, typ, body):
    b = body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<iii", len(b) + 8, rid, typ) + b


def _read(s):
    def n(k):
        out = b""
        while len(out) < k:
            c = s.recv(k - len(out))
            if not c:
                raise ConnectionError("closed")
            out += c
        return out
    ln = struct.unpack("<i", n(4))[0]
    data = n(ln)
    rid, typ = struct.unpack("<ii", data[:8])
    return rid, typ, data[8:-2].decode("utf-8", "replace")


def run(cmds):
    pw = open(os.path.expanduser("~/.secrets/mc-test-rcon.txt"), encoding="utf-8").read().strip()
    with socket.create_connection((HOST, PORT), timeout=60) as s:
        s.sendall(_pkt(1, 3, pw))
        rid, _, _ = _read(s)
        if rid == -1:
            raise SystemExit("RCON login refused")
        out = []
        for i, c in enumerate(cmds, start=2):
            s.sendall(_pkt(i, 2, c))
            out.append(_read(s)[2])
        return out


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    for c, r in zip(sys.argv[1:], run(sys.argv[1:])):
        print(">", c)
        print(r)
