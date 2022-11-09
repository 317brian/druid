# Jupyter notebook tutorials for Druid

You can try out the Druid APIs using the Jupyter Notebook-based tutorials that are available. These tutorials provide snippets of Python code that you can use to run calls against the Druid API.

## Before you start

Make sure you meet the following requirements before starting the Jupyter-based tutorials:

- Python3 

- The `requests` package for Python. For example, you can install it with the following command: 
   
   ```bash
   pip3 install requests
   ````

- Jupyter Lab (recommended) or Jupyter Notebook running on a non-default port. By default, Druid and Jupyter both try to use port `8888,` so start Jupyter on a different port. For example, use the following command to start Jupyter Lab on port `3001`:
   
   ```bash
   jupyter lab --port 3001
   ```

- An available Druid instance. You can use the `micro-quickstart` configuration described in [Quickstart (local)](../../../docs/tutorials/index.md). The tutorials assume that you are using the quickstart, so no authentication or authorization is expected unless explicitly mentioned.

## Tutorials

- [Introduction to the Druid API](./api-tutorial.ipynb) walks you through some of the basics related to the Druid API and several endpoints.

## Contributing

If you build a Jupyter tutorial, you need to do a few things to add it to the docs in addition to saving the notebook in this directory:

- Clear the outputs from your notebook before you make the PR. You can use the following command: 

   ```bash
   jupyter nbconvert --ClearOutputPreprocessor.enabled=True --inplace ./path/to/notebook/notebookName.ipynb
   ```

- Update the list of [Tutorials](#tutorials) on this page and in the [ Jupyter tutorial index page](../../../docs/tutorials/tutorial-jupyter-index.md#tutorials) in the `docs/tutorials` directory.