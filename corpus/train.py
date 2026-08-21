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
import os
import struct
import sys

from alphabet import ALPHABETS

HERE = os.path.dirname(__file__)
RAW = os.path.join(HERE, "raw")
ASSETS = os.path.join(HERE, os.pardir, "app", "src", "main", "assets")

MAGIC = b"LWM1"


def count(language: str, order: int) -> list[list[int]]:
    """One flat count array per order, from 1 (unigram) up to `order`."""
    alphabet = ALPHABETS[language]
    index = {symbol: position for position, symbol in enumerate(alphabet)}
    size = len(alphabet)
    tables = [[0] * (size ** k) for k in range(1, order + 1)]

    sources = sorted(
        os.path.join(RAW, name)
        for name in os.listdir(RAW)
        if name.endswith(f"-{language}.txt")
    )
    if not sources:
        raise SystemExit(f"no raw text for {language}: run fetch.py first")

    characters = 0
    for path in sources:
        with open(path, encoding="utf-8") as handle:
            for line in handle:
                # Every line is a sentence or a title, so it starts after a word boundary.
                # Prefixing a space lets the model learn which letters begin a word.
                text = " " + line.rstrip("\n")
                positions = [index[character] for character in text if character in index]
                characters += len(positions)
                for at, symbol in enumerate(positions):
                    offset = symbol
                    tables[0][offset] += 1
                    stride = size
                    for k in range(1, order):
                        if at - k < 0:
                            break
                        offset += positions[at - k] * stride
                        stride *= size
                        tables[k][offset] += 1
    print(f"{language}: {characters} characters from {len(sources)} files", file=sys.stderr)
    return tables


def write(language: str, order: int, tables: list[list[int]]) -> str:
    alphabet = ALPHABETS[language]
    os.makedirs(ASSETS, exist_ok=True)
    target = os.path.join(ASSETS, f"trigrams-{language}.bin")

    with open(target, "wb") as out:
        out.write(MAGIC)
        out.write(struct.pack(">BBH", order, len(alphabet), 0))
        for symbol in alphabet:
            out.write(struct.pack(">H", ord(symbol)))
        for table in tables:
            out.write(struct.pack(f">{len(table)}I", *table))

    size = os.path.getsize(target)
    print(f"{os.path.basename(target)}  {size / 1024:.0f} kB", file=sys.stderr)
    return target


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language", choices=("pl", "en"), required=True)
    parser.add_argument("--order", type=int, default=3)
    arguments = parser.parse_args()

    if arguments.order < 1 or arguments.order > 4:
        raise SystemExit("order must be between 1 and 4")

    tables = count(arguments.language, arguments.order)
    write(arguments.language, arguments.order, tables)
    return 0


if __name__ == "__main__":
    sys.exit(main())
