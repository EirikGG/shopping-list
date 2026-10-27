# Shopping list

A Home Assistant add-on. Home Assistant logs users in and shows the app in its
sidebar through ingress; every HA user sees and edits every list.

- `shopping_list/` is the add-on: its `config.yaml`, `Dockerfile` and docs,
  and the source it is built from: `rest/` (Java backend) and `web/` (Vue).
- `repository.yaml` makes this repository an add-on repository.

## Install in Home Assistant

Settings > Add-ons > Add-on store > ⋮ > Repositories, add
`https://github.com/EirikGG/shopping-list`, then install "Shopping list". Home
Assistant builds the add-on on the device, which takes a few minutes.

## Development

With the Vite dev server standing in for Home Assistant:

`docker compose -p shopping-list-dev --profile dev up -d --build`

Copy `.env.example` to `.env` first.
