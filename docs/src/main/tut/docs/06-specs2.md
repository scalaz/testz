---
layout: docs
title: testz-specs2
---

# {{ page.title }}

testz provides a module for specs2 compatibility, as an alternative to testz-runner.

It provides a harness, `Specs2Harness`, which is typed as an
`EffectHarness[Future, T]`. Tests can be written against a generic `Harness` or
`EffectHarness` and be run against specs2 or testz-runner, allowing gradual
conversion to testz-runner.
