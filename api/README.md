# API service

Back end for knowledge capture system described in the book [Growing
One System](https://leanpub.com/growing-one-system).

## Usage

Start it up with:

`clj -A:run`

Try it out with curl:

`curl -i http://localhost:8999/v1/hello`

## Development Mode

When developing, it's more friendly to use

`clj -A:dev`

This starts a well-equipped REPL on port 7888.
