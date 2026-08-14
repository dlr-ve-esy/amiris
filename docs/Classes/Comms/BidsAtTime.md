## In Short

`BidsAtTime` is a Portable sent to [DayAheadMarkets](../Agents/DayAheadMarket.md).
It is a summary of multiple [Bids](../Modules/Bid.md) from one `Trader` for one bidding time period.

## Content

* `deliveryTime` begin of the delivery interval
* `traderUuid` id of the trader that sends these bids
* `supplyBids` bids to be associated with the supply side, i.e. offering electricity
* `demandBids` bids to be associated with the demand side, i.e. requesting electricity

## See also

### Related Classes

* [Bid](../Modules/Bid.md)