# scala-native-example-app

An example project showing how to use Scala-Native in SBT, together with third
party libraries (in this case [Scalatags](https://github.com/lihaoyi/scalatags))
and a test suite (using [uTest](https://github.com/lihaoyi/utest)).

## Using this project

You should be able to import this project into IntelliJ-IDEA or any other Scala
IDE or editor without issue.

To build an executable without running:

```
$ ./mill show example.nativeLink
...
"out/example/nativeLink/dest/out"
```

To then run that executable:

```
$ out/example/nativeLink/dest/out --help
JSON Reformatter
Pretty-print JSON or minify it
  --src <path>    Source file to load JSON from; defaults to stdin if not given
  --dest <path>   Destination file to write JSON to; defaults to stdout if not given
  --indent <int>  Indentation to pretty-print the JSON with; default 4, pass -1 to minify instead

$ echo [1,   2,   3] | out/example/nativeLink/dest/out --dest out.json
[
    1,
    2,
    3
]

$ echo [1,   2,   3] | out/example/nativeLink/dest/out --indent -1
[1,2,3]

$ echo [1,   2,   3] | out/example/nativeLink/dest/out --dest out.json

$ cat out.json
[
    1,
    2,
    3
]
```

The executable should run instantly, without the ~1s startup overhead you may be
used to with Scala programs running on the JVM.

```
$ time out/example/nativeLink/dest/out --help
...
real    0m0.009s
user    0m0.003s
sys     0m0.003s  
```

To run tests:

```
$ ./mill example.test
...
-------------------------------- Running Tests --------------------------------
+ example.ExampleTests.minified 0ms
+ example.ExampleTests.indent0 0ms
+ example.ExampleTests.indent2 0ms
```

You can of course use the full functionality of uTest to
[select which tests to run](https://github.com/lihaoyi/utest#running-tests).

## Other libraries

That's all that is necessary to try using this project. Feel free to try
building larger applications using Scala-Native using this template, or trying
out some of the other third-party libraries that are available for Scala-Native:

```scala
ivy"com.lihaoyi::utest::0.7.7"
ivy"com.lihaoyi::ujson::1.2.3"
ivy"com.lihaoyi::upickle::1.2.3"
ivy"com.lihaoyi::os-lib::0.7.2"
ivy"com.lihaoyi::sourcecode::0.2.3"
ivy"com.lihaoyi::fastparse::2.3.1"
ivy"com.lihaoyi::fansi::0.2.10"
ivy"com.lihaoyi::scalatags::0.9.3"
ivy"com.lihaoyi::pprint::0.6.1"
ivy"com.lihaoyi::mainargs::0.2.1"
```
