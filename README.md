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
- [same for debian](https://docs.docker.com/engine/install/debian/)
- in the mentioned doc, you can install both latest or specific version of docker
- I needed to add current user to the docker group and logout/login. `sudo usermod -aG docker ${USER}`

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
scp ../src/main/resources/jfsb/disgb_jfsb.json raspi-beta:Documents/geobroker/config/disgb-registery.json 
```
Make a config file for geoBroker server `nvim geobroker/config/disgb-amsterdam.toml`
```toml
# server information
[server]
brokerId = "Amsterdam"
port = 5560
granularity = 5
messageProcessors = 2

    [server.mode]
    name = "disgb_subscriberMatching"
#    name = "disgb_publisherMatching"
    brokerAreaFilePath = "config/disgb-registry.json"
    brokerCommunicators = 2
```

## **Running**
- Run geoBroker servers in Distributed mode (frankfurt & paris sample):
  - in project source(frankfurt & paris sample): `java -jar GeoBroker-Server/out/GeoBroker-Server.jar GeoBroker-Server/src/main/resources/jfsb/disgbSM-frankfurt.toml`
  - jars in remote server: 
    - `tmux new -s geofaas`
    - two terminals? `ctrl-b %`
    - `cd geobroker`
    - ` java -jar GeoBroker-Server.jar config/disgb-amsterdam.toml`
- Run Corresponding FaaS (if any?)
  - tinyFaas function deployment (localfile):
    - `./scripts/upload.sh "test/fns/sieve-of-eratosthenes" "sieve" "nodejs" 1`
    - `./scripts/upload.sh "test/fns/echo-js" "echo" "nodejs" 1`
- Run Corresponding GeoFaaS Edge (non-optional)
  - `java -jar GeoFaaSEdge.jar Amsterdam 1` the broker id and number of listening epochs
- Run Cloud
- Run Client (if any?)

### Development tools
- [Draw lat:long + radius on map](https://www.freemaptools.com/radius-around-point.htm)
- for some strange reasons the broker areas in `<disgb-server-config>.json` are defined as first "long" then lat; Well-known text (WKT) format : `BUFFER (POINT (long lat), radius)`, that means reverse of common pair and also `Location.kt`

## Dependencies
- Kotlin 1.7.20  
- Maven  
- Ktor  
- JVM  
- [Go 1.20.6](https://go.dev/dl/go1.20.6.linux-arm64.tar.gz) (for tinyFaaS)    

## **Hardcoded DisGB config (GeoFaaS requirements)**
GeoFaaS is independent of the FaaS module. tinyFaaS could be replaced by any FaaS. That's why only DisGB (geoBroker) config mentioned below:  
- DiSGB mode: `disgb_subscriberMatching` RPs are near the subscribers

