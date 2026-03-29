# business-qa-mcp-server

`business-qa-mcp-server` is a standalone Spring Boot MCP Server module using HTTP/SSE transport.

## Build

```bash
mvn -pl business-qa-mcp-server -am clean package -DskipTests
```

## Run (HTTP/SSE mode)

```bash
java -jar business-qa-mcp-server/target/business-qa-mcp-server-1.0.0-SNAPSHOT.jar
```

Default endpoints:

- SSE endpoint: `http://localhost:8092/sse`
- Message endpoint: `http://localhost:8092/mcp/message`

## Exposed MCP tools

- `searchKnowledge(keyword)`
- `currentServerTime()`
- `add(a, b)`
