# 42 words

`SensitivityForecasterFile` is an implementation of [SensitivityForecastProvider](../Abilities/SensitivityForecastProvider.md).
It provides forecasts in the format of a merit order sensitivity.
However, these forecasts are insensitive to merit order changes since they are read from a file.
`SensitivityForecasterFile` is thus similar to [PriceForecasterFile](./PriceForecasterFile.md), but can be used with [GenericFlexibilityTraders](./GenericFlexibilityTrader.md) that employ a [PriceTaker](../Modules/MaxProfitPriceTaker.md) [AssessmentFunction](../Modules/AssessmentFunction.md).

# Details

`SensitivityForecasterFile` reads price forecasts from a file and converts them to [Sensitivities](../Comms/Sensitivity.md) (which are actually insensitive).

Sending a `ForecastRegistration` message to `SensitivityForecasterFile` is optional.
However, it is recommended, because if a `GenericFlexibilityTrader` with an `AssessmentFunction` that requires an _actual sensitivity_ to price changes registers with a `SensitivityForecasterFile` agent, an error will be thrown to indicate their incompatibility.
`NetAward` messages are not processed by `SensitivityForecasterFile`.
Therefore, the only mandatory message type to be sent to `SensitivityForecasterFile` is `SensitivityRequest` to receive the forecasts.

## Assumptions

Flexibility agents **should register** with the `SensitivityForecaster`, but **should not send** their actual market awards.
It is assumed that the clients employ a strategy that does not actively exploit the merit-order sensitivity, but uses it as an "as-is-price-forecasts", for example, the [MaxProfitPriceTaker](../Modules/MaxProfitPriceTaker.md) strategy.

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