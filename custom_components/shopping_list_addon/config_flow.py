"""Adding the integration: there is nothing to fill in."""

from __future__ import annotations

from typing import Any

from homeassistant.config_entries import ConfigFlow, ConfigFlowResult

from .const import DOMAIN


class ShoppingListAddonConfigFlow(ConfigFlow, domain=DOMAIN):
    VERSION = 1

    async def async_step_user(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        if "mqtt" not in self.hass.config.components:
            return self.async_abort(reason="mqtt_not_set_up")
        if user_input is None:
            return self.async_show_form(step_id="user")
        return self.async_create_entry(title="Shopping list", data={})
