# Scenario Two: High Load, causes offloading to the cloud

This scenario shows if an edge doesn't have proper resources to respond a client, it can offload the request transparently, i.e. the client gets a response and still doesn't need to do/know anything extra.

## Setup


### Clients
- high result timeouts to avoid clients to retry if the edge is already processing it
- Every 1 sec, 1,2,4,8, and 16 non-moving clients call, for a total 10 times each
- the client program gets the number of requests per client from the input: `$ ScenarioHighload.kt <numClients> <numRequests>`
- result timeout set to 25s and ack to 4s

### Servers
- we deploy the "sievehard" function
- set both to process all requests (`numClients * numRequests`), i.e. 10, 20, 40, 80, and 160



- Cloud
  - configs are same as Distance scenario

- Potsdam:
  - configs are same as Distance scenario