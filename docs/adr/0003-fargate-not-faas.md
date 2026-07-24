# Serverless via Fargate containers, not FaaS

"Serverless production" here means AWS Fargate (serverless containers) for the always-on services, plus a small number of true Lambdas only where work is genuinely event-triggered (e.g. the scheduled auction-close sweep). We do *not* run the services as Lambda/FaaS: Eureka discovery, STOMP WebSocket connections, long-running Proxy Bidder loops, and RabbitMQ consumers are all persistent by nature and fight the FaaS model, and JVM cold starts are costly. This preserves the architecture while still exercising real serverless where it fits.
