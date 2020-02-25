# Sidenodes

A clojure documentation generator forked from [marginalia](https://github.com/gdeer81/marginalia/).

## Features

It is limited to projects that use deps.edn.

In the current early version the following features are supported:
- Generating table of contents containing dependencies and namespaces
- One html file per namespace showing code and comments side by side
- Support for markdown in comments

## Usage

Add an alias to your deps.edn file

```clojure
:sidenotes
{:extra-deps
{sidenotes/sidenotes {:mvn/version "RELEASE"}}
:main-opts  ["-m" "sidenotes.core"]}
```

and call it

```bash
clojure -A:sidenotes

```
