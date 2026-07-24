# Target Spring Boot 3.4 + Java 21 LTS

The initial `userservice` scaffold used Spring Boot 4.1 / Java 25; we downgrade the whole project to Spring Boot 3.4 and Java 21 LTS. Boot 4 / Java 25 are very new: most tutorials, community answers, and the Spring Cloud release train (Eureka, Gateway) target Boot 3.x, and version-alignment friction would burn time that should go into the design patterns this project exists to teach. Java 21 LTS is also what most employers actually run. Upgrade path stays open once the ecosystem catches up.
