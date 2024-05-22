# Scenario Three: Outage, causes forwarding to the cloud

This experiment shows the system still works even if a GeoFaaS Bridge fails, while the client does/knows nothing extra.  
The edge GeoFaaS Bridge shutdowns after processing half of the requests and further client requests are routed to the cloud

## Setup

### Clients
- a non-moving client call 100k times
- the client program gets the number of requests per client from the input: `$ ScenarioOutage.kt <numClients> <numRequests>`
- ack and result timeout set to 70ms 

### Servers
- we use default sieve function (prime numbers below 10k)
- the edge GeoFaaS shutdowns after processing half of the requests, meanwhile some requests can be delivered to the edge GeoFaaS unprocessed and client(s) needs to retry

- Cloud
  - configs are same as Distance scenario
  - 1 thread for sieve function
  - set to process unlimited requests
  
- Potsdam:
  - configs are same as Distance scenario
  - 1 thread for sieve function
  - set to process 50k requests (half)
