# Security Policy

## Supported versions

This is a **software archive**. The original code cannot run (applets are gone
from browsers and JDK), and the reference design in `modern/` does not connect
to any network by default.

| Version | Supported |
|---------|-----------|
| Original (2000-2002) | ❌ Not runnable |
| modern/ reference | ✅ Tests only, no network |

## Reporting a vulnerability

Since this is an archive project with no runtime components, security
vulnerabilities are unlikely. However, if you find:

- A bug in the reference design that could cause issues when adapted
- A dependency vulnerability (if dependencies are added in the future)
- Any other security concern

Please report it by:

1. **Do not open a public issue** for security-sensitive matters
2. Email the maintainer directly (see GitHub profile)
3. Or open a draft security advisory if the repository has that feature enabled

## Security best practices for adapters

If you adapt the `modern/` reference design for production use:

- **Never hardcode API keys** — use environment variables or secret managers
- **Validate all inputs** — the reference design trusts engine definitions
- **Rate limit requests** — the reference design has no built-in rate limiting
- **Use HTTPS** — the reference design supports any URL scheme
- **Sandbox network access** — use allowlists for engine URLs

## Historical context

The original 2002 applet ran with full trust in Internet Explorer, which would
be considered a security risk by today's standards. Modern browsers have
eliminated this attack surface by removing applet support entirely.
