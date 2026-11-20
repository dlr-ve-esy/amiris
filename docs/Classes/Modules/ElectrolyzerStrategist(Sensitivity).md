# In Short

`ElectrolyzerStrategist` is an abstract base class hosting common tasks for `Electrolyzer` strategists.
It extends the basic [Strategist(Electrolysis)](./Strategist(Electrolysis).md) class.
It has one concrete child class:

* [ProductionTarget](./ProductionTarget.md) - Determines an optimal dispatch strategy, thereby seeking to fulfill a hydrogen production target while considering sensitivity forecasts

# Details

ElectrolyzerStrategist create [BidSchedules](./BidSchedule.md) for a specific electrolyzer unit using sensitivity-based electricity price as well as hydrogen price forecasts.
The ElectrolyzerStrategist hereby accounts for optional support payments for the produced hydrogen from [HydrogenSupportPolicy](../Agents/HydrogenSupportPolicy.md) as these effect opportunity costs of hydrogen production.
The extension of its parent [Strategist(Electrolysis)](./Strategist(Electrolysis).md) comprises storing hydrogen price forecasts and initialising the specific strategies.
ElectrolyzerStrategist should be hosted and used by an [ElectrolysisTraderSensitivity](../Agents/ElectrolysisTraderSensitivity.md) agent.

## Input

* `StrategistType` defines which type of strategist to use
* `ProductionTarget` Group, defined in [ProductionTarget](./ProductionTarget.md)
* `PlanningHorizonInHours` 
* `Type` strategist type; allowed values are `PRODUCTION_TARGET` for a [ProductionTarget](./ProductionTarget.md) strategist and `GREEN_HYDROGEN_MONTHLY` (not implemented for sensitivities, yet)

# Child classes

* [ProductionTarget](./ProductionTarget.md)

# See also

* [Electrolyzer](Electrolyzer.md)
* [ElectrolysisTraderSensitivity](../Agents/ElectrolysisTraderSensitivity.md)
* [Strategist(Electrolysis)](./Strategist(Electrolysis).md)
* [HydrogenSupportPolicy](../Agents/HydrogenSupportPolicy.md)