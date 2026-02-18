# 42 words

A [DamForecastProvider](../Abilities/DamForecastProvider.md) that provides day-ahead market (DAM) forecasts to other agents.
It uses bids from "inflexible" agents for future hours and performs own market clearing.
To have meaningful forecasts, all other agents need to actually bid at the DAM with their intended bids given earlier to Forecaster.

# Details

## Forecasting
For a perfect foresight, the MarketForecaster needs the intended bids for future hours from **all** market participants (i.e. [Traders](./Trader.md)).
The Traders need to ensure, that the bids they send to the MarketForecaster match their actual bid provided to the exchange at a later time.
To obtain these bid forecasts, the MarketForecaster requests them at the Traders.
This request includes a list of TimeStamps of the hour(s) to be forecasted in a [ClearingTime](../Comms/ClearingTimes.md) message.
Traders forward these requests to connected [PowerPlantOperators](./PowerPlantOperator.md), if necessary.

The PowerPlantOperators send forecast requests to [CarbonMarket](./CarbonMarket.md) and [FuelsMarket](./FuelsMarket.md) and, once they have a response, create a forecast for their [MarginalCost](../Modules/Marginal.md) and send it to their Trader.

The MarketForecaster has a foresight interval to be specified in its parameters.
Clients may request forecasts for any time steps between the current time and (current time + foresight interval).
If the requested time is not available (out of foresight interval, or not matching full hours) an exception is thrown.
At the beginning of the simulation, the MarketForecaster fills its (yet empty) container of forecasts for the next X hours within the foresight interval.
In every subsequent simulation interval, only missing forecasts are added, and out-of-date forecasts are removed.

## Market Coupling

In case multiple coupled DAMs are to be forecasted, `MarketForecaster` can also forecast results from coupled market zones.
To this end, it needs to digest a [TransmissionCapacities](../Comms/TransmissionCapacitySeries.md) message sent from the connected [DayAheadMarketMultiZone](./DayAheadMarketMultiZone.md) whose results `MarketForecaster` is trying to forecast.
If no such message is received, or no transmissions are specified therein, `MarketForecaster` will not consider market coupling.

In case transmission capacities to other markets are defined, `MarketForecaster` will contact [MarketCoupling](./MarketCoupling.md) to also consider neighbouring market zones in its forecast.
To achieve this, `MarketForecaster` implements the [MarketCouplingClient](../Abilities/MarketCouplingClient.md) interface.

## History

### 4.1

`MarketForecaster` becomes a [MarketCouplingClient](../Abilities/MarketCouplingClient.md).
It can thus consider market coupling within its forecasts.

### v3.6

`MarketForecaster` was formerly an abstract class, but can now be instantiated.
`MarketForecaster` can send out both `MeritOrderForecast`s and `PriceForecast`, at the same time to different clients.
Thus, a single `MarketForecaster` (or any of its child classes) is sufficient to handle multiple different types of forecasts.

# Dependencies

* All [Traders](./Trader.md)
* [MarketCoupling](./MarketCoupling.md) in case coupled market zones are to be considered.

# Input from file

* `ForecastPeriodInHours` number of hours to the future at which the forecast is available
* `Clearing` see [MarketClearing](../Modules/MarketClearing.md)

# Input from environment

* TransmissionCapacities from connected DayAheadMarket
* GateClosureInfo from connected DayAheadMarket
* Demand and Supply Bids from all (inflexible) Traders
* MarketCouplingForecastResult from MarketCoupling

# Simulation outputs

* `AwardedEnergyForecastInMWH`: Forecasted total awarded energy in MWh
* `ElectricityPriceForecastInEURperMWH`: Forecasted electricity price in EUR per MWh

# Contracts

* from DayAheadMarket: receive TransmissionCapacities and GateClosureInfo
* to Trader: send ForecastRequest
* from (inflexible) Trader: receive BidsForecast
* to MarketCoupling: send TransmissionAndBidForecasts
* from MarketCoupling: receive MarketCouplingForecastResult
* from (flexible) Trader: receive PriceForecastRequest and / or MeritOrderForecastRequest
* to (flexible) Trader: send PriceForecast and / or MeritOrderForecast

# Available Products

* `ForecastRequest` Request to start calculation of BidsForecasts

see also [DamForecastProvider](../Abilities/DamForecastProvider.md) and [MarketCouplingClient](../Abilities/MarketCouplingClient.md)

# Submodules

* [MarketClearingResult](../Modules/MarketClearingResult.md)
* [MarketClearing](../Modules/MarketClearing.md)

# Messages

* [TransmissionCapacitySeries](../Comms/TransmissionCapacitySeries.md) received as `TransmissionCapacities` from DayAheadMarket
* [ClearingTime](../Comms/ClearingTimes.md) received from DayAheadMarket as `GateClosureInfo` and  forwarded as `ForecastRequest` to connected Traders
* [BidsAtTime](../Comms/BidsAtTime.md) received from connected Traders as their `BidsForecast`
* [CouplingData](../Comms/CouplingData.md) sent as `TransmissionAndBidForecasts` to MarketCoupling and received from MarketCoupling as `MarketCouplingForecastResult`
* [MeritOrderMessage](../Comms/MeritOrderMessage.md) as `MeritOrderForecast`
* [AmountAtTime](../Comms/AmountAtTime.md) as `PriceForecast`

# See also

* [DamForecastProvider](../Abilities/DamForecastProvider.md)
* [PriceForecaster](./PriceForecaster.md)
* [PriceForecasterAPI](./PriceForecasterApi.md)
* [MeritOrderForecaster](./MeritOrderForecaster.md)
* [SensitivityForecaster](./SensitivityForecaster.md)
* [MarketCouplingClient](../Abilities/MarketCouplingClient.md)
* [MarketCoupling](./MarketCoupling.md)
* [DayAheadMarket](./DayAheadMarket.md)