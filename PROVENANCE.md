# Provenance

Housekeeping record for this archive, split out of `README.md`.

## Where `versions/2000-08/` came from

`versions/2000-08/` was previously stored *outside* this repository as
`../JSApplet/` (a separate git repo, one commit `02692df`, pushed to
`github.com/uhuntu/JSApplet`). It has been copied in because losing it would
lose the 1.2.3 baseline and make the whole comparison unreproducible.

The original directory was deleted on 2026-09-28 after that copy was verified
byte-identical, since by then it existed in three independent places: this
repository, the `uhuntu/JSApplet` remote, and the tar backup described below. Deleting it removed a duplicate from `work/`, not the last copy.

Its single git commit, `02692df`, has since been fetched back into this
repository's object database and tagged **`v1.2.3-baseline`**, so the 2000-era
snapshot has a durable local git object rather than depending on the remote.
Worth knowing what that commit is: it is a **2026 snapshot** of the 2000 source,
not a 24-year development history — that repo was `git init`'d on 2026-07-19, the
same afternoon as `JSearch`'s own initial commit `a664f23`. There is no deeper
history to recover in either repository.

`versions/2000-08/` is a **superset** of that commit's tree: 35 files against 33.
The two extras, `JSApplet.suo` and `vssver.scc`, were excluded from the commit
by that repo's own `.gitignore`. All 33 shared files are byte-identical. So the
archived copy is the more complete of the two; the commit's unique value is
being a named, reachable object.

## Recovery and reorganization

Recovered and reorganized on 2026-09-28 from the author's original working
directories. The pre-reorganization state is preserved as git tag
`pre-restructure-a664f23`, and a full byte-for-byte backup of both directories
at reorganization time is `../JSearch-archive-backup.tar.gz`
(sha256 `0a53d185253854008b243f24d990d4b229eb22aefc29f50144e526b3cfc6cabe`).

Files were moved, never edited — with one deliberate exception. The 16
documentation files removed from `versions/2002-01/DOCS/` were verified
byte-identical to those already present in `docs/` before removal, and all
GBK-encoded content is otherwise byte-identical to the original.

**The exception:** `Sources/SearchThread.java` has been modified twice since,
fixing the two races described under "The deadlock patch does not work" — a
shared static `resultLock`, and an atomic `actualSearchAllowed` decrement under
a second static lock. Both are compiled and bytecode-verified on JDK 8, and
neither alters behaviour on any path that can execute today, so the change is a
correctness improvement rather than a resurrection.

The unaffected March 2002 bytes remain available in git regardless:
`bca88b3:Sources/SearchThread.java` still resolves to the original blob, as
does the pre-restructure tag above. Every other file in this tree, including
all of `versions/`, is untouched.
