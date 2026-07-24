-- Runs once on first Postgres start (empty volume).
-- DB-per-service, one shared instance. Keycloak gets its own DB too.
-- Services with no DB (gateway, discovery, aiservice, notificationservice) omitted.
CREATE DATABASE userservice;
CREATE DATABASE auctionservice;
CREATE DATABASE agentservice;
CREATE DATABASE chatservice;
CREATE DATABASE keycloak;
