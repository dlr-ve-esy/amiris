# In Short

Summarises all needed data from the local exchanges to perform the market coupling and stores its result.
A `CouplingData` object contains: 

* a `TimeStamp` to denote which clearing event this message refers to,
* a `SupplyOrderBook` for supply bids, 
* a `DemandOrderBook` for demand bids, 
* a `TransferOrderBook` for import bids, 
* a `TransferOrderBook` for export bids, and 
* a `TransmissionBook` for transmission capacities.

# See Also

* [OrderBook](../Modules/OrderBook.md)
* [TransferOrderBook](./TransferOrderBook.md)
* [TransmissionBook](../Modules/TransmissionBook.md)