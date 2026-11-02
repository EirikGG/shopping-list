"""Every list of the add-on as a to-do list.

The add-on publishes each list, retained, to shopping_list/list/<id>/state:
its name, the open items newest first, and the recently completed ones. An
empty message there means the list was deleted. Changes made here are sent
as commands to the list's command topic, and come back as a new state.
"""

from __future__ import annotations

import json
import logging
import re
from typing import Any

from homeassistant.components import mqtt
from homeassistant.components.todo import (
    TodoItem,
    TodoItemStatus,
    TodoListEntity,
    TodoListEntityFeature,
)
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.exceptions import ConfigEntryNotReady, HomeAssistantError
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import STATE_TOPICS, STATUS_TOPIC

_LOGGER = logging.getLogger(__name__)

_LIST_ID = re.compile(r"^shopping_list/list/(\d+)/state$")
# "2 × Milk", also typed as "2 x Milk" or "2x Milk": the amount and the name.
_AMOUNT = re.compile(r"^\s*(\d{1,3})\s*[×xX]\s+(\S.*)$")
MAX_AMOUNT = 999


def summary(name: str, amount: int) -> str:
    """An item as it is shown, with its amount when there is more than one."""
    return f"{amount} × {name}" if amount > 1 else name


def parse_summary(text: str) -> tuple[str, int]:
    """The name and amount of what was typed, such as "2 × Milk"."""
    match = _AMOUNT.match(text)
    if match and 1 <= int(match.group(1)) <= MAX_AMOUNT:
        return match.group(2).strip(), int(match.group(1))
    return text.strip(), 1


async def async_setup_entry(
    hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback
) -> None:
    if not await mqtt.async_wait_for_mqtt_client(hass):
        raise ConfigEntryNotReady("MQTT is not connected")

    lists: dict[int, ShoppingListEntity] = {}
    # The add-on says "offline" when it stops.
    online = {"value": True}

    @callback
    def state_received(msg: mqtt.ReceiveMessage) -> None:
        match = _LIST_ID.match(msg.topic)
        if not match:
            return
        list_id = int(match.group(1))
        entity = lists.get(list_id)

        if not msg.payload:
            # The list was deleted in the add-on.
            if entity is not None:
                del lists[list_id]
                registry = er.async_get(hass)
                if entity.entity_id and registry.async_get(entity.entity_id):
                    registry.async_remove(entity.entity_id)
                else:
                    hass.async_create_task(entity.async_remove())
            return

        try:
            state = json.loads(msg.payload)
        except ValueError:
            _LOGGER.warning("Ignored a list that is not JSON on %s", msg.topic)
            return

        if entity is None:
            entity = ShoppingListEntity(list_id, state, online)
            lists[list_id] = entity
            async_add_entities([entity])
        else:
            entity.set_state(state)

    @callback
    def status_received(msg: mqtt.ReceiveMessage) -> None:
        online["value"] = msg.payload != "offline"
        for entity in lists.values():
            if entity.hass is not None:
                entity.async_write_ha_state()

    entry.async_on_unload(await mqtt.async_subscribe(hass, STATUS_TOPIC, status_received))
    entry.async_on_unload(await mqtt.async_subscribe(hass, STATE_TOPICS, state_received))


class ShoppingListEntity(TodoListEntity):
    """One list. Its items' uids are the add-on's item ids."""

    _attr_has_entity_name = False
    _attr_icon = "mdi:cart-outline"
    _attr_should_poll = False
    _attr_supported_features = (
        TodoListEntityFeature.CREATE_TODO_ITEM
        | TodoListEntityFeature.UPDATE_TODO_ITEM
        | TodoListEntityFeature.DELETE_TODO_ITEM
    )

    def __init__(self, list_id: int, state: dict[str, Any], online: dict[str, bool]) -> None:
        self._list_id = list_id
        self._online = online
        self._attr_unique_id = f"shopping_list_{list_id}_todo"
        self._command_topic = f"shopping_list/list/{list_id}/command"
        self._summaries: dict[str, str] = {}
        self._read(state)

    @property
    def available(self) -> bool:
        return self._online["value"]

    @callback
    def set_state(self, state: dict[str, Any]) -> None:
        self._read(state)
        if self.hass is not None:
            self.async_write_ha_state()

    def _read(self, state: dict[str, Any]) -> None:
        self._attr_name = state.get("list") or f"Shopping list {self._list_id}"
        items = [
            self._item(item, TodoItemStatus.NEEDS_ACTION) for item in state.get("items", [])
        ] + [
            self._item(item, TodoItemStatus.COMPLETED) for item in state.get("completed_items", [])
        ]
        self._summaries = {item.uid: item.summary for item in items}
        self._attr_todo_items = items

    @staticmethod
    def _item(item: dict[str, Any], status: TodoItemStatus) -> TodoItem:
        labels = item.get("labels") or []
        return TodoItem(
            uid=str(item["id"]),
            summary=summary(item["name"], item.get("amount") or 1),
            status=status,
            description=", ".join(labels) or None,
        )

    async def async_create_todo_item(self, item: TodoItem) -> None:
        name, amount = parse_summary(item.summary or "")
        if not name:
            raise HomeAssistantError("The item has no name")
        await self._send({"action": "add", "name": name, "amount": amount})

    async def async_update_todo_item(self, item: TodoItem) -> None:
        command: dict[str, Any] = {"action": "update", "item_id": int(item.uid)}
        if item.status is not None:
            command["completed"] = item.status == TodoItemStatus.COMPLETED
        # Ticking an item off sends its summary unchanged, which is then left
        # alone; an item named "3 x 4" with amount 1 keeps its name.
        if item.summary is not None and item.summary != self._summaries.get(item.uid):
            name, amount = parse_summary(item.summary)
            if not name:
                raise HomeAssistantError("The item has no name")
            command["name"] = name
            command["amount"] = amount
        await self._send(command)

    async def async_delete_todo_items(self, uids: list[str]) -> None:
        for uid in uids:
            await self._send({"action": "delete", "item_id": int(uid)})

    async def _send(self, command: dict[str, Any]) -> None:
        await mqtt.async_publish(self.hass, self._command_topic, json.dumps(command))
