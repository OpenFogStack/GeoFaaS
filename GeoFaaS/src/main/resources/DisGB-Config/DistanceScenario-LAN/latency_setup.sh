#!/usr/bin/bash

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