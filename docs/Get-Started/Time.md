# Time in AMIRIS

AMIRIS is bound to the representation of [time in FAME](https://gitlab.com/fame-framework/wiki/-/wikis/GetStarted/core/Time-in-Fame).
FAME TimeStamps are integers counting seconds since the beginning of the year 2000.
Negative values represent times before that date.
Additional to this integer representation, FAME-Io offers also a more [readable format](https://gitlab.com/fame-framework/fame-io/-/blob/dev/docs/source/input/csv_files.md) for CSV input files and contract times: `YYYY-MM-DD_hh:mm:ss`.

To allow the exchange of timeseries from different years without the need of removing or adding days **leap year are ignored**.
Thus, simulated time assumes 365 days per year, 24 hours per day and 730 hours per month.
Simulated time ignores leap-years, changes due to day-light savings time and uneven month durations.

In leap years, **FAME does not remove February 29th** but instead ends the year on December 30th.
This is done to avoid cuts in the middle of real-world timeseries - the end of a year shows peculiar behaviour anyways.
If you prepare simulation input data, we suggest to follow the same approach.

## Simulation Start Times

Example AMIRIS simulations begin two minutes before the end of the previous year.
This is necessary to have the power plants ready before the first market clearing. In the standard configuration, the power plant park is being built at the end of a year - hence we need to adjust the simulation start accordingly.

## Timeseries, Interpolation, and Forecast

Data for times not provided in input timeseries are interpolated (constant or linear) and extrapolated (constant) by FAME.
In case of extrapolation, a warning is given.
Since forecaster agents in AMIRIS need to provide forecasts also beyond the defined simulation end time (last time step + forecast horizon), we recommend to provide extra data in all input time series to avoid extrapolation warnings at the end of the simulation.