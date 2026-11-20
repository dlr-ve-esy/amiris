# In Short

`Strategist` is a basic abstract class for strategist operating a flexible electrolysis unit, thereby making used of sensitivity-based forecasts.

# Details

Strategist covers storing of forecasts for electricity prices or merit orders and handles basic strategy parameters, such as the schedule duration or forecast periods.
It also offers a routine of how to calculate a new [BidSchedule](./BidSchedule.md).

## Input

* `Dispatch` Group specifying how dispatch planning is performed
  * `Assessment` Group specifying assessment function; see [AssessmentFunctionBuilder](./AssessmentFunctionBuilder.md); Note: Only value `MAX_PROFIT` for a profit-maximising sensitivity-based assessment strategy is allowed, see [MaxProfit](./MaxProfit.md). 
  * `Bidding` Group specifying bidding behaviour
    * `ScheduleDurationInHours` Number of hours each created schedule is viable; should be less than or equal to the forecast period
    * `BidToleranceInEURperMWH` Optional input to define by what amount the offered price shall be lowered(when selling) or increased (when buying) energy based on the dispatch schedule
* `Strategy` Group defining dispatch planning strategy to be applied; see [ElectrolyzerStrategist](./ElectrolyzerStrategist.md)

# Child classes

* [ElectrolyzerStrategist](./ElectrolyzerStrategist.md)

# See also

* [ElectrolyzerStrategist](./ElectrolyzerStrategist.md)
* [AssessmentFunctionBuilder](./AssessmentFunctionBuilder.md)