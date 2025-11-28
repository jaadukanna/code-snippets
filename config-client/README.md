# Config Client

Tiny Spring Boot client that imports configuration from the Spring Cloud Config Server at `http://localhost:8888`.

Run the server first (from workspace root):

```bash
cd config-server
mvn spring-boot:run
```

Then run the client:

```bash
cd config-client
mvn spring-boot:run
```

Open `http://localhost:<client-port>/greeting` to see the value of `greeting.message` provided by the config server.
