# 42 words

`ElectrolysisTraderSensitivity` is a trader that demands electricity and produces hydrogen from it via a flexible electrolysis unit.
It is a [SensitivityForecastClient](./SensitivityForecastClient.md) that uses sensitivity forecasts to consider competition of flexibility options and estimate its joint market impact with other flexibility options.

# Details

## Strategies

`ElectrolysisTraderSensitivity` uses the [ProductionTarget](../Modules/ProductionTarget.md) strategist by default, which schedules electrolyzer operation based on a moving target of hydrogen production totals and electricity prices.
It considers the sensitivity of the merit order with respect to demand and supply changes to optimise its dispatch.

| Strategist        | Used Forecast              | Provide Forecast | Flex agent count        | Robustness | Available to                          |
|-------------------|----------------------------|------------------|-------------------------|------------|---------------------------------------|
| ProductionTarget  | sensitivity                | no               | multiple                | untested   | ElectrolysisTraderSensitivity         |

## Sensitivity-Based Dispatch

The key feature of `ElectrolysisTraderSensitivity` is its ability to estimate its market impact through sensitivity forecasts.
It interacts with a [SensitivityForecaster](./SensitivityForecaster.md) which provides information about how changes in demand or supply affect electricity prices.

## Contracts

ElectrolysisTraderSensitivity requires contracts with:
* [SensitivityForecaster](./SensitivityForecaster.md): Send `ForecastRegistration`, `SensitivityRequest`, and `NetAward`; receive `SensitivityForecast`
* [DayAheadMarket](./DayAheadMarket.md): Send `Bid`s and receive `Award`s
* [FuelsMarket](./FuelsMarket.md): Send `FuelBid` to offer hydrogen supply; receive `FuelBill` with payments for sold hydrogen
* [HydrogenSupportPolicy](./HydrogenSupportPolicy.md): Send `SupportInfoRequest`, `SupportPayoutRequest`; receive `SupportInfo`, `SupportPayout`

# Dependencies

* [SensitivityForecaster](./SensitivityForecaster.md)
* [FuelsMarket](./FuelsMarket.md)
* [HydrogenSupportProvider](../Abilities/HydrogenSupportProvider.md)

# Input from file

* `Device`: Group, defined in [Electrolyzer](../Modules/Electrolyzer.md) covering technical details of the electrolyzer device
* `Strategy`: Group defined in [ElectrolyzerStrategist](../Modules/ElectrolyzerStrategist.md) with strategy parameters
* `Support`: Optional group, defined in [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md) covering hydrogen support details

# Input from environment

See
* [DayAheadMarketTrader](../Abilities/DayAheadMarketTrader.md)
* [SensitivityForecastClient](../Abilities/SensitivityForecastClient.md)
* [FuelsTrader](../Abilities/FuelsTrader.md)
* [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md)

# Simulation outputs

* `OfferedElectricityPriceInEURperMWH`: Offered price for electricity in EUR/MWh
* `ProducedHydrogenInMWH`: Produced amount of hydrogen in thermal MWh
* `ReceivedMoneyForHydrogenInEUR`: Total received money for selling hydrogen in EUR
* `DispatchMultiplier`: Current dispatch multiplier estimate from sensitivity forecaster
* `OfferedChargePriceInEURperMWH`: Offered price for charging in EUR/MWh
* `ElectricityPricePredictionInEURperMWH`: Assumed electricity price in the next market clearing event based on forecast + flexibility dispatch
* `VariableCostsInEUR`: Variable operation and maintenance costs in EUR

# Messages

* [ForecastClientRegistration](../Comms/ForecastClientRegistration.md): sent `ForecastRegistration` to [SensitivityForecaster](./SensitivityForecaster.md)
* [Sensitivity](../Comms/Sensitivity.md): received `SensitivityForecast` from [SensitivityForecaster](./SensitivityForecaster.md)
* [FuelData](../Comms/FuelData.md): sent `FuelPriceForecastRequest` and `FuelPriceRequest` to [FuelsMarket](./FuelsMarket.md)
* [AmountAtTime](../Comms/AmountAtTime.md): sent `NetAward` to [SensitivityForecaster](./SensitivityForecaster.md); received hydrogen price forecast from [FuelsMarket](./FuelsMarket.md)
* [ClearingTimes](../Comms/ClearingTimes.md): received `GateClosureInfo` from [DayAheadMarket](./DayAheadMarket.md)

# See also

* [DayAheadMarket](./DayAheadMarket.md)
* [SensitivityForecaster](./SensitivityForecaster.md)
* [FuelsMarket](./FuelsMarket.md)
* [HydrogenSupportPolicy](./HydrogenSupportPolicy.md)
