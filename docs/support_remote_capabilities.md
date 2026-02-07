# Support Remote Capabilities

* Point to the REST API

~~~mermaid
sequenceDiagram

participant client
participant server

opt Discover Capability
    client ->> server: "Request API"
    server ->> client: "Response JSON"
~~~

## REST API

The REST API can be obtained through a JSON configuration.  The configuration location is different based on the framework.

| Property | Spring Boot | FastAPI | Django | Express  |Go
| :-: | :-: | :-: | :-: | :-: | :-: |
| Check Schema | `/v3/api-docs` | `openapi.json` | `/api/schema` | `/openapi.json` | `/swagger/doc.json` |
| Addtional Steps | Add springdoc-open-starter-webmvc-ui |

* Spring Boot Example
* FastAPI Example

| Field | Description | SpringBoot | FastAPI |
| :-: | :-: | :-: | :-: |
| `openapi` | String version of OpenAPI | x | x |
| `info` | {`title`, `version`} | x | x |
| `servers` | {`url`, `description`} | x | - |
| `paths` |
| `components` | data objects used in `paths` | x | x |

### Paths

| Types | Description |
| :-: | :-: |
| DELETE | Remove a resource from the database. |
| GET | Read, retrieve data from service. |
| POST | Create, allows client to push data in the request body instead of the URL. |
| PUT/PATCH | Update a resource |

## Dynamic Object

~~~java
    import com.fasterxml.jackson.databind.ObjectMapper;
    import com.fasterxml.jackson.databind.node.ObjectNode;

    ObjectMapper mapper = new ObjectMapper();

    // Create a generic JSON object on the fly
    ObjectNode root = mapper.createObjectNode();
    root.put("numStates", 3);
    root.put("features", 2);
    root.putArray("observations")
        .addArray().add(1.2).add(3.4)
        .addArray().add(5.6).add(7.8);

    String jsonString = mapper.writeValueAsString(root);
    // Now send this jsonString via POST...

~~~
