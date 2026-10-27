# Shopping list

Shopping lists shared by everyone in the household. Open it from the sidebar.

## Users

Home Assistant logs you in; the add-on has no accounts of its own. Every Home
Assistant user sees and changes every list, and the add-on records who created
each list and item.

## Data and backups

Everything is kept in one SQLite file, `/data/shoppinglist.db` inside the
add-on. Home Assistant includes it in its backups. The add-on is stopped for a
few seconds while a backup is taken, so the copy is consistent.

## Access

The add-on is only reachable through Home Assistant (ingress). It publishes no
ports, and only accepts users from Home Assistant's own proxy.
