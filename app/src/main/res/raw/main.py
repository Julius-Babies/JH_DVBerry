import json
import requests

# Download VVO JSON
url = "https://www.vvo-online.de/open_data/VVO_STOPS.JSON"
response = requests.get(url)
data = response.json()

# Build GeoJSON feature list
features = []

def convert_to_float(value, stop):
    try:
        return float(value)
    except ValueError:
        print(f"[WARN] Failed to convert '{value}' to float.")
        print(f"[INFO] Stop details: gid={stop.get('gid')}, name={stop.get('name')}, place={stop.get('place')}")
        return 0.0

for stop in data:
    # Check if coordinates are not empty strings
    if stop.get("x") != "" or stop.get("y") != "":
        feature = {
            "type": "Feature",
            "properties": {
                "number": stop.get("gid"),
                "nameWithCity": f"{stop.get('place')} {stop.get('name')}",
                "name": stop.get("name"),
                "city": stop.get("place"),
                "tariffZone1": "",
                "tariffZone2": "",
                "tariffZone3": ""
            },
            "geometry": {
                "type": "Point",
                "coordinates": [
                    convert_to_float(stop.get("x"), stop),
                    convert_to_float(stop.get("y"), stop)
                ]
            }
        }
        features.append(feature)
    else:
        print(f"[INFO] Skipping stop with missing coordinates: gid={stop.get('gid')}")

# Create the full GeoJSON structure
geojson = {
    "type": "FeatureCollection",
    "features": features
}

# Save to stops.json
with open("stops.json", "w", encoding="utf-8") as f:
    json.dump(geojson, f, ensure_ascii=False, indent=2)

print(f"[DONE] Saved {len(features)} features to 'stops.json'.")
