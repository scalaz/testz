---
layout: docs
title: testz-util
---

# {{ page.title }}

Testz's own tests are written with testz itself. Because of this, we need
to be a bit careful about using code which we haven't tested in the same file.

For example, code from testz-scalaz is not used in the tests for testz-stdlib;
however, outside of the test for the `Monoid[Result]`, the `Monoid[Result]` *is*
used in the tests for testz-scalaz.
