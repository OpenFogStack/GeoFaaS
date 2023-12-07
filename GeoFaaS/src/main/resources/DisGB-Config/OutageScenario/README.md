# Scenario Three: Outage causes forwarding to the cloud

## Setup
- sieve function for numbers below the 10k (default)
- the edge GeoFaaS shutdowns after "10th" request, some requests still sent to the edge GeoFaaS and clients need to retry
- there is a 5s timeout for an Ack, and a 4s for a result from GeoFaaS
- 
### clients program
- `ScenarioOutage.kt`
- the client program gets the number of requests per client from the input

### Servers
- Cloud
  - Same as Distance scenario
- TUBerlin:
  - Same as Distance scenario
  
- Potsdam:
  - Same as Distance scenario
