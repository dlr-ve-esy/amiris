AMIRIS shall simulate market dynamics of electricity markets.
It is specifically targeted at the energy systems analysis community and shall address its needs.

## Requirements

Users shall be able to configure AMIRIS to incorporate all important market actors, technologies, and policy instruments relevant for the market dynamics of electricity.
This shall include (non-inclusive list):

* electricity production units and their marketing strategies
* electricity consumption and corresponding purchase strategies
* electricity storage, including corresponding purchase and marketing strategies
* cross-zonal markets including transmission restrictions
* policy instruments (i.e. support instruments and legislative restrictions)

## Quality Goals

|Priority|Quality               |Description                                                                                   |Scenarios                                                                                                                                                          |
|--------|----------------------|----------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|1       |Reliability           |The system can maintain a specified level of performance when used under specified conditions.|The results of AMIRIS are deterministic. Simulation results can be accurately reproduced by re-running a simulation.                                             |
|2       |Maintainability       |The system can be modified, corrected, adapted or improved.                                   |AMIRIS can be easily adapted, fixed, and expanded. New agents, markets, components, policy mechanisms can be implemented easily.                                 |
|3       |Operability           |The system can be understood, learned, used and is attractive to users.                       |The configuration files of AMIRIS are clearly structured and intuitive to learn. Input parameters and output variables have expressive names featuring their unit.|
|4       |Performance Efficiency|The system provides appropriate performance relative to the amount of resources used.         |Simulations with AMIRIS can be executed fast on even small desktop computers with one computation core.                                                          |
|5       |Transferability       |System can be transferred from one environment to another.                                    |AMIRIS can easily coupled and combined with other agent-based simulations.                                                                         |

## Stakeholders

|Role / Name                                                                                                                |Expectations                                                          |
|---------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
|Team Energy Economics, Department of Energy Systems Analysis, Institute of Networked Energy System, German Aerospace Center|Team members shall be enabled to contribute to the development of AMIRIS.|
