# Nearby Police Incidents

TheBrief now includes a battery-conscious public police-incident feature based on the official Norwegian Police **Politiloggen API**.

## Default behavior
- Municipality: Ringerike (editable in Settings)
- All public Politiloggen categories enabled by default
- English category names on the dashboard
- Norwegian incident text is translated online to English; the original Norwegian is retained in the detail dialog
- Dashboard card shows recent public incidents and links to Politiet's Politiloggen page
- A background WorkManager check runs about every 2 hours when network is available. Android may defer it under Doze/battery restrictions.
- The first background run establishes a baseline so installing/updating the app does not generate a flood of notifications for old incidents.

## Categories
Events, Fire, Animals, Burglary, Rescue, Public order, Missing person, Maritime incident, Vandalism / property damage, Traffic, Theft, Accident, Violence, Weather, Other incidents.

## Important limitation
Politiloggen is a **public operational log**, not a live feed of every 112 call. Police may omit, delay, generalize, or update incidents for operational, privacy, or security reasons.

## Official source and attribution
The data is from Politiet / Politiloggen and is licensed under the Norwegian Licence for Open Government Data (NLOD) 2.0. The app includes visible attribution and a link to Politiloggen as required by the official usage guidance.

Official API: https://api.politiloggen.politiet.no/
Official public log: https://www.politiet.no/politiloggen
