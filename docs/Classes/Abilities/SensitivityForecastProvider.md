# In short

Agents that implement this Ability can provide sensitivity forecasts that reflect changes to the merit order if demand or supply is increased.

# Details

`SensitivityForecastProvider`s send [Sensitivity](../Comms/Sensitivity.md) messages to their [Clients](./SensitivityForecastClient.md).
They may provide different types of sensitivity forecasts.

# Available Products

* `SensitivityForecast`: sent to clients

# Outputs

None

# Messages

* [Sensitivity](../Comms/Sensitivity.md): impacts on the merit order if demand or supply is increased

# See also

* [Clients](./SensitivityForecastClient.md)
* [SensitivityForecaster](../Agents/SensitivityForecaster.md)
* [SensitivityForecasterFile](../Agents/SensitivityForecasterFile.md)