# GeoFaaS

Yet another edge-to-cloud FaaS platform. A decenteralized solution that routes client requests using their location to the closest FaaS server on the edge.  
Under the hood it leverages the Distributed [Geobroker](https://github.com/MoeweX/geobroker) (DisGB) and lightweight FaaS systems (e.g. [tinyFaaS](https://github.com/OpenFogStack/tinyFaaS)). 


## Remote server setup
An editor instead of `vi`? Helix or NeoVim! , and `tmux` for remote administrations. Plus `bat`, a better `cat`!
```
sudo apt update && sudo apt install -y neovim tmux bat htop
```
java and other dependencies 
```
sudo apt install -y default-jdk libavutil-dev git-core make zip
```

### Golang 1.20.6
The paper's version
```
mkdir ~/src && cd ~/src
wget https://go.dev/dl/go1.20.6.linux-arm64.tar.gz #for raspberries
wget https://go.dev/dl/go1.20.6.linux-amd64.tar.gz #for amd based (cloud)
sudo tar -C /usr/local -xzf go1.20.6.linux-arm64.tar.gz 
rm go1.20.6.linux-arm64.tar.gz && cd .. && rmdir src/
hx ~/.profile # on helix edior, alternatively vim or nano, etc.
```
(optional) add these two below lines and save, then `source ~.profile` from terminal: 
```
   PATH=$PATH:/usr/local/go/bin  
   GOPATH=$HOME/go
   alias bat="batcat"
```
### Docker 
- [Official recipe for raspberry pi](https://docs.docker.com/engine/install/raspberry-pi-os/#install-using-the-repository)
- [For Debian](https://docs.docker.com/engine/install/debian/). you can install both the latest or a specific version of docker
- I needed to add the current user to the docker group and logout/login. `sudo groupadd docker` and `sudo usermod -aG docker ${USER}`. For more, check the [post-install steps](https://docs.docker.com/engine/install/linux-postinstall/)

### Deployment setup  (To be updated)
```
cd Documents/ # optional path
git clone https://github.com/OpenFogStack/tinyFaaS.git # get tinyfaas
 
cd tinyFaaS && make
# upload some functions:
cd tinyFaaS && ./scripts/upload.sh "test/fns/sieve-of-eratosthenes" "sieve" "nodejs" 1 && cd .. 
mkdir geobroker && mkdir geobroker/config

# Setup geobroker server Jar + configs from your local
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
- each messageProcessor or brokerCommunicators is a ZMQ socket, the former as `SocketType.DEALER` and the latter as `SocketType.PUSH`


## Running
- Run geoBroker instances in Distributed mode (frankfurt & paris sample):
  1. in project source(frankfurt & paris sample): `java -jar GeoBroker-Server/out/GeoBroker-Server.jar GeoBroker-Server/src/main/resources/jfsb/disgbSM-frankfurt.toml`
  2.  jars in remote server: 
    - `tmux new -s geofaas`
    - a second terminal? `ctrl-b %`
    - `cd geobroker`
    - `java -jar GeoBroker-Server.jar config/disgb-berlin.toml`
- Run Corresponding FaaS instances (if any?)
  - tinyFaas function deployment (using source):
    - `./scripts/upload.sh "test/fns/sieve-of-eratosthenes" "sieve" "nodejs" 1`
    - `./scripts/upload.sh "test/fns/echo-js" "echo" "nodejs" 1`
- Run Corresponding GeoFaaS Edge (required)
  - `java -jar GeoFaaSServer.jar Berlin 1 production false` params: the broker id, number of listening requests before shutdown, running mode, and debug mode
  - you can add `-Dlog4j.configurationFile=log4j2.xml` flag for custom log4j2 config
- Run Cloud
- Start Clients and enjoy!

## Development tools
- Drawing lat:long tool [ 1](https://www.freemaptools.com/radius-around-point.htm)(w/ radius on map), [2](http://bboxfinder.com), and [3 (GeoJSON)](https://geojson.io/)
- [Hexagon Grid System](https://github.com/basonjui/hexagon-grid-system) to generate hexagonal areas
- [GeoJSON to WKT](https://geojson-to-wkt-converter.onrender.com)
- The broker areas in `<disgb-server-config>.json` are defined as first "long" then lat; Well-known text (WKT) format : `BUFFER (POINT (long lat), radius)`, that means reverse of common pair and also `Location.kt`
- Consider using polygon geoBroker areas instead of circular areas. Circular areas may behave unexpectedly and have overlaps. In some cases, they might intersect with clients where they shouldn’t, especially for clients that are not very far away.
## Dependencies
- Kotlin 1.9.23  
- Maven  
- Ktor  
- JVM  
- [Go 1.20.6](https://go.dev/dl/go1.20.6.linux-arm64.tar.gz) (for tinyFaaS)    

## DisGB/GeoFaaS notes & configs 
A GeoFaaS node can use any FaaS solutions providing a list of supported functions, thus tinyFaaS can be replaced by any FaaS. That's why only DisGB (geoBroker) config mentioned below:  
- GeoFaaS Bridges will have a name starting with "GeoFaaS-" (e.g. "GeoFaaS-Berlin"). The geoBroker will skip the event geo-check for GeoFaaS Bridge
- The "Cloud" broker prefers passing clients to the responsible Edge broker, if any. But it is suggested that the cloud broker has the complement edge areas for the default behaviour
- GeoFaaS node locations set to middle of its service area  
- DiSGB mode set to `disgb_subscriberMatching`. i.e. RPs are near the subscribers    
- A `/result` from any GeoFaaS node is not forwarded to potential brokers, as the client is already subscribed to responsible (same) broker directly for the least latency
- Client's subscription geofence = a circle around itself (with a `0.001` radius)  
- The broker areas don't overlap, that means for a client there is only one responsible GeoFaaS node 
- GeFaaS-Cloud subscribes to both `/call` (for clients that are far from any edge) and `/nack` (for offloading), also `/call/retry/` for client direct cloud calls when an edge GeoFaaS is running but not responding
- GeoFaaS-Edge subscribes only to `/call`s
- For overhead minimization, A `/call` from any publisher is only forwarded to brokers with potential subscribers if no local subscriber exists. But the logic is correct if the cloud only subscribes to `/call` with edge complement geofence.
- GeoFaaS's subscription geofence = broker area by default. The GeoFaaS-Cloud subscribes to world for the additional topics it subscribes to.
- ClientGeoFaaS subscribes to `/result` and `/ack` around itself when calls a function
- Timeout for the initial connection is 8 seconds. Client's listening timeout for the Ack after calling a function and its result is adjustable.
- The debug logs/comments I added can be found by searching for `>>>` and `//\\` 
- By default, the client's call has a retry for the result before it fails and is adjustable
- There are default values `retries` (for the result), `getAckAttempts`, `ackTimeout`, and `resTimeout` for ClientGBClient that can be tuned
- The client's retry priority starts with receiving pub Acknowledgement, then receiving GeoFaaS's Ack, then GeoFaaS's Result, and if all failed, calling the cloud directly via "functions/f1/call/retry" 
- GeoFaaS Message contains a response 'topic' and 'fence' pair, declaring what topic and fence the publisher is listening to for response (an empty topic if not listening for any responses)  
- For now, GeoFaaS assumes the list of deployed functions on the FaaS doesn't change after initialization, however, it has the ability to update the functions and will offload to the cloud if the function is no longer served by the FaaS and no other same-node FaaS serving it

## Acknowledgement
You can use any content and info from this project, but please mention the project. If you are writing a paper, please cite the GeoFaaS paper as a reference.  