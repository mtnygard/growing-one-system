# growing-one-system

Knowledge management system

# Developer setup

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
    yarn
    yarn shadow-cljs watch app

This provides ClojureScript compilation with hot reloading. It runs a
web server in dev at http://localhost:8080/ that serves the compiled
assets.

## API

The API layer is built with Clojure. Install it on Linux, macOS, or
WSL according to the [instructions at Clojure.org](https://clojure.org/guides/getting_started).
