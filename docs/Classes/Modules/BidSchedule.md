# In short

Represents a charge and discharge schedule for flexibility options, e.g. storage system [Devices](./Device.md) or [Generic Devices](./GenericDevice.md).


# Details

A BidSchedule is valid for a certain time frame only.
This time frame and its individual time periods are calculated based on a TimeStamp marking the beginning of the first period (`timeOfFirstElement`), a count of (similar) time periods covered by this BidSchedule (`durationInPeriods`) and a TimeSpan of each of the covered time periods (`period`).

BidSchedule contains 4 arrays covering all periods of the time frame in which the BidSchedule is valid.
* `requestedEnergyPerPeriodInMWH`: energy amount to be traded at electricity market - positive values: purchasing, negative values: selling
* `biddingPricePerPeriodInEURperMWH`: bidding price of the buy / sell offers in each period
* `expectedInitialInternalEnergyPerPeriodInMWH`: In case the actual energy content of a device for which this BidSchedule was created does not match the expected one in that time period, following this schedule can lead to bids that cannot be implemented physically (i.e. the storage might need to charge beyond being "full" or discharge though being depleted). Thus, each BidSchedule can check if the expected energy level of the connected device matches the given one (method "scheduleIsViable").
* `expectedElectricityPricesInEURperMWH` expected electricity prices based on forecast and flexibility dispatch

# See also

* [Device](./Device.md)
* [GenericDevice](./GenericDevice.md)

# History

# v4.1

`BidSchedule` gained the optional array `expectedElectricityPricesInEURperMWH`.

## v3.5

This class was renamed from `DispatchSchedule` to `BidSchedule`.