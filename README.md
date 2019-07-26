# growing-one-system

Knowledge management system

# Developer setup

## Local Checkouts

This application uses a number of pre-release libraries and personal
forks. You will need to check out the following repositories in the
parent directory that contains this repository:

    git clone git@github.com:mtnygard/vase
    git clone git@github.com:jez/pandoc-sidenote
    git clone git@github.com:mtnygard/fern
    git clone git@github.com:clojure/spec-alpha2
    git clone git@github.com:cognitect/test-runner
    git clone git@github.com:mtnygard/frenpl

If you are missing any of these, you'll get an error like "Error
building classpath. Manifest type not detected..."

## Repository Hygiene

We use [Pre-commit](https://pre-commit.com/) to manage a lot of
housekeeping tasks. Install it with pip, then install the hooks in
your copy of the repo:

    pip install pre-commit
    pre-commit install

This uses the definition in `.pre-commit-config.yaml` to set up all
the necessary git-machinery in your clone.

The first time the hooks run, it takes a while to record the
repository's environment. If you want to get that out of the way
early, you can run the hooks once manually:

    pre-commit run --all-files

## Front end

We use [Shadow-cljs](http://shadow-cljs.org/) to build. Install it
locally with [Yarn](https://yarnpkg.com/en/docs/install):

    cd front-end
    yarn && yarn html
    yarn shadow-cljs watch app

This provides ClojureScript compilation with hot reloading. It runs a
web server in dev at http://localhost:8080/ that serves the compiled
assets.

## API

The API layer is built with Clojure. Install it on Linux, macOS, or
WSL according to the [instructions at Clojure.org](https://clojure.org/guides/getting_started).

## Atomist

A variety of housekeeping tasks will be performed by "software delivery machines" created using [Atomist](atomist.com). See the repositories under [Igg-less](https://github.com/igg-less) for details.

You won't be able to run these machines directly. They're part of the infrastructure for running the main project and site.

# Try It Out

The API module offers a CLI to let you have a conversation with your
model of the world. You can run the CLI in dev mode like this:

    cd cli
    clj -A:repl

Enter statements exactly as you would through the UI.

To package the CLI for standalone use:

    cd cli
    clj -A:uberjar

The resulting uberjar will be in `target/cli-1.0.0-SNAPSHOT-standalone.jar`.

To run the uberjar:

    java -jar target/cli-1.0.0-SNAPSHOT-standalone.jar

That's a mouthful, so you'll probably want to make an alias for it.
