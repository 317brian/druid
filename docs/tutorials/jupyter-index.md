---
id: tutorial-jupyter-index
title: "Jupyter notebook tutorials"
---

<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one
  ~ or more contributor license agreements.  See the NOTICE file
  ~ distributed with this work for additional information
  ~ regarding copyright ownership.  The ASF licenses this file
  ~ to you under the Apache License, Version 2.0 (the
  ~ "License"); you may not use this file except in compliance
  ~ with the License.  You may obtain a copy of the License at
  ~
  ~   http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing,
  ~ software distributed under the License is distributed on an
  ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  ~ KIND, either express or implied.  See the License for the
  ~ specific language governing permissions and limitations
  ~ under the License.
  -->

You can try out the Druid APIs using the Jupyter notebook-based tutorials that are available. These tutorials provide snippets of Python code that you can use to run calls against the Druid API.

## Before you start

Make sure you meet the following requirements before starting the Jupyter-based tutorials:

- Python3 

- The `requests` package for Python. For example, you can install it with the following command: 
   
   ```bash
   pip3 install requests
   ````

- Jupyter lab (recommended) or Jupyter notebook running on a non-default port. By default, Druid and Jupyter both try to use port `8888,` so start Jupyter on a different port. For example, use the following command to start Jupyter lab on port `3001`:
   
   ```bash
   jupyter lab --port 3001
   ```

- An available Druid instance. You can use the `micro-quickstart` configuration described in [Quickstart (local)](./index.md). The tutorials assume that you are using the quickstart, so no authentication or authorization is expected unless explicitly mentioned.

## Tutorials

- [Introduction to the Druid API](./jupyter/api-tutorial.ipynb) walks you through some of the basics related to the Druid API and several endpoints.