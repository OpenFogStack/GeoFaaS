#!/usr/bin/bash

# @ From the GeoFaaS project https://github.com/ChaosRez/GeoFaaS/

TC=/sbin/tc

IF=$1 # wlan0, eth0
DST_IP=$2 # destination ip address
LATENCY="${3}"000 # additional latency (in ms)

# filter command (check man tc, filters section)
U32="$TC filter add dev $IF protocol ip parent 1: u32"

create() {
  echo ".:Started traffic control:."

  # make a root with priority queue
  $TC qdisc add dev "$IF" root handle 1: prio
  echo ".:Added root node:."

  # filter traffic to be matched to the above class
  $TC filter add dev "$IF" protocol ip parent 1: prio 1 u32 match ip dst "$DST_IP" flowid 1:1
  $TC filter add dev "$IF" protocol all parent 1: prio 2 u32 match ip dst 0.0.0.0/0 flowid 1:3
  echo ".:added rules:."

  # add delayed queues
  $TC qdisc add dev "$IF" parent 1:1 handle 10: netem delay "$LATENCY" # inter-edgeBrokers
  $TC qdisc add dev "$IF" parent 1:3 handle 30: pfifo_fast # rest connections with the default queuing
  echo ".:added queues:."



  echo ".:$LATENCY ns added to destination address $DST_IP:."
}

clean() {
  $TC qdisc del dev "$IF" root
  echo ".:tc rules set to default for interface $IF:."
}

clean
create

# Copyright (c) 2024 Reza Malek (github.com/ChaosRez)
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:

# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.

# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.