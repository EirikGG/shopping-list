# Changelog

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
