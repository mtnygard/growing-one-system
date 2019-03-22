# 3. Rich front end with API

Date: 2019-02-22

## Status

Accepted

## Context

We need to define the basic architecture of the system.

The GUI must be responsive. This implies at least some asynchronous behavior on the page, and probably means optimistic GUI updates.

Server-side page rendering for form submission is slow and *deeply* unfashionable now.

Excellent tools exist to define and implement HTTP based APIs.

## Decision

We will build the system as a back end API server with a rich front end. It may or may not be a single-page app, but page loads should be minimized in the important flows (i.e., when capturing information.)

We will create source modules that match this structure:

1. `api` for the back end service
2. `ui` for the front end code

## Consequences

We have an enormous array of frameworks and libraries to choose from. No matter what we pick, at least one person will think it's the dumbest choice in the world.

We must also decide how information will be passed between the front and back ends.

We have an open question about how to serve static assets and where they should live in the source tree.

We have an open question about testing, specifically how much integration testing is required.
