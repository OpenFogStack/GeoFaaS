# Scenario One: Distance/Latency Change

Shows that the geo-distribution is transparent to moving clients by showing the latency footprint of handovers and client's distance to cloud/edge.

![Alt text](client-and-serviceares.png)
## Setup

### traffic control
we used linux's traffic control `tc` to add 5ms network latency (10ms round trip) for inter-Edge connection  
- run the `latency_setup.sh <net-interface> <other-broker-ip> <additional latency in ms>` 
- allow non-root user to run tc commands: `setcap cap_net_admin+ep /usr/sbin/tc`

### Client
- a moving client from Pankow region in Berlin to far west of Potsdam
- the client calls the sieve function once per location
### Servers
- We set a 5ms inter-edge artificial network latency (each trip)
- you can copy measurement files to local using scp: `scp "raspi-gamma:~/Documents/logs/*.csv" .`
 <details>
     <summary>multiple marks placeholder:</summary>  

  ```json
  {
    "type": "FeatureCollection",
    "features": [
  
      
    ]
  }
  ```
  </details>

  <details>
      <summary>Full GeoJson:</summary>

  ```json
  {
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "geometry": {
        "type": "Polygon",
        "coordinates": [
          [
            [
              13.235211471588048,
              52.354464419905504
            ],
            [
              13.414874528411953,
              52.354464419905504
            ],
            [
              13.504706056823904,
              52.510057
            ],
            [
              13.414874528411953,
              52.6656495800945
            ],
            [
              13.235211471588048,
              52.6656495800945
            ],
            [
              13.145379943176097,
              52.510057
            ],
            [
              13.235211471588048,
              52.354464419905504
            ]
          ]
        ]
      },
      "properties": {
        "ccid": {
          "q": 0,
          "r": 0,
          "s": 0
        },
        "centroid": {
          "longitude": 13.325043,
          "latitude": 52.510057
        },
        "circumradius": 20000,
        "inradius": 17320.508075688773,
        "name": "TUB Node"
      },
      "id": 0
    },
    {
      "type": "Feature",
      "geometry": {
        "type": "Polygon",
        "coordinates": [
          [
            [
              12.966042471588047,
              52.1986064199055
            ],
            [
              13.145705528411952,
              52.1986064199055
            ],
            [
              13.235537056823903,
              52.354199
            ],
            [
              13.145705528411952,
              52.5097915800945
            ],
            [
              12.966042471588047,
              52.5097915800945
            ],
            [
              12.876210943176096,
              52.354199
            ],
            [
              12.966042471588047,
              52.1986064199055
            ]
          ]
        ]
      },
      "properties": {
        "ccid": "[object Object]",
        "centroid": {
          "longitude": 13.055874,
          "latitude": 52.354199
        },
        "circumradius": 20000,
        "inradius": 17320.508075688773,
        "name": "Potsdam Node"
      }
    },
    {
      "type": "Feature",
      "properties": {
        "name": "Pankow"
      },
      "geometry": {
        "coordinates": [
          13.402634,
          52.565708
        ],
        "type": "Point"
      }
    },
    {
      "type": "Feature",
      "properties": {
        "name": "Wedding"
      },
      "geometry": {
        "coordinates": [
          13.342552,
          52.54901
        ],
        "type": "Point"
      },
      "id": 1
    },
    {
      "type": "Feature",
      "properties": {
        "name": "Charlottenburg Palace"
      },
      "geometry": {
        "coordinates": [
          13.294315,
          52.520713
        ],
        "type": "Point"
      }
    },
    {
      "type": "Feature",
      "properties": {
        "name": "Grunewald"
      },
      "geometry": {
        "coordinates": [
          13.254318,
          52.488634
        ],
        "type": "Point"
      }
    },
    {
      "type": "Feature",
      "properties": {
        "name": "Steglitz"
      },
      "geometry": {
        "coordinates": [
          13.316116,
          52.452871
        ],
        "type": "Point"
      }
    },
    {
      "type": "Feature",
      "properties": {
        "name": "Zehlendorf"
      },
      "geometry": {
        "coordinates": [
          13.260326,
          52.435607
        ],
        "type": "Point"
      }
    },
    {
      "type": "Feature",
      "properties": {
        "name": "Kleinmachnow"
      },
      "geometry": {
        "coordinates": [
          13.20694,
          52.414566
        ],
        "type": "Point"
      },
      "id": 8
    },
    {
      "type": "Feature",
      "properties": {
        "name": "Duppeler Forst"
      },
      "geometry": {
        "coordinates": [
          13.157158,
          52.40179
        ],
        "type": "Point"
      }
    },
    {
      "type": "Feature",
      "properties": {
        "name": "Babelsberg"
      },
      "geometry": {
        "coordinates": [
          13.09227,
          52.403885
        ],
        "type": "Point"
      }
    },
    {
      "type": "Feature",
      "properties": {
        "name": "Bornim"
      },
      "geometry": {
        "coordinates": [
          13.016739,
          52.423779
        ],
        "type": "Point"
      }
    },
    {
      "type": "Feature",
      "properties": {
        "name": "Fahrland"
      },
      "geometry": {
        "coordinates": [
          13.002663,
          52.469188
        ],
        "type": "Point"
      }
    },
    {
      "type": "Feature",
      "properties": {
        "name": "Fahrland"
      },
      "geometry": {
        "coordinates": [
          13.002663,
          52.469188
        ],
        "type": "Point"
      }
    },
    {
      "type": "Feature",
      "properties": {
        "name": "Ketzin"
      },
      "geometry": {
        "coordinates": [
          12.883873,
          52.479017
        ],
        "type": "Point"
      }
    },
    {
      "type": "Feature",
      "properties": {
        "name": "Havel"
      },
      "geometry": {
        "coordinates": [
          12.811737,
          52.477408
        ],
        "type": "Point"
      }
    },
    {
      "type": "Feature",
      "properties": {
        "name": "Havel2"
      },
      "geometry": {
        "coordinates": [
          12.740869,
          52.462820
        ],
        "type": "Point"
      }
    },
    {
      "type": "Feature",
      "properties": {
        "ccid": "",
        "name": "Havel3"
      },
      "geometry": {
        "coordinates": [
          12.691420,
          52.448292
        ],
        "type": "Point"
      }
    }
  ]
}
  ```
  </details>

- Cloud
  - Location: `51.498593,-0.176959` (London)
    - cloud node in London (24ms ping). set the location to Imperial College London
  - Service Area:
    - Whole world
  - 10 threads for sieve function
- TUBerlin
  - 2 threads for sieve function
  - Location: `52.510057,13.325043`
  - Service Area: 
    - 20km diameter,
    <details>
    <summary>GeoJSON:</summary> 
    
    ```JSON 
    {
      "type": "Feature",
      "geometry": {
        "type": "Polygon",
        "coordinates": [
          [
            [
              13.235211471588048,
              52.354464419905504
            ],
            [
              13.414874528411953,
              52.354464419905504
            ],
            [
              13.504706056823904,
              52.510057
            ],
            [
              13.414874528411953,
              52.6656495800945
            ],
            [
              13.235211471588048,
              52.6656495800945
            ],
            [
              13.145379943176097,
              52.510057
            ],
            [
              13.235211471588048,
              52.354464419905504
            ]
          ]
        ]
      },
      "properties": {
        "ccid": {
          "q": 0,
          "r": 0,
          "s": 0
        },
        "centroid": {
          "longitude": 13.325043,
          "latitude": 52.510057
        },
        "circumradius": 20000,
        "inradius": 17320.508075688773
      }
    }
    ```
    </details>
  
- Potsdam
  - 2 threads for sieve function
  - Location: `52.354199, 13.055874`
- Service Area: 
  - 20km diameter,
    <details>
      <summary>GeoJSON:</summary> 
    
    ```JSON
            {
            "type": "Feature",
            "geometry": {
                "type": "Polygon",
                "coordinates": [
                    [
                        [
                            12.966042471588047,
                            52.1986064199055
                        ],
                        [
                            13.145705528411952,
                            52.1986064199055
                        ],
                        [
                            13.235537056823903,
                            52.354199
                        ],
                        [
                            13.145705528411952,
                            52.5097915800945
                        ],
                        [
                            12.966042471588047,
                            52.5097915800945
                        ],
                        [
                            12.876210943176096,
                            52.354199
                        ],
                        [
                            12.966042471588047,
                            52.1986064199055
                        ]
                    ]
                ]
            },
            "properties": {
                  "ccid": {
                      "q": 0,
                      "r": 0,
                      "s": 0
                  },
                  "centroid": {
                      "longitude": 13.055874,
                      "latitude": 52.354199
                  },
                  "circumradius": 20000.0,
                  "inradius": 17320.508075688773
              }
          }
      ```
  </details>

##