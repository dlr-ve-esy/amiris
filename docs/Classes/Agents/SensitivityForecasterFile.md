# 42 words

`SensitivityForecasterFile` is an implementation of [SensitivityForecastProvider](../Abilities/SensitivityForecastProvider.md).
It provides forecasts in the format of a merit order sensitivity.
These forecasts, however, are insensitive to merit order changes since the forecasts are read from file.
`SensitivityForecasterFile` is thus similar to [PriceForecasterFile](./PriceForecasterFile.md), but can be used with [GenericFlexibilityTraders](./GenericFlexibilityTrader.md) that employ a [PriceTaker](../Modules/MaxProfitPriceTaker.md) [AssessmentFunction](../Modules/AssessmentFunction.md).

# Details

`SensitivityForecasterFile` will read price forecasts from file and convert them to (actually insensitive) [Sensitivities](../Comms/Sensitivity.md).

Sending a `ForecastRegistration` message to `SensitivityForecasterFile` is optional.
However, it is recommended to do so, because if a `GenericFlexibilityTraders` with an `AssessmentFunction` that requires a sensitivity to price changes registers to a `SensitivityForecasterFile` agent, an error is thrown to indicate their incompatibility.
`NetAward` messages are not digested by `SensitivityForecasterFile`.
Thus, the only mandatory type of message to be sent to `SensitivityForecasterFile` is `SensitivityRequest` to receive the forecasts.

## Assumptions

Flexibility agents **should register** at the `SensitivityForecaster` but **should not send** their actual market awards.
It is assumed that the clients employ a strategy that is not actively exploiting the merit-order sensitivity, but use them as "as-is-price-forecasts", like, the [MaxProfitPriceTaker](../Modules/MaxProfitPriceTaker.md) strategy for example.

# Dependencies

None

# Input from file

* `PriceForecastsInEURperMWH`: timeseries of electricity price forecasts

# Input from environment

* `ForecastRegistration` messages from flexibility option clients
* `SensitivityRequest` messages from flexibility option clients

# Simulation outputs

* `ElectricityPriceForecastInEURperMWH`: The forecasted value for the electricity price.

# Contracts

* FlexibilityOptions: receive `ForecastRegistration`
* FlexibilityOptions: receive `SensitivityRequest` send `SensitivityForecast`

# Available Products

See [SensitivityForecastProvider](../Abilities/SensitivityForecastProvider.md)

# Submodules

None

# Messages

* [Sensitivity](../Comms/Sensitivity.md) sent to forecast clients

# See also

* [SensitivityForecastProvider](../Abilities/SensitivityForecastProvider.md)
* [Sensitivity](../Comms/Sensitivity.md)
* [SensitivityForecastClient](../Abilities/SensitivityForecastClient.md)
* [PriceForecasterFile](./PriceForecasterFile.md)
* [MarketForecaster](./MarketForecaster.md)
* [GenericFlexibilityTraders](./GenericFlexibilityTrader.md)
* [PriceTaker](../Modules/MaxProfitPriceTaker.md)
* [AssessmentFunction](../Modules/AssessmentFunction.md)