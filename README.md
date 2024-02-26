# GeoFaaS

Yet another edge-to-cloud FaaS platform. A distributed solution that routes client requests using their location to the closest FaaS server.  
Under the hood it leverages the Distributed [Geobroker](https://github.com/MoeweX/geobroker) (DisGB) and lightweight FaaS systems (e.g. [tinyFaaS](https://github.com/OpenFogStack/tinyFaaS)). 


## Remote server setup
An editor instead of `vi`? Helix or NeoVim! , and `tmux` for remote administrations. Plus `bat`, a better `cat`!
```
sudo apt update && sudo apt install -y neovim libavutil-dev tmux bat git-core make zip htop # the last 3 are dependencies
```
### `java`
```
sudo apt install default-jdk
```

### Golang 1.20.6
```
mkdir ~/src && cd ~/src
wget https://go.dev/dl/go1.20.6.linux-arm64.tar.gz #for raspberries
wget https://go.dev/dl/go1.20.6.linux-amd64.tar.gz #for amd based (cloud)
sudo tar -C /usr/local -xzf go1.20.6.linux-arm64.tar.gz 
rm go1.20.6.linux-arm64.tar.gz && cd .. && rmdir src/
hx ~/.profile # on helix edior, alternatively vim or nano, etc.
```
add these two below lines and save, then `source ~.profile` from terminal: 
```
   PATH=$PATH:/usr/local/go/bin  
   GOPATH=$HOME/go
   alias bat="batcat"
```
### Docker 
- [Official recipe for raspberry pi](https://docs.docker.com/engine/install/raspberry-pi-os/#install-using-the-repository)
- [For Debian](https://docs.docker.com/engine/install/debian/). you can install both the latest or a specific version of docker
- I needed to add the current user to the docker group and logout/login. `sudo groupadd docker` and `sudo usermod -aG docker ${USER}`. For more, check the [post-install steps](https://docs.docker.com/engine/install/linux-postinstall/)

### Setup codes (To be updated)
```
cd Documents/ # optional path
git clone https://github.com/OpenFogStack/tinyFaaS.git # get tinyfaas
 
tmux # run the line below in tmux and detach
cd tinyFaaS && make
# Optionally upload some functions:
cd tinyFaaS && ./scripts/upload.sh "test/fns/sieve-of-eratosthenes" "sieve" "nodejs" 1 && cd .. 
mkdir geobroker && mkdir geobroker/config

# Setup geobroker server Jar + configs from your pc
scp GeoBroker-Server.jar raspi-alpha:~/Documents/geobroker
scp DistanceScenario-LAN/disgb-registry.json raspi-beta:Documents/geobroker/config && scp DistanceScenario-LAN/disgb-registry.json raspi-alpha:Documents/geobroker/config && scp DistanceScenario-LAN/disgb-registry.json raspi-gamma:Documents/geobroker/config && gcloud compute scp DistanceScenario-LAN/disgb-registry.json instance-1:Documents/geobroker/config

# Setup GeoFaaS Jar
scp GeoFaaS/out/GeoFaaSServer.jar raspi-beta-wlan:Documents/
# Setup GeoFaaS scenarios Jar from /out
scp GeoFaaSServer.jar raspi-beta:Documents/ && scp GeoFaaSServer.jar raspi-gamma:Documents/ && scp GeoFaaSServer.jar raspi-delta:Documents/ && scp ClientGeoFaaS-HighloadScenario.jar raspi-alpha:Documents/clients && scp ClientGeoFaaS-OutageScenario.jar raspi-alpha:Documents/clients && scp ClientGeoFaaS-DistanceScenario.jar raspi-alpha:Documents/clients && gcloud compute scp GeoFaaSServer.jar instance-1:Documents/ 
```
Make a config file for geoBroker server `hx geobroker/config/disgb-berlin.toml`
```toml
# server information
[server]
brokerId = "Berlin"
port = 5560
granularity = 5
messageProcessors = 2

    [server.mode]
    name = "disgb_subscriberMatching"
#    name = "disgb_publisherMatching"
    brokerAreaFilePath = "config/disgb-registry.json"
    brokerCommunicators = 2
```
- 'granularity' is the accuracy of location queries. the bigger, the smaller tiles of the world map, therefore more accuracy.
- 'messageProcessors' is the number of ZMQ message processors (check DisGBSubscriberMatchingServerLogic.java)
- 'brokerCommunicators' is the number of ZMQ message communicators; is also a parameter used in each message processor and broker communicator (ZMQProcessStarter.java)


## Running
- Run geoBroker servers in Distributed mode (frankfurt & paris sample):
  1. in project source(frankfurt & paris sample): `java -jar GeoBroker-Server/out/GeoBroker-Server.jar GeoBroker-Server/src/main/resources/jfsb/disgbSM-frankfurt.toml`
  2.  jars in remote server: 
    - `tmux new -s geofaas`
    - a second terminal? `ctrl-b %`
    - `cd geobroker`
    - `java -jar GeoBroker-Server.jar config/disgb-berlin.toml`
- Run Corresponding FaaS (if any?)
  - tinyFaas function deployment (localfile):
    - `./scripts/upload.sh "test/fns/sieve-of-eratosthenes" "sieve" "nodejs" 1`
    - `./scripts/upload.sh "test/fns/echo-js" "echo" "nodejs" 1`
- Run Corresponding GeoFaaS Edge (non-optional)
  - `java -jar GeoFaaSServer.jar Berlin 1 production false` params: the broker id, number of listening requests before exit, running mode, and debug mode
- Run Cloud
- Run Client (if any?)

## Development tools
- Drawing lat:long tool [ 1 (w/ radius on map)](https://www.freemaptools.com/radius-around-point.htm), [2](http://bboxfinder.com), and [3 (GeoJSON)](https://geojson.io/)
- [Hexagon Grid System](https://github.com/basonjui/hexagon-grid-system) to generate hexagonal areas
- [GeoJSON to WKT](https://geojson-to-wkt-converter.onrender.com)
- The broker areas in `<disgb-server-config>.json` are defined as first "long" then lat; Well-known text (WKT) format : `BUFFER (POINT (long lat), radius)`, that means reverse of common pair and also `Location.kt`
- It is suggested to use polygon geoBroker areas for geoBroker.  The circle areas sometimes worked not expected. It intersects with some clients as shouldn't (but not very far clients)
## Dependencies
- Kotlin 1.7.20  
- Maven  
- Ktor  
- JVM  
- [Go 1.20.6](https://go.dev/dl/go1.20.6.linux-arm64.tar.gz) (for tinyFaaS)    

## DisGB/GeoFaaS notes & configs 
A GeoFaaS node can use any FaaS solutions providing a list of supported functions, thus tinyFaaS can be replaced by any FaaS. That's why only DisGB (geoBroker) config mentioned below:  
- GeoFaaS Bridges will have a name starting with "GeoFaaS-" (e.g. "GeoFaaS-Berlin"). The geoBroker will skip the event geo-check for GeoFaaS Bridge
- The "Cloud" broker prefers passing clients to the responsible Edge broker, if any. But it is suggested that the cloud subscribes to the complement edge areas for the default behaviour
- GeoFaaS node locations set to middle of its service area  
- DiSGB mode set to `disgb_subscriberMatching`. i.e. RPs are near the subscribers    
- A `/result` from any GeoFaaS node is not forwarded to potential brokers, as the client is already subscribed to responsible (same) broker directly for the least latency
- A `/call` from any publisher is only forwarded to potential brokers if no local subscriber exists  
- GeoFaaS's subscription geofence = broker area by default  
- Client's subscription geofence = a circle around itself (with a `0.001` radius)  
- The GeoFaaS-Cloud's subscription geofence is the world, therefore it is responsible for requests outside all other service areas. Hence, its Location is 0.0:0.0
- The broker areas don't overlap, that means for a client there is only one responsible GeoFaaS node 
- GeFaaS-Cloud subscribes to both `/call` (for clients that are far from any edge) and `/nack` (for offloading), also `/call/retry/` to directly call the cloud when an edge GeoFaaS is running but not responding
- GeoFaaS-Edge subscribes only to `/call`s
- ClientGeoFaaS subscribes to `/result` and `/ack` around itself when calls a function
- timeout for the initial connection is 8 seconds. Client's listening timeout for the Ack after calling a function and its result is adjustable.  
- to make the client's location updated, should publish a PingReqPayload to geoBroker  
- the debug logs/comments I added can be found by searching for `>>>` and `//\\` 
- The client's call has a retry for the result before it fails by default and is adjustable
- There are default values `retries` (for the result), `getAckAttempts`, `ackTimeout`, and `resTimeout` for ClientGBClient that can be tuned
- The client's retry priority starts with receiving pub Acknowledgement, then receiving GeoFaaS's Ack, then GeoFaaS's Result, and if all failed, calling the cloud directly via "functions/f1/call/retry" 
- GeoFaaS Message contains a response 'topic' and 'fence' pair, declaring what topic and fence the publisher is listening to for response (an empty topic if not listening for any responses)  
- For now, GeoFaaS assumes the list of deployed functions on the FaaS doesn't change after initialization and is already deployed, however, it has the ability to update the functions and will offload to the cloud if the function is no longer served by the FaaS and no other same-node FaaS serving it
