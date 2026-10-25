---
title: Do not name your package after the library
impact: HIGH
tags: core, relocation, build
---

## Do not name your package after the library

The shade step relocates `com.github.dewarepk.synthetixlib` to `<your package>.lib.synthetixlib`. A consumer package that starts with the same prefix would be rewritten too and `verifyRelocation` fails.

**❌ Incorrect**

```java
package com.github.dewarepk.synthetixlib.myplugin;
```

**✅ Correct**

```java
package com.acme.auction;
```
