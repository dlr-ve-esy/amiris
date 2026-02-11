# 42 words

`DayAheadMarketMultiZone` (DAMMZ) is a type of [DayAheadMarket](./DayAheadMarket.md) which is connected to a [MarketCoupling](./MarketCoupling.md) agent.
It digests the requested and offered energy from connected [DayAheadMarketTraders](../Abilities/DayAheadMarketTrader.md) and forwards the bids together with transmission capacities to the `MarketCoupling`.
Based on the optimised transfers between market zones, `DayAheadMarketMultiZone` clears its own market.
The award of the clearing is sent back to the `DayAheadMarketTraders`.

# Details

Based on the received [Bids](../Comms/BidsAtTime.md) from the DayAheadMarketTraders, DAMMZ fills the [Demand & Supply book](../Modules/OrderBook.md).
The books are sent to the market coupling agent where all linked markets are cleared together.
After the coupling results are received, the total amount of awarded supply and demand energy is calculated and sent out as [Award](../Comms/AwardData.md) message for each contracted Agent.
If an agent did not place a bid but is contracted for an award, the awarded energy will be Zero.
To enable the [MarketForecaster](./MarketForecaster.md) to also consider market coupling, it can forward [TransmissionCapacities](../Comms/TransmissionCapacitySeries.md) to the `MarketForecaster`.

# Dependencies

* [DayAheadMarket](./DayAheadMarket.md): Parent class defining sending of GateClosureInfos
* [DayAheadMarketTraders](../Abilities/DayAheadMarketTrader.md): to send their supply and demand bids
* [MarketCouplingClient](../Abilities/MarketCouplingClient.md): to interact with `MarketCoupling`

# Input from file

Additional fields to the ones in [DayAheadMarket](./DayAheadMarket.md):

* `MarketZone`: Identifier specifying the market zone this DayAheadMarket is representing
* `Transmission`: a list of transmission capacities towards connected market zones, each specifying
  * `MarketZone`: Connected Market zone that can be supplied with additional energy
  * `CapacityInMW`: Net transfer capacity of supply from own to connected market zone

# Input from environment

* Bids from DayAheadMarketTraders
* Bid transfers from MarketCoupling

# Simulation outputs

Additional to the outputs as in [DayAheadMarket](./DayAheadMarket.md):

* `PreCouplingElectricityPriceInEURperMWH`: electricity price which would occur without any market coupling in EUR/MWh
* `PreCouplingTotalAwardedPowerInMW`: awarded power without any market coupling in MW
* `PreCouplingDispatchSystemCostInEUR`: system cost without any market coupling in EUR
* `AwardedNetEnergyFromImportInMWH`: net awarded power from imports in MWh
* `AwardedNetEnergyToExportInMWH`: net awarded energy to export in MWh

# Contracts

* DAMZ forward transmission capacity info to `MarketForecaster`
* `DayAheadMarketTraders` send their BidsAtTime to DAMZ
* DAMZ send TransmissionAndBids to `MarketCoupling`
* DAMZ receive MarketCouplingResult from `MarketCoupling`
* DAMZ sends receive AwardData to `DayAheadMarketTraders`

see also [DayAheadMarket](./DayAheadMarket.md)

# Available Products

* `TransmissionAndBids` Transmission capacities and bids from local exchange

see also [DayAheadMarket](./DayAheadMarket.md) and [MarketCouplingClient](../Abilities/MarketCouplingClient.md)

# Submodules

* [MarketClearing](../Modules/MarketClearing.md)
* [MeritOrderKernel](../Modules/MeritOrderKernel.md)
* [OrderBook](../Modules/OrderBook.md)
* [MarketClearingResult](../Modules/MarketClearingResult.md)

# Messages

* [AwardData](../Comms/AwardData.md) sent out
* [BidsAtTime](../Comms/BidsAtTime.md) received

see also [DayAheadMarket](./DayAheadMarket.md)

# See also

* [DayAheadMarket](./DayAheadMarket.md)
* [MarketCouplingClient](../Abilities/MarketCouplingClient.md)
* [DayAheadMarketTrader](../Abilities/DayAheadMarketTrader.md)
* [MarketCoupling](./MarketCoupling.md)
* [MarketForecaster](./MarketForecaster.md)