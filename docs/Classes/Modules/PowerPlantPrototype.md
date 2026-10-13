# In short

The `PowerPlantPrototype` comprises basic data of conventional power plants and is used in [PowerPlants](./PowerPlant.md) and [Portfolios](./Portfolio.md).

# In detail

Covered parameters are:

- cycling costs in Euro per MW,
- specific CO2-emissions for consumed thermal energy in tons per thermal MW,
- fuel type (e.g. hardcoal, lignite),
- capacity outages
- variable costs in Euro per MWh, and
- must-run capacities

# Input

`PowerPlantPrototype` also defines a ParameterTree of input parameters.
This set can be used in associated Agents to assemble their set of input parameters.
Required inputs are:

* `FuelType` The different fuel types like coal, oil, etc.
* `OpexVarInEURperMWH` variable cost of the respective power plant technology.
* `CyclingCostInEURperMW` Additional cycling costs for conventional power plants in Euro per Megawatt, i.e. costs due to plant start up
* `SpecificCo2EmissionsInTperMWH` specific CO2-emissions in tons per thermal MWh
* `OutageFactor` share of installed capacity that is not available for electricity production at a given time.
* `MustRunFactor` share of the total installed capacity that must run at a given time due to, e.g., heat contracts, or reserve power obligations; the must-run factor will be capped at the maximum available capacity after deducting the outage factor.
