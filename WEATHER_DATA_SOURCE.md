# Weather data

TheBrief now uses **MET Norway Locationforecast 2.0** as the primary weather forecast instead of OpenWeather for the dashboard forecast.

- The API provides forecasts up to nine days; TheBrief shows **7 days** when the Tomorrow card is opened.
- The app geocodes the selected city/municipality to coordinates and requests MET's point forecast.
- A 10-minute app-side cache prevents repeated taps/refreshes from jumping between forecast responses every few minutes.
- The UI shows the MET forecast update age.
- OpenWeather remains available for the existing severe-alert/custom-alert compatibility paths.

MET Norway requires a descriptive User-Agent. The client sends one and rounds coordinates to four decimals for effective caching, following MET's guidance.

A second independent forecast (Open-Meteo) is used only as a cross-check for tomorrow's high/low. MET Norway remains the displayed primary forecast. The dashboard reports high/moderate agreement or a disagreement warning instead of replacing the primary forecast with a random provider.
