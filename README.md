# GeoFaaS

A middleware between [Geobroker](https://github.com/MoeweX/geobroker) (or DisGB) and a FaaS system (e.g. [tinyFaaS](https://github.com/OpenFogStack/tinyFaaS)). Routing client requests using their location to the proper FaaS server.


## Remote server setup
### An editor instead of `vi`? Helix or NeoVim! , and `tmux` for remote administrations. Plus `bat`, a better `cat`!
```
sudo apt update
sudo apt install -y neovim libavutil-dev tmux bat
```
### `java`
```
sudo apt install default-jdk
```

### Golang 1.20.6
```
mkdir ~/src && cd ~/src
wget https://go.dev/dl/go1.20.6.linux-arm64.tar.gz #for raspberries
sudo tar -C /usr/local -xzf go1.20.6.linux-arm64.tar.gz 
rm go1.20.6.linux-arm64.tar.gz
hx ~/.profile # for helix edior, alternatively vim or nano, etc.
# add these two below lines and save, then 'source ~.profile' from terminal: 
  # PATH=$PATH:/usr/local/go/bin   and 
  # GOPATH=$HOME/go
  # alias bat="batcat"
```
### Docker 
- [official recipe for raspberry pi](https://docs.docker.com/engine/install/raspberry-pi-os/#install-using-the-repository)
- [same for debian](https://docs.docker.com/engine/install/debian/). you can install both latest or specific version of docker
- I needed to add current user to the docker group and logout/login. `sudo usermod -aG docker ${USER}`. Check the [post install steps](https://docs.docker.com/engine/install/linux-postinstall/)

### Setup codes (To be updated)
```
cd Documents/ # optional path
git clone https://github.com/OpenFogStack/tinyFaaS.git # get tinyfaas
 
tmux # run below line in tmux and detach
cd tinyFaaS && make
# Optionally upload some functions:
cd tinyFaaS && ./scripts/upload.sh "test/fns/sieve-of-eratosthenes" "sieve" "nodejs" 1 && cd .. 
mkdir geobroker && mkdir geobroker/config

# FIXME. upload geobroker server Jar from your pc
scp GeoBroker-Server.jar raspi-alpha:~/Documents/geobroker
scp ../src/main/resources/jfsb/disgb_jfsb.json raspi-beta:Documents/geobroker/config/disgb-registry.json 
```
Make a config file for geoBroker server `nvim geobroker/config/disgb-berlin.toml`
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
- The geoBroker's client names matter, as it will skip event geo-check for "GeoFaaS-" servers, and skip subscription checking 
- 'granularity' is the accuracy for location queries. the bigger, the smaller tiles of world map, therefore more accuracy.
- 'messageProcessors' is the number of ZMQ message processors (check DisGBSubscriberMatchingServerLogic.java)
- 'brokerCommunicators' is the number of ZMQ message communicators; is also a parameter used in each message processor and broker communicator (ZMQProcessStarter.java)


## **Running**
- Run geoBroker servers in Distributed mode (frankfurt & paris sample):
  1. in project source(frankfurt & paris sample): `java -jar GeoBroker-Server/out/GeoBroker-Server.jar GeoBroker-Server/src/main/resources/jfsb/disgbSM-frankfurt.toml`
  2.  jars in remote server: 
    - `tmux new -s geofaas`
    - two terminals? `ctrl-b %`
    - `cd geobroker`
    - `java -jar GeoBroker-Server.jar config/disgb-berlin.toml`
- Run Corresponding FaaS (if any?)
  - tinyFaas function deployment (localfile):
    - `./scripts/upload.sh "test/fns/sieve-of-eratosthenes" "sieve" "nodejs" 1`
    - `./scripts/upload.sh "test/fns/echo-js" "echo" "nodejs" 1`
- Run Corresponding GeoFaaS Edge (non-optional)
  - `java -jar GeoFaaSEdge.jar Berlin 1 production` the broker id, number of listening epochs, and running mode
- Run Cloud
- Run Client (if any?)

### Development tools
- [Draw lat:long + radius on map](https://www.freemaptools.com/radius-around-point.htm)
- for some strange reasons the broker areas in `<disgb-server-config>.json` are defined as first "long" then lat; Well-known text (WKT) format : `BUFFER (POINT (long lat), radius)`, that means reverse of common pair and also `Location.kt`
- geoBroker areas work unexpected. they seem to be bigger than the default radius 2.1 (KM?). geoBroker areas intersect with its clients far away (but not very far)

## Dependencies
- Kotlin 1.7.20  
- Maven  
- Ktor  
- JVM  
- [Go 1.20.6](https://go.dev/dl/go1.20.6.linux-arm64.tar.gz) (for tinyFaaS)    

## **Hardcoded DisGB config & assumptions (GeoFaaS requirements)**
GeoFaaS is independent of the FaaS module. tinyFaaS could be replaced by any FaaS. That's why only DisGB (geoBroker) config mentioned below:  
- DiSGB mode: `disgb_subscriberMatching`. i.e. RPs are near the subscribers    
- GeoFaaS's subscription geofence = broker area  
- Client's subscription geofence = a circle around itself (with a `0.1` radius)  
- The `/result` from any GeoFaaS server is not forwarded to potential brokers, as the client is already subscribed to responsible (same) broker
- The `/call` from any entity is only forwarded to potential brokers if no local subscriber exists  
- The GeoFaaS-Cloud's subscription geofence is the world, therefore it is responsible for requests. Hence, its Location is 0.0:0.0
- The broker areas don't overlap, that means for a client there is only one responsible GeoFaaS server 
- GeFaaS-Cloud subscribes to both `/call` (for clients that are far from any Edge server) and `/nack` (for offloading)
- GeoFaaS-Edge subscribes only to `/call`s
- ClientGeoFaaS subscribes to `/result` and `/ack` around itself when calls a function
- GeoFaaS servers (Edge/Cloud) have a name starting with "GeoFaaS-", and when publishing, they fake their location to client location (middle of the publish fence)  
- message TypeCode (NORMAL/PIGGY) is for further uses
- GeoFaaS Message contains a Topic and Fence pair, declaring what topic and fence is the publisher is listening for response (empty topic if not listening for any response)  
- timeout for initial connection is 8 seconds. Client's listening timeout for the Ack after calling a function is 8.5 seconds.  
- in geoBroker to make the client's location updated, should publish a PingReqPayload  
- GeoFaaS may processes and enqueues all the geographically (and topic) relevant messages. later can use the receiver id to process/dismiss  
- the debug logs/comments I added can be found by searching for `>>>` and `//\\`  
