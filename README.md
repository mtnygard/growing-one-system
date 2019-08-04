# growing-one-system

Knowledge management system

# Developer setup

## Skipping Developer Setup

If you just want to try the examples from [Some Commands, Some Queries](#making-a-model) below, you can download a standalone jar file from the
[releases](https://github.com/mtnygard/growing-one-system/releases) tab.

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
    git clone git@github.com:mdiin/cambada

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

# Where Did My Data Go?

Right now, there are two storage methods supported. By default, all
data lives only in memory.

You can use `-d` to point to a local Datomic Pro database running in
dev mode for local storage. (Future releases will support Datomic Pro
remote and Datomic Cloud.)

# Initialization

You can keep commands in a file (or several files). If you supply `-e
_filename_` one or more times, those files will be loaded in order
before your REPL prompt appears.

# Some Commands, Some Queries

Once you start the CLI REPL, you'll get a prompt like `=>`. That's
your opportunity to make a model, add some facts, and ask some
questions.

## Making a Model

The model consists of two parts, attributes and relations. An
attribute has a name, a type, and a cardinality. You can make an
attribute like this:

```
attr name string one;
attr completed? boolean one;
```

After the keyword `attr` comes the attribute name. The second word is
the type, which can be one of:

- `string`
- `boolean`
- `instant` (a date time stamp with time zone)
- `long`
- `double`

The last part is the cardinality, either `one` or `many`. Cardinality
`many` means the attribute can take on a set of values for a single
relation. Keep in mind, though, the set doesn't have any particular
order. (That's because a value's ordinal position is actually another
fact, so you need to define a relation which has the ordinal as its
own attribute.)

With some attributes defined, you can make a _relation_. A relation is
a subset of the cross-product of its attributes. That's a precise
definition, but maybe not an intuitive one. An example may help.

Suppose we have a set of colors with the elements Red, Green, and
Blue. That implies there's an attribute for color.

```
attr color string one;
```

Then we might have a set of shapes: Circle, Square, Triangle.

```
attr shape string one;
```

The cross-product of those sets contains every pair of color and
shape, 9 pairs in all. A relation of color and shape is any subset of
that cross-product. So the set `{ (Red, Circle), (Green, Triangle), (Blue, Square) }`
is a relation with three elements, each of which is a pair.

To declare a relation, we enumerate its sets:

```
relation colored-shape color shape;
```

## Add Some Facts

From this point one, we can declare that members of that relation
exist:

```
colored-shape "Red" "Circle";
colored-shape "Green" "Triangle";
colored-shape "Blue" "Square";
```

We can think of this as adding members to the relation, but strictly
speaking, each command executed a function from the old relation to a
new relation. At any given time, `colored-shape` refers to exactly one
subset of color/shape pairs.

## Ask Some Questions

We can also use the relation name to query its members:

```
colored-shape ?color ?shape;
```

The `?color` word indicates a logic variable. `?shape` is
another. We're asking for all the pairs from the `colored-shape`
relation that meet the constraints. Since we haven't actually applied
any constraints, we'll get back every pair of color and shape.

Suppose we want to find only the green shapes:

```
colored-shape "Green" ?shape;
```

Now, we're looking for pairs that have "Green" in the first position
(the color) and any value in the second position. That means we'll get
back a set `#{ ("Green", "Triangle") }`. That is a set with one
element, where the element is a pair of strings.

In this case, the names of the logic variables don't really matter,
except for one case... Suppose we use the same logic variable in both
positions:

```
colored-shape ?a ?a;
```

We won't get any results, because we're asking for both values to be
the same. A logic variable must have the same value everywhere it
appears in a query. In this case, we don't have any pairs that have
the same value in both positions. But other relations might have a
good reason to do that!

If we try to fill in both positions, then we aren't making a query any
more:

```
colored-shape "Green" "Triangle";
```

That's just making a new fact. Later, we'll see how you would make an
existence query.

## Constraints

If you've been trying these statements at a REPL, you might have
experimented a bit and realized that you can put pretty much any
string in a name. That's what the attribute declaration says, it's
just a string.

But what if you want the colors in `colored-shape` to come from a
limited set of enumerated values? That requires a constraint.

Constraints are not placed on the attributes, but on their uses in a
relation. We could put in a series of definitions like this:

```
attr name string one;

relation colors name;
relation shapes name;
relation colored-shape2 (name in colors) (name in shapes);
```

Notice that here, instead of saying that `color` is an attribute with
a string value, we're saying there's a unary relation called
`colors`. Then `colored-shape2` is constrained so that the only
allowed values for its first position are those values found in the
`colors` relation. Likewise for shapes.

If you try to add a fact that breaks these constraints, you will get
an error and the fact will be rejected.

At the moment, `in` is the only constraint we support. It only works
when checking one value against the first position in some other
relation.

### When to Constrain

Don't always assume that a constraint is the answer to your modeling
problem. Remember the purpose of a constraint is to _exclude_ facts
from your understanding of the world. Sometimes it's better to accept
a fact that is stated, even if it makes no sense at the time. For
instances, if someone tells you a shape is "mauve", and your set of
colors doesn't include `mauve`, that doesn't change the reality of the
shape, just your ability to represent it.

Often, it is better to apply restrictions at the time of a query
instead of when accepting facts. Then you can write queries to find
out the facts that are contradictory or nonsensical... because finding
out there are conflicts in your knowledge base is really important!

## Shorthand for Repeated Facts

When you have a series of declarations, it can be tedious to repeat
the relation name over and over. We have a shorthand notation that
uses a colon (":") to mean "hold the left side constant."

For example, we can restate the colors from before like this:

```
colors: "Red" "Green" "Blue";
colors: "Mauve";
```

That is exactly as if you had supplied:

```
colors "Red";
colors "Green";
colors "Blue";
colors "Mauve";
```

The colon shorthand can do more than just repeat the relation
name. Suppose you have a relation with 2 positions:

```
colored-shape "Red": "Triangle" "Circle" "Square";
```

This will add three facts about red shapes.

Since the colon means "hold the left side constant", you can use it
when there are more than one "missing" values on the right:

```
colored-shape:
  "Red"    "Triangle"
  "Green"  "Circle"
  "Blue"   "Square"
  "Mauve"  "Pentagon"
  ;
```

This makes a nice looking notation that resembles a table
declaration.

Of course, you have to supply enough values to completely fill the
facts. If there are any leftovers, the interpreter will throw an
error.

## Joining Relations

Let's make another few relations:

```
relation animal name;

attr large? boolean one;
attr furry? boolean one;
relation animal-body name large? furry?;

attr habitat string one;
relation animal-habitat name habitat;
```

Here we see a common pattern. Instead of trying to make one giant
relation with everything in it, we make several smaller relations that
are about specific aspects (or facets) of the facts. Unlike an RDBMS,
these relations don't allow "null" values anywhere.

Here's how we could use these relations:

```
animal "lynx";
animal-body "lynx" false true;
animal-habitat "lynx" "North America";
```

So let's query for furry animals in North America:

```
animal ?name,
animal-body ?name ?large true,
animal-habitat ?name "North America";
```

A few things to notice about this query. First, we have several
"clauses" connected with commas. That means we only want results that
simultaneously satisfy all of these clauses. In other words, the
clauses are implicitly "anded" together.

Second, notice we defined an attribute called `large?` but the logic
variable is `?large`. It's the `?` at the front that makes it a logic
variable.

If you run this query at the REPL, you'll get a result that looks like this:

```
|    0 |     1 |    2 |             3 |
|------+-------+------+---------------|
| lynx | false | true | North America |
```

(In an upcoming release, the columns will be named instead of
indexed.)

Even though `?name` appears three times in our query, it's kind of
pointless to include the value three times in the result since we know
it has to be the same. So the result for `?name` only appears the
first time it shows up in the query. That means the second column
(index 1) corresponds to `?large`, The `true` in the third column
(index 2) comes from the constraint on the `furry?` attribute, then we
see "North America" which was the constraint on the `habitat`
attribute.

Suppose we wanted to find out the characteristics of the lynx. We
would need to constrain the name to be "lynx" everywhere. The best way
to achieve that is this:

```
animal ?name,
animal-body ?name ?large true,
animal-habitat ?name "North America",
= ?name "lynx";
```

The final clause requires that `?name` must be equal to "lynx". Pretty
straightforward. You can also use some other operators in their own
clauses: `<`, `<=`, `!=`, `>`, and `>=`. Equal and not-equal work on
any type. Less than, greater than, and their kin work only on numbers:
long or double.

(A future release will extend these to work on instants as well.)

What happens if you try to join relations that don't have matching
facts?

```
animal "leopard";
animal-body "leopard" true true;

animal ?name,
animal-body ?name ?large ?furry,
animal-habitat ?name ?habitat;
```

If you run those commands at the REPL, you'll get

```
|    0 |     1 |    2 |             3 |
|------+-------+------+---------------|
| lynx | false | true | North America |
```

What happened to our leopard? Remember that we get results where all
the logic variables can be satisfied. There is no way to unify
"leopard" with `?name` in the `animal-habitat` relation, so there's no
possible value to assign to `?habitat` with "leopard" as the name.

RDBMSs deal with this via an elaborate scheme of left joins, right
joins, inner joins, and outer joins... all so they can return NULLs
that you have to protect against later! If you want partial results,
run partial queries!

```
animal ?name, animal-body ?name ?large true;

|       0 |     1 |    2 |
|---------+-------+------|
|    lynx | false | true |
| leopard |  true | true |
```

Oh, as you can see in this query, clauses don't have to start on new
lines. It's just easier to read that way most of the time.

# Controlling the REPL

There are a small handful of "commands" that change the behavior of
the REPL itself. These are not processed as facts or queries:

| `:quit`    | Exit the REPL            |
| `:noprint` | Turn output printing off |
| `:print`   | Turn output back on      |

`:noprint` is helpful when you have a lot of declarations to process
and you don't want tabular noise streaming down your screen like The
Matrix.

Files that you supply with `-e` are processed with `:noprint`. You can
embed `:print` in the files to turn output on.
