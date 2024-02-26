# Scenario Two: High Load, causes offloading to the cloud

This experiment shows the system still works even if a GeoFaaS Bridge fails, while the client does/knows nothing extra.  
The edge GeoFaaS Bridge shutdowns after processing half of the requests and further client requests are routed to the cloud

## Setup


### Clients
- high result timeouts to avoid clients to retry if the edge is already processing it
- Every 5 sec, 1,2,4,8, and 16 non-moving clients call, for a total 10 times
- the client program gets the number of requests per client from the input: `$ ScenarioHighload.kt <numClients> <numRequests>`
- result timeout set to 25s and ack to 4s

### Servers
- we deploy the "sievehard" function
- set both to process all requests (`numClients * numRequests`), i.e. 10, 20, 40, 80, and 160



- Cloud
  - configs are same as Distance scenario
  - 10 threads for sievehard function

- Potsdam:
  - configs are same as Distance scenario
  - 2 threads for sievehard function