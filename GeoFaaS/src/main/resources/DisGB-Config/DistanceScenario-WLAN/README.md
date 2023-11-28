# Scenario One: Distance/Latency 

## Setup
### Servers
- TUBerlin:
  - Location: `52.510057,13.325043`
  - Service Area: 
    - 20km diameter,
    - GeoJSON: 
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
  
- Potsdam:
  - Location: `52.354199, 13.055874`
- Service Area: 
  - 20km diameter,
- GeoJSON:
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


- multiple marks placeholder: 
```json
{
  "type": "FeatureCollection",
  "features": [

    
  ]
}
```

- Full GeoJson:
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
        "name": "TUB Broker"
      }
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
        "name": "Potsdam broker"
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
      }
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
    }
  ]
}
```