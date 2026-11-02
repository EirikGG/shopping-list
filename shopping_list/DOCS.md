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

## Home Assistant entities (MQTT)

If Home Assistant has an MQTT broker, such as the Mosquitto broker add-on
with the MQTT integration set up, the add-on finds it by itself. Every list
then shows up as a device under Settings > Devices & services > MQTT, with:

- **Open items** (sensor): how many items are left. Its attributes have the
  total, and the open items with their amount and labels.
- **Changes** (event): fires on `item_added`, `item_completed`,
  `item_reopened`, `item_changed` (name, amount or labels) and
  `item_deleted`. The attributes have the item, its amount and labels, and who
  made the change: `by` is their name, and `by_user_id` their Home Assistant
  user, the same as `user_id` on their person entity.
- **Add item** (text): what you type in it is added to the list.
- **Items** (sensor): the open items as one line, newest first, such as
  `2 × Melk, Brød, Egg`, or `Tom` when there are none. Items that do not fit
  in a state (255 characters) are counted at the end, as in `+3 til`.

Without a broker the add-on works as before; the log says so when it starts.

### Automations on changes

Trigger on the Changes entity, and check the event type. `not_from` keeps it
from firing again with the last event when the add-on restarts:

```yaml
triggers:
  - trigger: state
    entity_id: event.groceries_changes
    not_from:
      - unavailable
      - unknown
conditions:
  - condition: state
    entity_id: event.groceries_changes
    attribute: event_type
    state: item_added
actions:
  - action: notify.mobile_app_phone
    data:
      message: >
        {{ trigger.to_state.attributes.by }} added
        {{ trigger.to_state.attributes.item }}
```

The same events are also published to `shopping_list/list/<id>/event`, for an
MQTT trigger. `<id>` is the list's `list_id` attribute on its Open items
sensor.

### Changing a list from an automation

Publish JSON to the list's command topic, which is the `command_topic`
attribute of its Open items sensor:

```yaml
actions:
  - action: mqtt.publish
    data:
      topic: shopping_list/list/1/command
      payload: '{"action": "add", "name": "Milk", "amount": 2, "labels": ["Rema"]}'
```

The actions are `add`, `complete`, `reopen` and `delete`. `amount` and
`labels` are optional, and only for `add`; labels the list does not have are
left out. `stores`, from before labels were called that, works too. The
other actions take the item's `name`, matched without regard to case, or its
`item_id`. `complete` takes the newest open item of that
name, and `reopen` the last one completed. Changes made this way show
"Home Assistant" as who made them.

Commands that cannot be carried out, such as completing an item that is not
on the list, are skipped with a warning in the add-on's log.

### Widget on your phone

The Home Assistant Companion app can show a list's items on the iPhone home
screen, and open the list when tapped. It needs the MQTT entities above.

1. Find the list's `list_id`: it is an attribute of its Open items sensor,
   under Settings > Developer tools > States.
2. Open the add-on from the sidebar in a browser and copy the path from the
   address bar, such as `/a0d7b954_shopping_list`.
3. In the Companion app, go to Settings > Companion app > Widgets > Create
   widget. Add the list's **Items** sensor, and set its tap action to
   Navigate, to the path from step 2 with the list's id:
   `/a0d7b954_shopping_list?list=1`.
4. On the home screen, add the Home Assistant **Custom** widget, then touch
   and hold it, choose Edit widget, and pick the widget from step 3.

The add-on opens the list given by `?list=`, and the first list if there is
no such list. The widget shows as much of the items as it has room for. iOS
decides when widgets are updated, so a widget can be a few minutes behind.

## Access

The add-on is only reachable through Home Assistant (ingress). It publishes no
ports, and only accepts users from Home Assistant's own proxy.
