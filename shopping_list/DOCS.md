# Shopping list

Shopping lists shared by everyone in the household. Open it from the sidebar.

## Users

Home Assistant logs you in; the add-on has no accounts of its own. Every Home
Assistant user sees and changes every list, and the add-on records who created
each list and item.

## Data and backups

Lists are kept in one SQLite file, `/data/shoppinglist.db` inside the
add-on. Home Assistant includes it in its backups. The add-on is stopped for a
few seconds while a backup is taken, so the copy is consistent.

## Files and images

Items can have files and images, up to 15 MB each (Home Assistant's ingress
does not take larger uploads). They are kept in `/data/files`, next to the
database, so they are part of every backup. Large photos make backups
larger.

Deleting a file removes it straight away. Files on deleted items and lists
are removed the next time the add-on starts.

## Access

The add-on is only reachable through Home Assistant (ingress). It publishes no
ports, and only accepts users from Home Assistant's own proxy.
