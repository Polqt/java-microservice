# Run agentservice Skeleton

Status: ready-for-agent

## Parent

`../spec.md`

## What to build

Create the smallest runnable `agentservice` vertical skeleton. It must start as a Java 21 Spring Boot service, connect to its own Postgres database and RabbitMQ, register with Eureka, and expose operational health. This ticket establishes a working service boundary before Proxy Bidder behavior is added.

## User stories covered

- Foundation for stories 30–31.

## Acceptance criteria

- [ ] `agentservice` starts with environment-driven Postgres, RabbitMQ, and Eureka configuration.
- [ ] The service uses its own `agentservice` database in the shared local Postgres instance.
- [ ] The health endpoint reports the application as available when required infrastructure is reachable.
- [ ] Eureka lists `agentservice` under its configured application name.
- [ ] A minimal persistence check proves the service can write and read through its own database connection.
- [ ] No Proxy Bidder business behavior, controller, or Rabbit listener is added yet.

## Blocked by

None - can start immediately.
