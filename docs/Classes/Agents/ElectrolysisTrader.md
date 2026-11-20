# 42 words

`ElectrolysisTrader` is a type of `FlexibilityTrader` that operates an `Electrolyzer` unit to produce hydrogen from electricity.
It is thus a flexible demand `Trader` and will create demand bids.
As a child of [FuelsTrader](../Abilities/FuelsTrader.md), it can also trade fuels at a [FuelsMarket](./FuelsMarket.md).
Produced hydrogen is sold at the `FuelsMarket`.
Depending on the `Strategist` used, it can either operate flexibly trying to minimize costs in order to meet periodical hydrogen production targets, reproduce a dispatch time series, or maximise its profits from PPA marketing.

# Details

## Strategies

Two operation Strategists are available for the ElectrolysisTrader:

* `DISPATCH_FILE`: Creates a [BidSchedule](../Modules/BidSchedule.md) following a given *TimeSeries* read from an input file. It can provide forecasts regarding its behaviour.
* `SINGLE_AGENT_SIMPLE`: Uses the forecasted price and an estimate for price changes to estimate a good dispatch strategy.
  This strategy is assumed to be somewhat robust and might be used in combination with other flexibility options, but has not been tested for this feature.
* An additional Strategist `GREEN_HYDROGEN_MONTHLY` is only available to ElectrolysisTrader's child class [GreenHydrogenTraderMonthly](./GreenHydrogenTraderMonthly.md).

## Alternative: ElectrolysisTraderSensitivity

For scenarios where multiple flexibility options compete and their joint market impact should be considered, use [ElectrolysisTraderSensitivity](./ElectrolysisTraderSensitivity.md).
This variant uses sensitivity forecasts to estimate dispatch multipliers and optimise its dispatch when competing against other flexibility options.

| Strategist             | Used Forecast              | Provide Forecast | Flex agent count        | Robustness | Available to               |
|------------------------|----------------------------|------------------|-------------------------|------------|----------------------------|
| DISPATCH_FILE          | none                       | yes              | multiple                | n/a        | ElectrolysisTrader         |
| SINGLE_AGENT_SIMPLE    | price                      | no               | single (maybe multiple) | untested   | ElectrolysisTrader         |
| GREEN_HYDROGEN_MONTHLY | electricity, hydrogen, PPA | no               | single (maybe multiple) | untested   | GreenHydrogenTraderMonthly |

## Contracts

Contracts of the ElectrolysisTrader depend on its dispatch strategy:

* `SINGLE_AGENT_SIMPLE` requires a price forecast from the [PriceForecaster](./PriceForecaster.md)
* `DISPATCH_FILE` provides a BidsForecast to the [MarketForecaster](./MarketForecaster.md).

Once the forecasts are obtained, the ElectrolysisTrader uses its [Strategist](../Modules/ElectrolyzerStrategist.md) to calculate a suitable [DispatchSchedule](../Modules/BidSchedule.md).
The dispatch plan may be affected by optional support payments for produced hydrogen paid by the [HydrogenSupportPolicy](./HydrogenSupportPolicy.md).
The DispatchSchedule can be of any duration between 1 and the foresight period.
It will be based upon the specifications of the attached [Electrolyzer](../Modules/Electrolyzer.md) unit.
Following the DispatchSchedule, the ElectrolysisTrader places demand bids at the [DayAheadMarket](./DayAheadMarket.md).

# Dependencies

* [PowerPlantScheduler](../Abilities/PowerPlantScheduler.md)
* [MarketForecaster](./MarketForecaster.md)
* [FuelsMarket](./FuelsMarket.md)
* [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md)
* [HydrogenSupportProvider](../Abilities/HydrogenSupportProvider.md)

also see [FlexibilityTrader](./FlexibilityTrader.md)

# Input from file

* `Device`: Group, defined in [Electrolyzer](../Modules/Electrolyzer.md) covering technical details of the electrolyzer device to administrate
* `Strategy`: Group defined in [Strategist](../Modules/ElectrolyzerStrategist.md) covering parameters of the employed dispatch strategy
* `Support`: Optional group, defined in [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md) covering hydrogen support details

# Input from environment

See [FlexibilityTrader](./FlexibilityTrader.md) and [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md)

# Simulation outputs

* `OfferedEnergyPriceInEURperMWH`: Offered energy price in EUR per MWh
* `ProducedHydrogenInMWH`: Produced amount of hydrogen in MWh
* `ReceivedMoneyForHydrogenInEUR`: Total received money for selling hydrogen in EUR

See also [FlexibilityTrader](./FlexibilityTrader.md), [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md), and [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md)

# Contracts

* MarketForecaster: Send requests, get electricity price forecasts, send bids forecasts
* FuelsMarket: Send requests, get hydrogen price forecasts, offer produced hydrogen
* DayAheadMarket: Send bids, get awards
* HydrogenSupportProvider: Sends SupportInfoRequest, SupportPayoutRequest and receives SupportInfo, SupportPayout

# Available Products

See [FlexibilityTrader](./FlexibilityTrader.md), [FuelsTrader](../Abilities/FuelsTrader.md), and [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md)

# Submodules

* [Electrolyzer](../Modules/Electrolyzer.md)
* [BidSchedule](../Modules/BidSchedule.md)
* [Strategist](../Modules/ElectrolyzerStrategist.md)

See also [FlexibilityTrader](./FlexibilityTrader.md)

# Messages

* [PointInTime](../Comms/PointInTime.md): `MeritOrderForecastRequest` or `PriceForecastRequest` sent to MarketForecaster
* [ClearingTimes](../Comms/ClearingTimes.md): `HydrogenPriceForecastRequest` sent to FuelsMarket
* [MeritOrderMessage](../Comms/MeritOrderMessage.md): received `MeritOrderForecast` from MeritOrderForecaster
* [AmountAtTime](../Comms/AmountAtTime.md): received `PriceForecast` from PriceForecaster, sent `HydrogenOffer` to FuelsMarket, received `FuelsReward` from FuelsMarket
* [FuelCost](../Comms/FuelCost.md): received `HydrogenPriceForecast` from FuelsMarket

See also [FlexibilityTrader](./FlexibilityTrader.md), [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md)

# See also

* [ElectrolysisTraderSensitivity](./ElectrolysisTraderSensitivity.md)
* [FlexibilityTrader](./FlexibilityTrader.md)
* [FuelsMarket](./FuelsMarket.md)
* [FuelsTrader](../Abilities/FuelsTrader.md)
* [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md)
* [GreenHydrogenTraderMonthly](./GreenHydrogenTraderMonthly.md)
* [HydrogenSupportPolicy](./HydrogenSupportPolicy.md)
* [HydrogenSupportClient](../Abilities/HydrogenSupportClient.md)