# 42 words

ElectrolysisTraderSensitivity is a flexible Trader that demands electricity and produces hydrogen from it via electrolysis.
It is a [FlexibilityTrader](./FlexibilityTrader.md) that uses sensitivity forecasts to consider competition of flexibility options.
This agent extends the base ElectrolysisTrader by incorporating sensitivity information from a [SensitivityForecastProvider](../Abilities/SensitivityForecastProvider.md) to estimate its joint market impact with other flexibility options.

# Details

## Strategies

ElectrolysisTraderSensitivity uses the ProductionTarget strategist by default, which schedules based on a moving target of hydrogen production totals and electricity prices.
It considers the sensitivity of the merit order with respect to demand and supply changes to optimize its dispatch.

| Strategist        | Used Forecast              | Provide Forecast | Flex agent count        | Robustness | Available to                          |
|-------------------|----------------------------|------------------|-------------------------|------------|---------------------------------------|
| ProductionTarget  | price, sensitivity         | yes              | multiple                | tested     | ElectrolysisTraderSensitivity         |

## Sensitivity-Based Dispatch

The key feature of ElectrolysisTraderSensitivity is its ability to estimate its market impact through sensitivity forecasts.
It interacts with a [SensitivityForecaster](./SensitivityForecaster.md) which provides information about how changes in demand or supply affect market outcomes.

The interaction flow is as follows:
1. ElectrolysisTraderSensitivity registers at the SensitivityForecaster with its type of sensitivity needed
2. It sends net awards after market clearings to help the forecaster update its joint-impact assessment
3. It requests sensitivity forecasts which include an estimate of its dispatch multiplier
4. The strategist uses this information to optimize dispatch considering competition with other flexibility options

## Contracts

ElectrolysisTraderSensitivity requires contracts with:
* [SensitivityForecaster](./SensitivityForecaster.md): Send ForecastRegistration, NetAward, and SensitivityRequest; receive SensitivityForecast
* [DayAheadMarket](./DayAheadMarket.md): Send bids and receive awards
* [FuelsMarket](./FuelsMarket.md): Offer hydrogen supply
* [HydrogenSupportPolicy](./HydrogenSupportPolicy.md): Receive support information and send payout requests

# Dependencies

* [SensitivityForecastProvider](../Abilities/SensitivityForecastProvider.md)
* [SensitivityForecaster](./SensitivityForecaster.md)
* [FuelsMarket](./FuelsMarket.md)
* [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md)
* [HydrogenSupportProvider](../Abilities/HydrogenSupportProvider.md)

# Input from file

* Device: Group, defined in [Electrolyzer](../Modules/Electrolyzer.md) covering technical details of the electrolyzer device
* Strategy: Group defined in [ElectrolyzerStrategist](../Modules/ElectrolyzerStrategist.md) with ProductionTarget parameters:
  * ProductionTarget.HydrogenProductionTargetInMWH: Time series of hydrogen production targets
  * ProductionTarget.ProductionTargetIntervalInHours: Interval in hours for production target fulfillment
* Support: Optional group, defined in [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md) covering hydrogen support details

# Input from environment

See [FlexibilityTrader](./FlexibilityTrader.md), [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md), and [SensitivityForecastClient](../Abilities/SensitivityForecastClient.md)

# Simulation outputs

* OfferedElectricityPriceInEURperMWH: Offered price for electricity in EUR/MWh
* ProducedHydrogenInMWH: Produced amount of hydrogen in MWh
* ReceivedMoneyForHydrogenInEUR: Total received money for selling hydrogen in EUR
* ReceivedMoneyForElectricityInEUR: Total received money for purchasing electricity in EUR
* VariableCostsInEUR: Variable costs in EUR
* DispatchMultiplier: Current dispatch multiplier estimate from sensitivity forecaster

See also [FlexibilityTrader](./FlexibilityTrader.md)

# Messages

* [Sensitivity](../Comms/Sensitivity.md): received SensitivityForecast from SensitivityForecaster
* [ForecastClientRegistration](../Comms/ForecastClientRegistration.md): sent ForecastRegistration to SensitivityForecaster
* [AmountAtTime](../Comms/AmountAtTime.md): sent NetAward to SensitivityForecaster, received hydrogen price forecast
* [ClearingTimes](../Comms/ClearingTimes.md): received GateClosureInfo from DayAheadMarket

See also [FlexibilityTrader](./FlexibilityTrader.md)

# See also

* [FlexibilityTrader](./FlexibilityTrader.md)
* [ElectrolysisTrader](./ElectrolysisTrader.md)
* [SensitivityForecaster](./SensitivityForecaster.md)
* [SensitivityForecastProvider](../Abilities/SensitivityForecastProvider.md)
* [SensitivityForecastClient](../Abilities/SensitivityForecastClient.md)
* [FuelsMarket](./FuelsMarket.md)
* [HydrogenSupportPolicy](./HydrogenSupportPolicy.md)
