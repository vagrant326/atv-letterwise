#!/usr/bin/env python3
"""Builds the character n-gram table the keyboard ships.

    python3 train.py --language pl --order 3

Reads everything in raw/ for the language and writes
app/src/main/assets/trigrams-<language>.bin.

The table is **dense**: a flat array of counts indexed by symbol arithmetic, one array per
order. Sparse storage would be smaller in principle and pointless in practice - the
alphabet is 27 symbols for English and 33 for Polish, so an order-3 table is a few tens of
thousands of counts, well under a megabyte. Dense means lookup is index arithmetic with no
hashing, no allocation and no parsing on the device.

Format, big-endian throughout so the Kotlin side can read it with a plain ByteBuffer:

    magic       4 bytes  "LWM1"
    order       u8
    symbols     u8       S
    reserved    u16      0
    alphabet    S x u16  UTF-16 code units, index order
    counts      for k in 1..order: S^k x u32
"""

import argparse
import array
import os
import struct
import sys

from alphabet import ALPHABETS

HERE = os.path.dirname(__file__)
DEFAULT_RAW = os.path.join(HERE, "raw")
DEFAULT_ASSETS = os.path.join(HERE, os.pardir, "app", "src", "main", "assets")

MAGIC = b"LWM1"


def count(language: str, order: int, raw: str, title_weight: int) -> list[list[int]]:
    """One flat count array per order, from 1 (unigram) up to `order`."""
    alphabet = ALPHABETS[language]
    index = {symbol: position for position, symbol in enumerate(alphabet)}
    size = len(alphabet)
    tables = [[0] * (size ** k) for k in range(1, order + 1)]

    sources = sorted(
        os.path.join(raw, name)
        for name in os.listdir(raw)
        if name.endswith(f"-{language}.txt")
    )
    if not sources:
        raise SystemExit(f"no raw text for {language}: run fetch.py first")

    characters = 0
    for path in sources:
        # Titles are a few percent of the text but the whole workload. Left at their natural
        # share they do not move the counts at all, which is measurable: adding them changed
        # nothing. The weight is how the domain mix gets set deliberately rather than by
        # whatever the download happened to contain.
        # startswith, not `in`: "titles-" is a substring of "subtitles-", so the obvious
        # containment test weighted the entire corpus uniformly - which changes no ratio and
        # therefore no ranking, and looked exactly like the domain mix not mattering.
        weight = title_weight if os.path.basename(path).startswith("titles-") else 1
        with open(path, encoding="utf-8") as handle:
            for line in handle:
                # Every line is a sentence or a title, so it starts after a word boundary.
                # Prefixing a space lets the model learn which letters begin a word.
                text = " " + line.rstrip("\n")
                positions = [index[character] for character in text if character in index]
                characters += len(positions) * weight
                for at, symbol in enumerate(positions):
                    offset = symbol
                    tables[0][offset] += weight
                    stride = size
                    for k in range(1, order):
                        if at - k < 0:
                            break
                        offset += positions[at - k] * stride
                        stride *= size
                        tables[k][offset] += weight
    print(f"{language}: {characters} weighted characters from {len(sources)} files", file=sys.stderr)
    return tables


def write(language: str, order: int, tables: list[list[int]], out: str) -> str:
    alphabet = ALPHABETS[language]
    os.makedirs(out, exist_ok=True)
    target = os.path.join(out, f"trigrams-{language}.bin")

    with open(target, "wb") as out:
        out.write(MAGIC)
        out.write(struct.pack(">BBH", order, len(alphabet), 0))
        for symbol in alphabet:
            out.write(struct.pack(">H", ord(symbol)))
        for table in tables:
            # array rather than struct.pack(f">{n}I", *table): at order 4 the highest table
            # is over a million cells, and unpacking that into call arguments is pointless
            # work.
            packed = array.array("I", table)
            if sys.byteorder == "little":
                packed.byteswap()
            out.write(packed.tobytes())

    size = os.path.getsize(target)
    # How much of the dense table is actually used. This is the number that decides whether
    # dense storage is still the right call at a given order.
    for at, table in enumerate(tables, start=1):
        filled = sum(1 for cell in table if cell)
        share = filled / len(table) * 100
        print(
            f"  order {at}: {filled}/{len(table)} cells used ({share:.1f}%)",
            file=sys.stderr,
        )
    print(f"{os.path.basename(target)}  {size / 1024:.0f} kB", file=sys.stderr)
    return target


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language", choices=("pl", "en"), required=True)
    parser.add_argument("--order", type=int, default=3)
    parser.add_argument("--raw", default=DEFAULT_RAW, help="directory of normalised text")
    parser.add_argument("--out", default=DEFAULT_ASSETS, help="where to write the table")
    parser.add_argument(
        "--title-weight",
        type=int,
        default=1,
        help="how many times title text counts, to set the domain mix deliberately",
    )
    arguments = parser.parse_args()

    if arguments.order < 1 or arguments.order > 4:
        raise SystemExit("order must be between 1 and 4")

    tables = count(
        arguments.language, arguments.order, arguments.raw, arguments.title_weight
    )
    write(arguments.language, arguments.order, tables, arguments.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
