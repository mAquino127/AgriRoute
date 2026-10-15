# AgriRoute

AgriRoute is an android mobile application that compares alternative delivery routes and flags potential route risk using live traffic, travel-time estimates, and hourly precipitation forecasts.

## Main features

- **TomTom Routing API** calculates road routes, geometry, distance, traffic-aware travel time, and up to three alternatives in one request.
- **TomTom Traffic API – Flow Segment Data** checks current speed versus free-flow speed near each alternative's midpoint. The full route's traffic sections are also read from the Routing response. If a Flow request fails, route comparison still works with the traffic data included in the Routing response.
- **Open-Meteo** supplies hourly precipitation forecasts. Heavy rainfall is checked against the route's locally configured flood/landslide-prone points.
- **Route risk labels** show On time, Risky, or Can't tell. Closures, severe traffic slowdowns, heavy rain on a hazard-prone section, a tight deadline, or missing/conflicting data influence the result. Select any alternative to inspect its ETA, distance, risk reason, and mapped hazards, then choose or switch routes.
- **Route Alerts** can re-check scheduled/on-road deliveries and save newly detected hazards or risk-status changes locally.

## Route map condition markers

When a TomTom key is configured, each route alternative is sampled at several points using the TomTom Traffic Flow API. Traffic observations use a traffic-light icon and Open-Meteo weather observations use a cloud-and-sun icon. Marker background colors communicate condition severity (green = normal/low, amber = caution, red = high risk). Tap a marker to see its source, approximate route distance, and observed/forecast time. Select a different route tab to see that route's own samples.

The route header shows the truck model selected for the delivery (for example, Hino 500 or Isuzu Forward) instead of the delivery ID. In Updates, hourly API condition observations are recorded once per hour along with risk changes. Traffic markers require your TomTom key to have Traffic Flow API access; Open-Meteo does not need an API key. These are route-point samples, not a complete street-by-street weather radar or incident map.
