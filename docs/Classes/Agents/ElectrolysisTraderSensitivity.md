# 42 words

`ElectrolysisTraderSensitivity` is a trader that demands electricity and produces hydrogen from it via a flexible electrolysis unit.
It is a [SensitivityForecastClient](./SensitivityForecastClient.md) that uses sensitivity forecasts to consider competition of flexibility options and estimate its joint market impact with other flexibility options.

# Details

## Strategies

`ElectrolysisTraderSensitivity` employs an [ElectrolyzerStrategist](../Modules/ElectrolyzerStrategist(Sensitivity).md) that is compatible with sensitivity forecasts.
Currently, only a single variant, [ProductionTarget](../Modules/ProductionTarget.md) is implemented.
It schedules electrolyzer operation based on a moving target of hydrogen production totals and electricity prices.
It considers the sensitivity of the merit order with respect to demand and supply changes to optimise its dispatch.

## Contracts

ElectrolysisTraderSensitivity requires contracts with:
* [SensitivityForecaster](./SensitivityForecaster.md): Send `ForecastRegistration`, `SensitivityRequest`, and `NetAward`; receive `SensitivityForecast`
* [DayAheadMarket](./DayAheadMarket.md): Send `Bid`s and receive `Award`s
* [FuelsMarket](./FuelsMarket.md): Send `FuelPriceForecastRequest` and `FuelBid`; receive `FuelPriceForecast` and `FuelBill`
* [HydrogenSupportPolicy](./HydrogenSupportPolicy.md): Send `SupportInfoRequest`, `SupportPayoutRequest`; receive `SupportInfo`, `SupportPayout`

# Dependencies

* [SensitivityForecaster](./SensitivityForecaster.md)
* [FuelsMarket](./FuelsMarket.md)
* [HydrogenSupportProvider](../Abilities/HydrogenSupportProvider.md)

# Input from file

* `Device`: Group, defined in [Electrolyzer](../Modules/Electrolyzer.md) covering technical details of the electrolyzer device
* `Strategy`: Group defined in [ElectrolyzerStrategist](../Modules/ElectrolyzerStrategist(Sensitivity).md) with strategy parameters
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

See also [DayAheadMarketTrader](../Abilities/DayAheadMarketTrader.md) and [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md).

# Messages

See [SensitivityForecastClient](../Abilities/SensitivityForecastClient.md), [DayAheadMarketTrader](../Abilities/DayAheadMarketTrader.md), [FuelsTrader](../Abilities/FuelsTrader.md), and [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md).

# See also

* [DayAheadMarket](./DayAheadMarket.md)
* [DayAheadMarketTrader](../Abilities/DayAheadMarketTrader.md)
* [SensitivityForecaster](./SensitivityForecaster.md)
* [SensitivityForecastClient](../Abilities/SensitivityForecastClient.md)
* [FuelsMarket](./FuelsMarket.md)
* [FuelsTrader](../Abilities/FuelsTrader.md)
* [HydrogenSupportPolicy](./HydrogenSupportPolicy.md)
* [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md)
