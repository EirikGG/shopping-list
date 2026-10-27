# Shopping list

A Home Assistant add-on. Home Assistant logs users in and shows the app in its
sidebar through ingress; every HA user sees and edits every list.

- `shopping_list/` is the add-on: its `config.yaml`, `Dockerfile` and docs,
  and the source it is built from: `rest/` (Java backend) and `web/` (Vue).
- `repository.yaml` makes this repository an add-on repository.

## Install in Home Assistant

Settings > Apps > App store > ⋮ > Repositories, add
`https://github.com/EirikGG/shopping-list`, then install "Shopping list".

## Releasing

GitHub Actions (`.github/workflows/addon.yml`) tests every push and pull
request. On `main` it also builds the add-on for amd64 and aarch64 and
publishes the images to the GitHub container registry, where Home Assistant
downloads them.

A version is only published once, so to release:

1. Bump `version` in `shopping_list/config.yaml` and add it to
   `shopping_list/CHANGELOG.md`.
2. Push to `main`, and wait for the workflow to finish (a few minutes) before
   updating in Home Assistant. Until then HA may offer the update but fail to
   download it.

## Development

With the Vite dev server standing in for Home Assistant:

`docker compose -p shopping-list-dev --profile dev up -d --build`

Copy `.env.example` to `.env` first.
