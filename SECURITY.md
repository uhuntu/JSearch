# Security Policy

## Supported versions

This is a **software archive**. The original code cannot run. `modern/` is a
local design study (tests, fixtures, optional demo server). Nothing here is a
supported network service.

| Tree | Status |
|------|--------|
| Original (2000-2002) | Not runnable |
| `modern/` | Teaching sketch only |

## Reporting a vulnerability

This archive is not deployed. If you find a problem in `modern/` (for example
in the optional demo server or fetcher) that would matter to someone running
it locally:

Please report it by:

1. **Do not open a public issue** for security-sensitive matters
2. Email the maintainer directly (see GitHub profile)
3. Or open a draft security advisory if the repository has that feature enabled

## Historical context

The original 2002 applet ran with full trust in Internet Explorer, which would
be considered a security risk by today's standards. Modern browsers have
eliminated this attack surface by removing applet support entirely.
