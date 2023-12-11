# Scenario Two: Highload causes offloading to the cloud

## Setup
- changed sieve function difficulty to return prime numbers under 10m (instead of 10k)
- high result timeouts to avoid clients to retry if the edge is already processing it


### clients
- 16 non-moving clients, each call 10 times
- `ScenarioHighload.kt`
- the client program gets the number of requests per client from the input
- result timeout set to 18s and ack to 4s

### Servers
- Cloud
  - Same as Distance scenario

- Potsdam:
  - Same as Distance scenario
  - set to process 160 requests (all requests)