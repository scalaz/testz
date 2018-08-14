<!-- <img align="right" src="resources/testz.png" height="200px" style="padding-left: 20px"/> -->

# testz

The pure testing library.

[![Join the chat at https://gitter.im/scalaz/testz](https://badges.gitter.im/scalaz/testz.svg)](https://gitter.im/scalaz/testz?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Pitch

1. testz was designed as a reaction against the current state of tests in Scala; if you're averse to
   tech shit-talking, stop reading now, but keep in mind that without other libraries I would have
   never developed testz.

2. testz, unlike the testing frameworks that dominate the Scala testing landscape, is not a framework.
   It's purely a library; there is no inversion of control going on. Not only *can* you replace any
   part of testz that you'd like to, but it's actually *practical* for users to do that.

3. a) testz achieves this in a few ways. Firstly, by having a small codebase; I can say without
   reservation that testz-core is the smallest testing library in Scala, because it (at time of writing)
   doesn't exceed 100 lines. But more importantly, there are many things that testz deliberately does
   not do, which I will list before going into the extra things testz does.
   b) (an aside: I will include a (1) next to every time I say "testz doesn't provide this" or "testz does provide this" *if*
   the user can change it)

4. a) testz doesn't provide automated test detection, a la SBT's `TestFramework` API, unlike basically every Scala testing framework.
   This is because tests written with testz, just like any other code, are run from a `main` function.
   b) This affords the user infinite control of how their suites are run: want to separate integration tests from unit tests?
   Maybe by having multiple modules with different `main` functions, or by passing a flag? Just *do it*, and do it the way you want.
   c) Want to avoid running some suites in parallel, but run others in parallel?
   testz doesn't even provide test suite parallelism; that's your job to provide. In the example of the scalaz 8 test suite,
   adding parallelism meant changing 7 lines in the `main` function.

5. a) testz also doesn't provide matchers (1). I can barely describe to you the pain caused by using (and not using) matchers with specs2 and scalatest.
   For those who aren't familiar, matchers give the framework a way to write assertions combined with additional diagnostics on test failure.
   They also provide a "fluent DSL" for writing tests, but tests that use that DSL often don't do what you'd think (this is partially because assertions
   are side effects in these frameworks).
   b) testz (1) doesn't care about diagnostics on test failure, nor providing a DSL to obscure what's going on.
   You can provide diagnostics if you want - testz may provide a submodule with functions that provide nice diagnostics - but that's
   orthogonal to the job testz performs right now.

6. One other reason why matchers exist in other libraries is to pass source code line numbers around for diagnostics. This is yet another thing
   I don't consider useful enough to put inside the core of the library (1), and incredibly error-prone to pass around properly when you take into
   account that the premise of testz is that tests are just code. If you want to find a test, you can use its name; that at least isn't sensitive
   to small refactorings of test code.

7. a) testz doesn't provide side-effecting test registration or assertions. Since registering tests to be run isn't a side effect, there's no way to
   (for example) accidentally register a test in a callback, so it isn't reliably registered before a test suite finishes. The same improvement
   applies to putting assertions in the wrong place. This also makes the control flow much easier for users to reason about.
   b) One thing that always annoyed me about testing frameworks in Scala (specifically scalatest and specs2) is that most of the time, assertions
   return some kind of value in some kind of `-Result` type... but they perform a side effect anyway! So if you throw away the value, nothing changes.
   I think this is pretty damn evil, and so unless you actually *use* the `Result` value somehow in testz, the test outcome is not affected. (1)

8. testz also doesn't provide a type for test suites for you to extend; there are multiple reasons I'm able to do this.
   There's no `TestFramework` integration, as users run their own test suites themselves. There's no tangled web of implicits and
   methods necessary to write tests (I'm looking at you, specs2).

9. No test harnesses in testz use by-name arguments. I think by-names are in general a misfeature of Scala; in exchange
    for a tiny bit of concision, you no longer know which code is executing where. Instead, testz uses `Function0`, AKA
    `() => A`, to be explicit.

10. This may be the lack of a feature or a feature, but testz doesn't use implicits for anything.
    Users writing tests don't need implicits and the `main` method doesn't need implicits.

11. testz doesn't require any dependencies, not even scalaz. You can get running with just testz-stdlib and testz-runner
    fairly easily, modules with zero dependencies, and Scalaz 8 does exactly that. I provide multiple integration modules,
    for example, testz-scalaz, testz-specs2, and testz-scalatest, none of which are actually *needed*.

12. testz doesn't include tools for property testing; at least, not yet. Every single property test I have ever seen in code works better
    as an exhaustive test with a set of meaningful test data; they always repeatedly test useless cases, and don't test useful
    ones. Random data generation is fine for a single thing, and that's exploring the space of test data when you're not sure
    which edge cases you might have. But you always need to know the implementation of a function to know which test data is meaningful,
    and to really know which edge cases exist. I know this point is contentious within the FP community, so I plan to elaborate on it later.

13. Alright, now that I have everything out of the way that testz *doesn't* do, it's time to talk about the things it *does* do.

14. In my experience, testz provides the fastest-executing tests out of any testing library or framework in the Scala ecosystem.
    This is only possible because it does so little. I once tried executing 10000 empty (succeeding) tests in specs2; it took over a minute,
    and specs2 is inherently parallel. It takes under a tenth of a second (synchronously) with testz, *even if all of them fail*.

15. Tests written with testz can be executed on any other testing framework, with zero changes.
    You can start using testz with specs2 (and soon scalatest) right now, integrating it into an
    existing project slowly while (in my opinion) drastically improving the API. Then, once all of the
    tests are switched over, you can swap to using pure testz, with all of the benefits that brings.

16. a) testz gives you exact, precise control over resources. The whole "BeforeAndAfter" (execute side effects before and after tests)
    and "fixture" (per-test resource allocation) paradigm that's used by other testing frameworks is totally abandoned in testz in favor
    of something better; the resource API allows you to easily and safely share resources (and large testing datasets) between tests without using side effects
    or introducing a complex execution model like utest.
    b) This is just one example of a pattern in testz; I really, really care about letting you use resources properly. Having tight control
    over when test data is reachable for GC, or is cleaned up.

17. A lot of testz tests are written in a way which is more abstract than other tests; for example, generating documentation for a test
    suite without running any tests is an easy task.

18. testz lets you customize *everything*. This is partly because there is no type for test suites; you can be as abstract or as concrete
    as you'd like, introduce custom test harness types, anything. For example, from scratch, adding test-level, user-controlled parallelism
    to a test suite written with testz just requires writing a short utility (around 7 lines total), and using it from a test. Nothing special.

19. The simplest test harness type for testz enforces pure tests; when you use this harness, tests can't perform effects of any kind.
    It's also easy to add your own harness types for any effect type you want, including monads; or even to completely throw out
    the `testz.Result` type if you're not feeling it. *Your choice.*

20. That sums up most of what I have to say about testz. For those who will inevitably ask, I'm familiar with puretest, lambdatest, minitest,
    utest, scalatest, and specs2; I believe none of these come close to delivering a library. One consequence is, I don't consider any of them to be functional either;
    frameworks are emphatically not functional programming from where I'm standing.


## Contributing

This project is released under the scalaz code of conduct.
