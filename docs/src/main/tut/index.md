---
layout: home
title:  "Home"
section: "home"
---

Welcome to testz
================

testz is a Scala library for purely functional testing.

In its core, it provides abstractions and tools for assertions and test registration.

It also contains multiple extension modules, for extra utilities or integration with
other libraries.

Library Dependency
------------------

For the most basic building blocks of testz:

```scala
libraryDependencies += "org.scalaz" %% "testz-core" % "0.0.4"
```

For the most basic, zero-dependency test harnesses
(see the [simple example](./docs/01-first-example.html)):

```scala
libraryDependencies += "org.scalaz" %% "testz-stdlib" % "0.0.4"
```
:
For a way to run testz test suites
(see [here](./docs/07-runner.html) for details):

```scala
libraryDependencies += "org.scalaz" %% "testz-runner" % "0.0.4"
```

For scalaz 7.2 support (see [here](./docs/04-scalaz.html) for information on what that constitutes):

```scala
libraryDependencies += "org.scalaz" %% "testz-scalaz" % "0.0.4"
```

```tut:silent
import testz._
```

Come to the [Gitter](https://gitter.im/scalaz/testz).
