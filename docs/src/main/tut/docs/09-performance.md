---
layout: docs
title: Performance
---

# {{ page.title }}

None of the code in testz is run often enough to be a candidate for the JIT.

This makes the JIT irrelevant to the performance engineering in testz;
we have different things to focus on.

Doing less in general is what makes testz's performance so good.
Fewer classes to load, fewer trampoline jumps (none),
fewer thread pool submissions (none, by default).

In practice, the overhead of testz is zero.

Take a look at the benchmarks in `BulkPureBenchmarks.scala`.

Running 500 test suites with 50 tests each, where all of the tests do nothing but
return success or failure, takes 500 ms (on my machine, of course), JIT totally
disabled (`-Xint`), including class loading time and `object` + static class
initialization. In practice, especially if running tests multiple times within the
same VM, a few things from the Java and Scala runtimes will usually be JITted and the
class loading won't be a factor, bringing it to below 10 ms.

Seriously, testz is NEVER responsible for slowdown, and if you find that it is, let me
know.

