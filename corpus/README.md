# Corpus and model

Builds the character n-gram table the keyboard ships. Everything here runs in the dev
container; nothing here runs on the device.

```bash
docker compose -f docker/compose.yaml run --rm -w /work/atv-letterwise/corpus dev \
  python3 fetch.py --language pl --megabytes 40

docker compose -f docker/compose.yaml run --rm -w /work/atv-letterwise/corpus dev \
  python3 train.py --language pl --order 3
```

`fetch.py` writes to `raw/`, which is gitignored. `train.py` writes
`app/src/main/assets/trigrams-<language>.bin`, which **is** committed: it is a derived
table of a couple of hundred kilobytes, not a corpus, and committing it means CI can build
a working keyboard without downloading anything.

## Sources, and why two of them

**OpenSubtitles** (OPUS mono files) supplies running speech. That is what teaches the model
ordinary letter sequences, and subtitles match the domain — film and television dialogue —
far better than general web text would.

**Wikidata** supplies film titles, series titles, actor and musician names. This is the
workload the keyboard actually faces, and it is precisely where statistics from running
text generalise worst: `electroboom` and `twoset` obey no ordinary spelling pattern.

The subtitle download is bounded by `--megabytes`, default 40. Character trigrams over a
36-symbol alphabet saturate long before a corpus of that size runs out, so pulling gigabytes
would cost bandwidth and buy nothing measurable. Raise it if a measurement says otherwise.

## Two things learned from the SPARQL endpoint

Queries are paged and one entity kind at a time. A union of kinds times out, and so does
walking the subclass tree with `wdt:P31/wdt:P279*`. When it times out the endpoint returns a
**truncated body**, which surfaces as a JSON parse error rather than an HTTP error — so a
failure looks like corrupt data instead of a timeout. Direct `wdt:P31` costs a few niche
films and finishes.

People are selected by occupation (actor, musician) rather than by being human. `Q5` has
millions of members and none of the filtering that makes a name relevant to a TV search box.

## Normalisation keeps diacritics

`alphabet.py` lowercases, folds typographic lookalikes, and reduces text to the alphabet plus
single spaces. It does **not** strip Polish diacritics. That is the obvious cleanup and it
would break the model: the keyboard asks it to rank `ó` against `o`, and a model trained
without `ó` has nothing to say.

Characters outside the alphabet become a space rather than vanishing. Deleting them would
join the letters on either side into a trigram that never occurs in real text — `don't`
would otherwise teach the model that `t` follows `n`.

## Format

Dense: one flat count array per order, indexed by symbol arithmetic, big-endian.

```
magic       4 bytes  "LWM1"
order       u8
symbols     u8       S
reserved    u16
alphabet    S x u16  UTF-16 code units, index order
counts      for k in 1..order: S^k x u32
```

Sparse storage would be smaller in principle and worse in practice: S is 27 for English and
36 for Polish, so an order-3 table is a few tens of thousands of counts. Dense means lookup
is index arithmetic with no hashing and no allocation on the keystroke path.

The layout is pinned from both ends. `BinaryNgramModelTest` writes the same bytes `train.py`
writes and asserts the two rank candidates identically, because a silent disagreement between
trainer and reader would produce a keyboard that ranks letters by nonsense while looking
fine.

## Measuring

```bash
docker compose -f docker/compose.yaml run --rm -w /work/atv-letterwise dev ./gradlew :core:bench
```

Reports KSPC over `bench/queries-v1.tsv` with the trained model and with a uniform one side
by side. The uniform column is what the keyboard costs with no prediction at all — a phone
keypad — so the gap between the columns is the only thing that says whether the model earns
its place.
