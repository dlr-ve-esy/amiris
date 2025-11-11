# AMIRIS Quickstart Guide

AMIRIS is a *JAVA* application configured via *Python* scripts.
To run AMIRIS, you need **Python** and a **Java Development Kit**.
Check the required version [here](../Get-Started.md#requirements).
This quick start guide assumes that you have both Java and Python installed and are familiar with Python environments.
If you experience troubles, try the more explicit [Step-by-Step Guide](./StepByStep.md).

## Installation and Execution

1. **Environment**: Create and activate a Python environment with Python 3.9 or higher.
2. **AMIRIS-Py**: run `pip install amirispy` to install the *amirispy* package.
3. **AMIRIS Files**: use `amiris download` to download the AMIRIS ".jar" executable and examples into the *current folder* 
4. **Execution**: Run AMIRIS with `amiris run --scenario ./examples/demo/Simple/scenario.yaml --output simple`, where
    * `--scenario ./examples/demo/Simple/scenario.yaml` points to a `scenario.yaml` in one of the subfolders from the examples you just downloaded; Choose any example of interest.
    * `--output simple` sets the name of the folder to be created and hold the result data. Results in existing folders are overwritten.

Executing AMIRIS should only take a few seconds on any computer.
Now, open the newly created folder called "simple" and check out the [Results](./Results.md).

### Updating
Feel free to give the AMIRIS project a "star" :star: and hit the notification bell :bell: in order to get notifications on new releases.
**In the future**: If you want to update your existing model version use

* `amiris download -m model` to download the latest model, and
* `amiris download -m examples` to get the latest examples.

## Next steps

* Try out the `backtest` examples for Germany or Austria as provided in the [AMIRIS-Examples](https://gitlab.com/dlr-ve/esy/amiris/examples)
* Compare the backtest examples for Germany and Austria with real market data, e.g., from [Smard.de](https://www.smard.de/).
* Experiment by changing the scenarios: Open a `scenario.yaml` and change, e.g., CO2 or fuel prices. Simply replace the name of the corresponding timeseries with a constant value directly in the YAML file without editing the CSV file.
* Replace data with your own timeseries, change installed capacity, create your own scenarios...