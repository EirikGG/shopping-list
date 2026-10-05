---
id: TASK-3
title: Create github action for deployment to test and prod
status: To Do
assignee:
  - egg
created_date: '2026-10-05 15:39'
updated_date: '2026-10-05 15:40'
labels: []
dependencies: []
type: feature
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Should have a backup service in docker-compose which takes backup in a 3 day intervall. Deploy to test should happen automatically when commiting to master with files and db from prod backup. 

Deploy to prod should happen manually - a full backup is taken and app is deployed.
<!-- SECTION:DESCRIPTION:END -->
