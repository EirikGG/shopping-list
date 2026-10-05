---
id: TASK-1
title: Add fileserver and system for uploading images
status: To Do
assignee:
  - egg
created_date: '2026-10-05 15:28'
labels: []
dependencies: []
type: feature
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
ChatGPT recogments SeaweedFS:

For your setup, I would not use a traditional file server like Samba/NFS for application attachments. I’d use an S3-compatible object store in Docker Compose.
For your Java/Javalin + PostgreSQL setup, my first choice would currently be SeaweedFS, running in its simple single-node weed mini mode. SeaweedFS explicitly supports single-node production use, exposes an S3-compatible API, supports presigned URLs, and has official Docker/Compose examples.
<!-- SECTION:DESCRIPTION:END -->
