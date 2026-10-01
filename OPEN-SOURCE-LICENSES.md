# Open Source Licenses

## About Squircle CE

This application is built with love and open source software. We're grateful
to all the developers and contributors who made this possible.

---

## Core Components

### Squircle CE
- **License:** Apache License 2.0
- **Copyright:** Black Squircle UI
- **Repository:** https://github.com/black-squircle/squircle-ce

### Sora Editor
- **License:** GNU Lesser General Public License v2.1 (LGPL-2.1)
- **Copyright:** Rosemoe
- **Repository:** https://github.com/Rosemoe/sora-editor

**Note:** This app uses Sora Editor for code editing functionality. Under
LGPL v2.1, you have the right to replace this library with a modified version.
Build scripts are available in our GitHub repository.

### Python Runtime
- **License:** Python Software Foundation License
- **Copyright:** Python Software Foundation
- **Website:** https://www.python.org/

### Git Command-Line Tool (bundled for the terminal and the Git panel)
- **Version:** 2.55.0
- **License:** GNU General Public License v2 (GPL-2.0)
- **Copyright:** Junio C Hamano and the Git contributors
- **Website:** https://git-scm.com/
- **Source:** https://github.com/git/git
- **Binary distribution:** Pre-built ELFs and shared libraries are taken
  from the [Termux](https://termux.dev/) apt repository (`git` and its
  dependencies). The executables live in `:core-git`, their shared
  libraries in `:feature-terminal`. Squircle CE runs them as separate
  processes and does not link against them, so the app itself remains
  Apache-2.0 (see THIRD-PARTY-LICENSES). Each dependency retains its
  original license:

  | Component | Version | License | Upstream |
  |---|---|---|---|
  | git | 2.55.0 | GPL-2.0 | https://git-scm.com/ |
  | libpcre2 | 10.47 | BSD-3-Clause | https://www.pcre.org/ |
  | libcurl / libwcurl | 8.22.0 | curl License (MIT/X11-style) | https://curl.se/ |
  | libssh2 | 1.11.1 | BSD-3-Clause | https://www.libssh2.org/ |
  | libnghttp2 (HTTP/2) | 1.70.0 | MIT | https://nghttp2.org/ |
  | libnghttp3 (HTTP/3) | 1.18.0 | MIT | https://github.com/ngtcp2/nghttp3 |
  | libngtcp2 (QUIC) | 1.25.0 | MIT | https://github.com/ngtcp2/ngtcp2 |
  | OpenSSL | 3.6.3 | Apache-2.0 | https://www.openssl.org/ |
  | zlib | — | Zlib | https://zlib.net/ |
  | Expat | 2.8.5 | MIT | https://libexpat.github.io/ |
  | libiconv / libcharset | — | LGPL-2.1+ | https://www.gnu.org/software/libiconv/ |

  Per GPL-2.0, the source code for `git` itself is available at
  https://github.com/git/git and may be obtained on request from the
  project maintainers.

---

## Major Dependencies

We use many excellent open source libraries including:
- **Dagger** (Apache 2.0) - Dependency injection
- **Retrofit** (Apache 2.0) - HTTP client
- **Room** (Apache 2.0) - Database
- **Kotlin Coroutines** (Apache 2.0) - Asynchronous programming
- **Timber** (Apache 2.0) - Logging
- **Git** (GPL-2.0) - Bundled as Termux-built binaries (`libgit.so` and helpers
  in `:core-git`), used by both the terminal and the Git panel
- **ColorPicker** (MIT) - Color selection
- **OpenBLAS** (BSD 3-Clause) - Bundled as `libopenblas.so` (Termux build), required by
  precompiled Android wheels such as NumPy
- **libc++** (Apache 2.0 with LLVM Exceptions) - Shipped as `libc++_shared.so`
  from the NDK `c++_shared` STL; C++ runtime required by precompiled wheels
- And many more...

For complete license information, please see the full THIRD-PARTY-LICENSES
file in the project repository.

---

## Your Rights

As a user of this application, you have the following rights:

✅ Use the application freely
✅ Inspect the source code on GitHub
✅ Modify the application for personal use
✅ Distribute copies under the same license terms

Under LGPL v2.1 for Sora Editor:
✅ Replace the editor library with your own modified version
✅ Access build scripts to rebuild the application

---

## Contributing

We welcome contributions! Visit our GitHub repository to:
- Report bugs
- Suggest features
- Submit pull requests
- Help with translations

---

## Questions?

If you have questions about licensing or compliance, please open an issue
on GitHub or contact us through the project repository.

---

**Thank you for using Squircle CE!** 🚀

Full license texts are available in:
- LICENSE (Apache 2.0)
- NOTICE (Attribution notices)
- THIRD-PARTY-LICENSES (Complete third-party licenses)
