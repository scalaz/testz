---
layout: docs
title: testz-scalaz
---

# {{ page.title }}

Provided for scalaz support are the following tools:

## Instances

An instance of `Equal[testz.Result]` which does more or less what you'd expect.

An instance of `Monoid[testz.Result]` which combines errors in the presence of any
failures, and returns `Succeed()` otherwise.

## Harnesses

`TaskHarness`, a harness for tests in `scalaz.concurrent.Task`, provided as an
`EffectResourceHarness[Task, T]`.
