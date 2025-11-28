# Spring Cloud Config Server (local/native backend)


This is a minimal Spring Cloud Config Server that serves configuration from the local `config-repo` directory in the workspace.

By default this workspace config is now provided from a local Git repository located at `config-repo`.

Run (from workspace root):

```bash
cd config-server
mvn spring-boot:run
```

The server starts on port `8888`. Example endpoint to fetch the default `application` properties:

```
http://localhost:8888/application/default
```

Notes:
- The server is configured to use a Git backend pointing to the local repository at `file:///workspaces/code-snippets/config-repo`.
- The local repo was initialized and committed in this workspace; the default branch is `main`.

