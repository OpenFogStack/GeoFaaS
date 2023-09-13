# GeoFaaS

A middleware between [Geobroker](https://github.com/MoeweX/geobroker) (or DisGB) and a FaaS system (e.g. [tinyFaaS](https://github.com/OpenFogStack/tinyFaaS)). Routing client requests using their location to the proper FaaS server.


## **Running**
- Run geoBroker servers in Distributed mode (frankfurt & paris sample): 
  - Server Frankfurt: `java -jar GeoBroker-Server/out/GeoBroker-Server.jar GeoBroker-Server/src/main/resources/jfsb/disgbSM-frankfurt.toml`
  - Server Paris: `java -jar GeoBroker-Server/out/GeoBroker-Server.jar GeoBroker-Server/src/main/resources/jfsb/disgbSM-paris.toml`
- Run Corresponding FaaS (if any?) 
  - tinyFaas function deployment (localfile): `./scripts/upload.sh "test/fns/sieve-of-eratosthenes" "sieve" "nodejs" 1`
- Run Corresponding GeoFaaS Edge (non-optional)
- Run Cloud
- Run Client (if any?)

## Remote server setup
### An editor instead of `vi`? Helix or NeoVim! , and `tmux` for remote administrations
```
sudo apt update
sudo apt install -y neovim libavutil-dev tmux
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
```
### Docker 
- [official recipe for raspberry pi](https://docs.docker.com/engine/install/raspberry-pi-os/#install-using-the-repository)
- [same for debian](https://docs.docker.com/engine/install/debian/)
- in the mentioned doc, you can install both latest or specific version of docker
- I needed to add current user to the docker group and logout/login. `sudo usermod -aG docker ${USER}`

### Setup codes
```
git clone https://github.com/OpenFogStack/tinyFaaS.git # get tinyfaas
cd tinyFaas & make
# optionally upload some functions
git clone https://github.com/ChaosRez/geoFaaS.git # to be completed
```

### Development tools
- [Draw lat:long + radius on map](https://www.freemaptools.com/radius-around-point.htm)
- for some strange reasons the broker areas in `<disgb-server-config>.json` are defined as first "long" then lat: `BUFFER (POINT (long lat), radius)`, that means reverse of common pair and also `Location.kt`

## Dependencies
- Kotlin 1.7.20  
- Maven  
- Ktor  
- JVM  
- [Go 1.20.6](https://go.dev/dl/go1.20.6.linux-arm64.tar.gz) (for tinyFaaS)    
