# In short

A converter that produces hydrogen using electricity.
Ramping restrictions are not considered.

# Details

Electrolyzer comprises several routines to convert electric energy to hydrogen considering limited conversion capacities and conversion efficiencies. 

# Input from file

Electrolyzer requires the following inputs:
* `PeakConsumptionInMW` time series; peak electric power consumption used for conversion to hydrogen
* `ConversionFactor` constant < 1; defines the efficiency of energy conversion from electric energy to hydrogen's thermal energy equivalent
* `VariableCostInEURperMWH` time series; defines the variable cost of an electrolyzer in EUR per MWh electricity

# See also

* [Strategist](./ElectrolyzerStrategist.md)
* [ElectrolysisTrader](../Agents/ElectrolysisTrader.md)
* [ElectrolysisTraderSensitivity](../Agents/ElectrolysisTraderSensitivity.md)