# Scenario Three: Outage causes forwarding to the cloud

## Setup
- default sieve function for numbers below the 10k
- the edge GeoFaaS shutdowns after processing half of the requests, some requests still sent to the edge GeoFaaS and clients need to retry

### clients
- 5 non-moving clients call 10 times each
- `ScenarioOutage.kt`
- the client program gets the number of requests per client from the input
- ack and result timeout set to 4s

### Servers
- Cloud
  - Same as Distance scenario
  - set to process 25 requests (half)
  
- Potsdam:
  - Same as Distance scenario
  - set to process 25 requests (half)
