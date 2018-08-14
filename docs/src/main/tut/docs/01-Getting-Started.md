---
layout: docs
title: Getting Started
---

# {{ page.title }}

```scala
libraryDependencies += "org.scalaz" %% "testz-core" % "0.0.4"
```

Will get you the most basic building blocks of testz.

For the most basic, zero-dependency test harnesses:

```scala
libraryDependencies += "org.scalaz" %% "testz-stdlib" % "0.0.4"
```

For a way to run testz test suites:

```scala
libraryDependencies += "org.scalaz" %% "testz-runner" % "0.0.4"
```

For scalaz 7.2 support:

```scala
libraryDependencies += "org.scalaz" %% "testz-scalaz" % "0.0.4"
```

```tut:silent
import testz._
```

Come to the [Gitter](https://gitter.im/scalaz/testz).
