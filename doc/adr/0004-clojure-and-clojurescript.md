# 4. Clojure and ClojureScript

Date: 2019-02-22

## Status

Accepted

## Context

Viable choices for back end implementation language, based on the dev team's skills include:

- Ruby (with Rails, naturally)
- Go
- Python
- Rust
- Java
- Scala
- Clojure
- Elixir

 Candidate choices for the front end include:

- ClojureScript
- Typescript
- Elm

The dev team has little experience with vanilla Javascript.

Virtually any combination of these languages can implement the constructs of ADR 3.

Any choice of languages will be unfamiliar to some readers, though some choices will be more foreign than others.

The dev team's most recent experience is with Clojure and ClojureScript.

## Decision

We will use Clojure for the API, and ClojureScript for the UI.

## Consequences

Code will require more explanatory text due to the unfamiliar syntax.

Where possible, we can use isomorphic code between back-end and front-end.