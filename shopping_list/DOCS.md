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

`update` changes an item given by its `item_id`: any of `name`, `amount` and
`completed` (`true` or `false`), keeping what is left out:

```json
{"action": "update", "item_id": 5, "name": "Oat milk", "amount": 3, "completed": true}
```

Commands that cannot be carried out, such as completing an item that is not
on the list, are skipped with a warning in the add-on's log.

## To-do lists in Home Assistant (HACS)

The Shopping list (add-on) integration makes every list a to-do list in Home
Assistant: in its To-do page, in Assist ("add milk to Handleliste"), and in
the Companion app's To-do List widget on the phone's home screen. It uses
the MQTT entities above, so the add-on and the MQTT integration must be set
up first.

1. In HACS, choose ⋮ > Custom repositories, and add
   `https://github.com/EirikGG/shopping-list` with the type Integration.
2. Download **Shopping list (add-on)** in HACS, and restart Home Assistant.
3. Go to Settings > Devices & services > Add integration, and choose
   Shopping list (add-on). There is nothing to fill in.

Every list is then a `todo.` entity, such as `todo.handleliste`. Items show
their amount, as in `2 × Melk`, and their labels as the description. Typing
`3 x Egg` or `3 × Egg` adds Egg with the amount 3. The 50 most recently
completed items are shown as completed. Items can be added, ticked off,
renamed and deleted; labels, links and files are only in the add-on.

### Widget on your phone

On the iPhone, touch and hold the home screen, tap +, choose Home Assistant
and the **To-do List** widget. Then touch and hold the widget, choose Edit
widget, and pick the list. Items are ticked off and added in the widget;
tapping the list's name opens it in the Companion app.

### Opening a list in the add-on

A link to the add-on with `?list=<id>` opens that list, where `<id>` is the
`list_id` attribute of the list's Open items sensor. The path is the
add-on's address in the sidebar, such as `/a0d7b954_shopping_list?list=1`.

## Access

The add-on is only reachable through Home Assistant (ingress). It publishes no
ports, and only accepts users from Home Assistant's own proxy.
