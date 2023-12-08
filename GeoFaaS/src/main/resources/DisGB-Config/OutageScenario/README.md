# Scenario Three: Outage causes forwarding to the cloud

## Setup
- sieve function for numbers below the 10k (default)
- the edge GeoFaaS shutdowns after "10th" request, some requests still sent to the edge GeoFaaS and clients need to retry
- there is a 5s timeout for an Ack, and a 4s for a result from GeoFaaS


### clients program
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
