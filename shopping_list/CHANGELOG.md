# Changelog

## 0.10.1

- Works around the Home Assistant app on iPhone sometimes showing a black
  screen after the add-on has loaded, though tapping still worked. The page
  is now drawn again after the lists load, when they change, and when the
  app comes back to it.
- An item's time is at the right end of the line under its name, so it is in
  the same place on every row.

## 0.10.0

- Five store sections for things that are not groceries: Tools and hardware,
  Electrical, Furniture and home, Clothing and shoes, and Car. Before, items
  like cables, plugs and furniture were put in the nearest grocery section.
- Light bulbs and batteries are under Electrical rather than Household.
- Slimmer rows: an item's labels, links, files and time are on one line under
  its name, and the space around rows and section headings is smaller.

## 0.9.0

- Items are sorted into store sections, such as Fruit and vegetables or
  Dairy and eggs, and shown under a heading for each, in the order a shop
  usually has them. Items that fit no section are under Uncategorised, last.
  The button beside the list name turns the sections off for that list.
- The sorting is done on the add-on itself by a small language model
  (multilingual-e5-small), which understands Norwegian and English. Nothing
  is sent anywhere. Items already on the lists are sorted shortly after the
  add-on starts.
- The add-on image is about 300 MB larger, and uses about 200 MB more memory.

## 0.8.0

- The Shopping list (add-on) integration, installed through HACS from this
  repository, makes every list a to-do list in Home Assistant. They work in
  the To-do page, in Assist and in the Companion app's To-do List widget.
  See the documentation tab.
- MQTT: the `update` command changes an item's name, amount or completed
  state, by its `item_id`. The list's state also has its 50 most recently
  completed items, as `completed_items`.
- The Items sensor from 0.7.0 is removed; the To-do List widget replaces it.

## 0.7.0

- Every list has an Items sensor in MQTT, with its open items as one line
  in Norwegian, such as "2 × Melk, Brød, Egg".
- A link with `?list=<id>` opens that list.
- The documentation tab shows how to put a list on the iPhone home screen
  with the Companion app's Custom widget, which opens the list when tapped.

## 0.6.0

- Stores are now called labels (etiketter), with a tag icon, so they also
  suit lists that are not for shopping, like a Christmas list. Your stores
  are kept as labels, with their items.
- MQTT: events and the Open items sensor say `labels` instead of `stores`.
  Update automations that read `stores`. Commands still take `stores`.
- The API uses `/api/label`, `/api/label-list` and `/api/label-item-mapping`,
  with `labelId` and `labelIds`.

## 0.5.2

- The store filter has a "No store" chip, for the items without a store. It
  can be combined with stores, like the other chips.

## 0.5.1

- The Changes events have `by_user_id`, the Home Assistant user who made the
  change, which matches `user_id` on person entities. It is empty for changes
  made through MQTT.

## 0.5.0

- With an MQTT broker in Home Assistant, every list shows up as a device with
  an Open items sensor, a Changes event entity for automations, and an Add
  item text field. Automations can add, complete, reopen and delete items
  through MQTT. See the documentation tab.

## 0.4.1

- Every Home Assistant user gets Shopping list in the sidebar, not only
  admins.

## 0.4.0

- Items have an amount, set with − and + in the item editor. An amount above
  1 is shown before the name, like "2 × Milk".
- The count beside each list is now what is left out of the total, like 3/5.

## 0.3.2

- Store chips no longer have a square behind them, most visible in the dark
  theme.

## 0.3.1

- Choosing stores when adding an item on a phone is quicker: the stores are
  one row of chips to swipe through, with New first. Stores are renamed and
  deleted in the item editor, which now shows them all.
- The item editor no longer brings up the keyboard on phones when it opens.

## 0.3.0

- Items have links and files: add a recipe link, a photo of the product or a
  receipt. Images are shown in the item, other files can be downloaded.
- Editing an item opens a larger editor with its name, stores, links and
  files. The list shows how many links and files an item has.

## 0.2.1

- Installs and updates from a ready-built image, instead of building on the
  device. Updates now take seconds rather than minutes.

## 0.2.0

- Items still to buy show when they were added.
- Lists are sorted newest first: open items by when they were added, then
  completed items by when they were completed.

## 0.1.0

- First version as a Home Assistant add-on: lists, items and stores, shared by
  every Home Assistant user, stored in SQLite.
