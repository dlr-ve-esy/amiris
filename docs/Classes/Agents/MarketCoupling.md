# In less than 42 words

`MarketCoupling` couples the markets of multiple [`DayAheadMarket`](./DayAheadMarket.md) agents.
It aims at maximising the overall welfare by minimising price spreads among the participating markets by transferring demand bids across these markets while considering restrictions of available transfer capacities.
  
# Details

`MarketCoupling` receives `CouplingData` requests from [](../Abilities/MarketCouplingClient.md).
If those are associated with a forecast clearing, `MarketCoupling` joins messages by their intended clearing time.
Thus, it can process multiple market coupling events in one action.

Using `CouplingData` `MarketCoupling` instantiates a [DemandBalancer](../Modules/DemandBalancer.md) that implements the actual coupling algorithm.
Basically our coupling algorithm guarantees correctness and termination within tolerance parameters, utilising two criteria: 

1. shifting only the minimal-effective-demand from an expensive `DayAheadMarket` to a less expensive one at a time and 
2. processing the most-effective-pair of all possible combinations first.

The minimal-effective-demand is the maximal demand that can be shifted from one market to another without effecting prices for both plus a user-defined energy amount in order to achieve the minimisation of the price delta between both markets.
Finally, `MarketCoupling` returns the updated order books and transmission capacities to its respective `MarketCouplingClient`s.

Further details can be found in [Nitsch and El Ghazi (2024)](https://doi.org/10.5281/zenodo.10561382).

# Dependencies

None

# Input from file

* `MinimumDemandOffsetInMWH` optional offset added to the demand shift that ensures a price change at the involved markets.

# Input from environment

* [MarketCouplingClient](../Abilities/MarketCouplingClient.md) to send `CouplingData` requests and receiving `CouplingData` results

# Simulation outputs

* `AvailableTransferCapacityInMWH`: complex output; the capacity available for transfer between two markets in MWH
* `UsedTransferCapacityInMWH`: complex output; the actual used transfer capacity between two markets in MWH

Simulation outputs are only written out on "actual" market coupling events connected with MarketCouplingResults. 
No output is provided on forecast calculations.
 
# Contracts

* `MarketCouplingClient` receive TransmissionAndBidForecasts send MarketCouplingForecastResult
* `MarketCouplingClient` receive TransmissionAndBids and send MarketCouplingResult

# Available Products

* `MarketCouplingForecastResult`: Result of future market coupling(s)
* `MarketCouplingResult` Result of a coupled market clearing

# Submodules

* [CouplingData](Classes/Comms/CouplingData.md)
* [TransmissionBook](../Modules/TransmissionBook.md)
* [MarketClearingResult](../Modules/MarketClearingResult.md)
* [DemandBalancer](Classes/Comms/DemandBalancer.md)

# Messages

* [CouplingData](Classes/Comms/CouplingData.md) received from `MarketCouplingClient`
* [CouplingData](Classes/Comms/CouplingData.md) sent to `MarketCouplingClient`

# See also
