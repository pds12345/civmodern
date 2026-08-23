# Third-party notices

CivModern is licensed under the GPLv3 (see `LICENSE.txt`). It additionally bundles source
code from the projects below.

## owo-lib

`src/main/java/sh/okx/civmodern/common/ui/**` and the UI-related mixins in
`src/main/java/sh/okx/civmodern/common/mixins/` are a vendored, pruned fork of
**owo-lib 0.13.0+1.21.11** — https://github.com/wisp-forest/owo-lib

The nine-patch metadata under `src/main/resources/assets/civmodern/nine_patch_textures/`
and the panel/button textures under `src/main/resources/assets/civmodern/textures/gui/`
come from the same release.

The sources were remapped from intermediary to Mojang mappings and relocated out of
`io.wispforest.owo` so that CivModern no longer requires owo-lib to be installed, and so
that both can coexist if a player has owo-lib for another mod. See `tools/README.md`.

MIT is compatible with the GPLv3. The upstream notice, reproduced verbatim from
`LICENSE_owo-lib` in the owo-lib 0.13.0+1.21.11 jar:

```
The MIT License (MIT)

Copyright (c) 2021 

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```
