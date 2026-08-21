"""Alphabets and text normalisation shared by the corpus scripts.

The Polish alphabet keeps its diacritics. Stripping them is the obvious cleanup and it
would be a mistake: the keyboard is asked to rank `ó` against `o`, so a model trained
on text without `ó` cannot answer. See docs/10-letterwise.md.
"""

SPACE = " "

# Space is index 0, which is also the natural back-off symbol.
EN_LETTERS = "abcdefghijklmnopqrstuvwxyz"
PL_EXTRA = "ąćęłńóśźż"  # a c e l n o s z z

ALPHABETS = {
    "en": SPACE + EN_LETTERS,
    "pl": SPACE + EN_LETTERS + PL_EXTRA,
}

# Typographic characters that appear throughout scraped subtitle text. Folding them keeps
# a word whole; leaving them would turn one word into two contexts.
FOLD = {
    " ": " ",  # non-breaking space
    "‘": "'",
    "’": "'",
    "“": '"',
    "”": '"',
    "–": "-",  # en dash
    "—": "-",  # em dash
    "…": "...",
}


def normalise(line: str, language: str) -> str:
    """Lowercase, fold lookalikes, and reduce to the alphabet plus single spaces.

    Characters outside the alphabet become a space rather than vanishing. Dropping them
    would join the letters either side into a trigram that never occurs in real text:
    `don't` would otherwise teach the model that `t` follows `n`.
    """
    alphabet = set(ALPHABETS[language])
    folded = "".join(FOLD.get(character, character) for character in line)
    kept = [character if character in alphabet else SPACE for character in folded.lower()]

    # Collapse runs of spaces: a paragraph break is one word boundary, not four.
    out = []
    for character in kept:
        if character == SPACE and (not out or out[-1] == SPACE):
            continue
        out.append(character)
    return "".join(out).strip()
