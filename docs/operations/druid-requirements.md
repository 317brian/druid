---
title: System requirements
id: requirements
---

## Operating system

Any actively supported version and flavor of Linux.

## Python

Either Python 2 or Python 3.

## Java version 

Druid fully supports Java 8u92+, Java 11, and Java 17. We recommend an Open JDK-based Java distribution.

For more information about Java in Druid, see [Java runtime](./java.md)

## Metadata storage

For production deployments, use one of the following:

- MySQL MIN_VERSION or later. For more information, see [MySQL extension](../development/extensions-core/mysql.md).
- PostgreSQL MIN_VERSION or later. For more information, see [Postgre SQL extension](../development/extensions-core/postgresql.md).

## Deep storage

You can use [AWS S3](../development/extensions-core/s3.md), [Azure Blob Storage](../development/extensions-core/azure.md), or [HDFS](../development/extensions-core/hdfs.md).


