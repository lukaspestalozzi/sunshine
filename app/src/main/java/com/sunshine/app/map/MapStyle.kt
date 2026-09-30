package com.sunshine.app.map

/**
 * Where offline regions find the map style (design D3 of add-offline-regions). MapLibre's offline
 * download cannot read a style from the app's assets, so [SunshineHttpRequest] answers this URL
 * locally with [OPEN_TOPO_MAP_STYLE]. The `.invalid` domain is reserved and never resolves.
 */
const val REGION_STYLE_URL = "https://sunshine.invalid/opentopomap-style.json"

// Missing tiles leave the background visible: blank, never substitute imagery.
const val OPEN_TOPO_MAP_STYLE = """
{
  "version": 8,
  "sources": {
    "opentopomap": {
      "type": "raster",
      "tiles": ["https://tile.opentopomap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "maxzoom": 17
    }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#E0E0E0" } },
    { "id": "opentopomap", "type": "raster", "source": "opentopomap" }
  ]
}
"""
