# Support Remote Capabilities

Rather than support every possible use-case, it would be very useful to access custom services through REST or gRPC.  This allows developers to only expose the API for developers/users to access their capabilities.

## REST API

~~~mermaid
sequenceDiagram
actor user
participant client
participant server

opt Discover Capability
    user ->> client: Add server
    client ->> server: "Request API"
    server ->> client: "Response JSON"
end
opt configure capability
    note over user,client: Map UI info to request fields
end
opt Execute Capability
    user ->> client : Execute capability
    client ->> server : Send request
    server ->> client : Send Response
end
~~~

The REST API can be obtained through a JSON configuration.  The configuration location is different based on the framework.

| Property | Spring Boot | FastAPI | Django | Express  |Go
| :-: | :-: | :-: | :-: | :-: | :-: |
| Check Schema | `/v3/api-docs` | `openapi.json` | `/api/schema` | `/openapi.json` | `/swagger/doc.json` |
| Addtional Steps | Add springdoc-open-starter-webmvc-ui |

* Spring Boot [Example](./example_springboot_post.json)
* FastAPI [Example](./example_fastapi.json)

| Field | Description | SpringBoot | FastAPI |
| :-: | :-: | :-: | :-: |
| `openapi` | String version of OpenAPI | x | x |
| `info` | {`title`, `version`} | x | x |
| `servers` | {`url`, `description`} | x | - |
| `paths` |
| `components` | data objects used in `paths` | x | x |

### REST Limitations

Within this application, it makes sense to extract some features to pass to REST services.  This can be performed through GET.  A set of parameters are extracted from the annotation (duration, bandwidth, center frequency).  The parameter is matched with the schema data types.  If match, the option is included in a combobox for simple entering.

One concern is the size of the input data.  Passing high sample rate IQ data for more complex analysis will be expensive.

* `Max Request Body`
  * In Spring Boot, there is a 10MB or 25 MB limit to the request body
* Passing IQ (double[][])
  * Comma Separated Values - Uses textual representation per double.
    * With high precision there can be 300 % increase in representation size.
    * Truncated precision can be used to reduce bandwidth but at the cost of signal integrity.
  * Base64 Encoding - Increase bytewise size by 33 %.

**NOTE** This is a reason why GRPC maybe more appropriate with better efficiency of representing and transferring the IQ data.  But for now the REST service can be applied to support passing a short burst in being redirected to external functions with a less strict contract.

### Paths

| Types | Description |
| :-: | :-: |
| DELETE | Remove a resource from the database. |
| GET | Read, retrieve data from service. |
| POST | Create, allows client to push data in the request body instead of the URL. |
| PUT/PATCH | Update a resource |

### Dynamic Object

This follows from [Example Spring Boot Post](./example_springboot_post.json).

~~~mermaid
classDiagram
class ClusterRequest {
    numStates : int
    features : int
    observations : double[][]
}
class ClusterResponse {
    clusters : int
    labels : int[]
}
~~~

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
~~~

## gRPC

gRPC remote procedure call has a tight contract of the data structures and service signatures.

* ServerReflection can support dynamic discovery.

~~~protobuf
service IQSignalService {
  // Mode 1: Returns a single classification (e.g., "WiFi", "LTE")
  rpc ClassifySignal (IQBatch) returns (ClassificationLabel);

  // Mode 2: Returns a list of discrete events/signals detected in the batch
  rpc DetectEvents (IQBatch) returns (DetectionList);
}

message ClassificationLabel {
  string label = 1;
  float confidence = 2;
}

message DetectionList {
  repeated Detection detections = 1;
}

message Detection {
  double start_time = 1;
  double frequency_center = 2;
  string signal_type = 3;
}
~~~