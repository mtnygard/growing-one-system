# 2. Use AWS

Date: 2019-02-22

## Status

Accepted

## Context

The system should be available from multiple devices, with the same record of facts.

A desktop application would require some kind of synchronization via files, perhaps in Dropbox, OneDrive, GDrive, or the like. File locking and version conflicts are a concern.

A web application with a database handles the synchronization. However, a web application requires some place to run. In 2019, we can assume use of a cloud platform, but must decide which one to use.

The development team knows AWS the best. This could be a motive to pick Azure or GCP, if the dev team's learning is an objective.

This system will have a small number of users and operate at low scale. Requests will be very intermittent.

The operator would like to keep the monthly cost footprint down.

Cloud providers offer proprietary services with non-standard APIs. These can supply a lot of functionality, at the expense of lock-in on those services.

Embracing vendor services results in a large number of contingent decisions.

Wrapping vendor services is costly to implement and results in "lowest common denominator" functionality.

## Decision

We will build the system on AWS.

Furthermore, we will embrace vendor services without attempting to wrap them or isolate ourselves from them.

## Consequences

The dev team won't learn a new cloud platform.

The reader will learn how to build a complete system with AWS.

We will need to choose which AWS compute, storage, and networking services to use. Some of these choices will have serious cost implications.

We have the option to use AWS Cognito for user management and access control.

Build and deployment tools have excellent support for AWS, so we expect to have greater options with those.
