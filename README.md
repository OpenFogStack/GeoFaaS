# GeoFaaS

A middleware between [Geobroker](https://github.com/MoeweX/geobroker) (or DisGB) and a FaaS system (e.g. [tinyFaaS](https://github.com/OpenFogStack/tinyFaaS)). Routing client requests using their location to the proper FaaS server.

## Remote server setup
### An editor instead of `vi`? Helix or NeoVim!
```
sudo apt update
sudo apt install -y neovim
```
### `tmux` for remote administrations
```
sudo apt install libavutil-dev tmux
```

### Golang 1.20.6
```
mkdir ~/src && cd ~/src
wget https://go.dev/dl/go1.20.6.linux-arm64.tar.gz #for raspberries
sudo tar -C /usr/local -xzf go1.20.6.linux-arm64.tar.gz 
rm go1.20.6.linux-arm64.tar.gz
hx ~/.profile # for helix edior, alternatively vim or nano, etc.
# add these two in seperate lines: 'PATH=$PATH:/usr/local/go/bin' and 'GOPATH=$HOME/go'
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
## Dependencies
Kotlin 1.7.0  
Maven
Ktor
[Go 1.20.6](https://go.dev/dl/go1.20.6.linux-arm64.tar.gz) (for tinyFaaS)  
