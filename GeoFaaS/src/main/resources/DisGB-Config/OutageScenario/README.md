# Scenario Three: Outage, causes forwarding to the cloud

This scenario shows if an edge doesn't have proper resources to respond a client, it can offload the request transparently, i.e. the client gets a response and still doesn't need to do/know anything extra.

## Setup

### Clients
- 10 non-moving clients call 100 times each
- the client program gets the number of requests per client from the input: `$ ScenarioOutage.kt <numClients> <numRequests>`
- ack and result timeout set to 4s

### Servers
- we use default sieve function (prime numbers below 10k)
- the edge GeoFaaS shutdowns after processing half of the requests, meanwhile some requests delivered to the edge GeoFaaS and clients need to retry

- Cloud
  - configs are same as Distance scenario
  - 1 thread for sieve function
  - set to process 500 requests (half)
  
- Potsdam:
  - configs are same as Distance scenario
  - 1 thread for sieve function
  - set to process 500 requests (half)
