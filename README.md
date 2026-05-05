<!-- SPDX-FileCopyrightText: 2021-2025 German Aerospace Center <amiris@dlr.de>

SPDX-License-Identifier: Apache-2.0 -->
![AMIRIS_Logo](./docs/uploads/AMIRIS_LogoWTitle.png)

## _Simulate electricity markets emerging from interactions of producers, consumers, and flexibilities_

[![Pipeline status](https://gitlab.com/dlr-ve/esy/amiris/amiris/badges/main/pipeline.svg)](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/commits/main)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/tree/main/LICENSE.txt)
[![JOSS paper](https://joss.theoj.org/papers/10.21105/joss.05041/status.svg)](https://joss.theoj.org/papers/10.21105/joss.05041)
[![Zenodo](https://img.shields.io/badge/Research-Zenodo-blue)](https://zenodo.org/communities/amiris)
[![Last Commit](https://img.shields.io/gitlab/last-commit/dlr-ve/esy/amiris/amiris)](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/commits/dev)

AMIRIS is an agent-based simulation model of electricity markets and their actors.
Check out its [full documentation](./docs/home.md).

**[Get started right away](./docs/Get-Started.md)**!

## What is AMIRIS?

AMIRIS enables you to assess the interplay between actors, market designs, and policy instruments for diverse electricity market scenarios.
You can freely select and combine scopes in AMIRIS due to its versatile implementation of agents.
For example, you may model large-scale transnational energy systems and small-scale decisions of individual households at the same time.
Whatever you do: AMIRIS will deliver results quickly thanks to the powerful framework [FAME](https://gitlab.com/fame-framework/wiki/-/wikis/home).
The typical **runtime is less than a minute** when simulating a market zone in hourly resolution for one year.

The figure below illustrates the available prototypical agents as well as associated flows of information, energy, and money.

![AMIRIS Model Schema](./docs/uploads/AMIRIS_ModellSchema_CCBY4.png)

## Who is AMIRIS for?

AMIRIS is intended for informed users in the energy domain, such as researchers, companies, and students.
Although not strictly necessary, [Basic Knowledge of Electricity Markets](https://en.wikipedia.org/wiki/Electricity_market#Wholesale_electricity_market) is helpful.

## Applications

The development of AMIRIS started in 2008.
Since then, AMIRIS has seen many different applications.
Selected recent applications:

- AMIRIS was used to assess **storage profitability** in renewable-dominated energy scenarios. [Preprint](https://dx.doi.org/10.2139/ssrn.5320926 )
- A novel approach to account for **competing flexibility options** was backtested on open [historical data](https://zenodo.org/records/16978510). [Preprint](https://zenodo.org/records/17087877)
- In [TradeRES](https://traderes.eu/) AMIRIS was used to analyse cost recovery of renewables in ~100% renewable electricity systems under different **Energy Policy Instruments**. [Paper](https://doi.org/10.1109/EEM60825.2024.10608886)
- Intertwined dynamics between **Energy Community Markets** and national electricity markets were analysed with AMIRIS. [Paper](https://doi.org/10.1016/j.egyr.2024.06.052)
- Interactions of **Household Flexibility** (heat pumps, electric vehicles, and electricity storage units) with the German energy system were modelled with AMIRIS. [Paper](https://elib.dlr.de/207802)
- In project VERMEER **Impacts of Cold Dunkelflaute** events on the European electricity system were investigated using **Market Coupling** in AMIRIS. [Paper](https://zenodo.org/records/10561382) [Report](https://elib.dlr.de/196641/)
- AMIRIS was used to assess the **Economic Potential of Large Flexibility Providers** in future electricity market scenarios. [Paper](https://doi.org/10.1016/j.est.2024.110959)
- **Monetary Saving Potentials of Load Shifting** were analysed using AMIRIS. [Dissertation](https://depositonce.tu-berlin.de/items/4a364bac-9e97-4d35-8eb6-645824cfc02d)
- A wide range of scenarios for **Future Electricity Markets** was explored using an AMIRIS [scenario generator](https://doi.org/10.5281/zenodo.8382789). [Paper](https://doi.org/10.1016/j.egyr.2024.11.013)

## Non-Applications

AMIRIS follows an explorative approach: It is well suited to investigate the effects arising from the interactions of energy system actors under a given set of assumptions.
While individual actors can optimise their decisions, AMIRIS **does not optimise the energy system** as a whole.
Further, AMIRIS **does not enforce system-wide constraints** like a carbon emission cap.
Thus, we recommend using optimisation-based tools like [REMix](https://gitlab.com/dlr-ve/esy/remix/framework), [PyPSA](https://pypsa.org/) or [oemof](https://oemof.org/) to answer questions like "What is the cheapest electricity system given a carbon emission cap of X?".

## Community

AMIRIS is mainly developed by the German Aerospace Center, Institute of Networked Energy Systems.
We provide multi-level support for AMIRIS users as listed on our dedicated [Support Page](./docs/Community/Support.md).
**We welcome all contributions**: bug reports, feature requests, documentation enhancements, and code.
Please see our [Contribution Guidelines](./docs/Community/Contribute.md).

## Citing AMIRIS

If you use AMIRIS in an academic context please cite [doi: 10.21105/joss.05041](https://doi.org/10.21105/joss.05041).
In other contexts, please include a link to our [Gitlab repository](https://gitlab.com/dlr-ve/esy/amiris/amiris).

```
@article{schimeczek2023,
	title = {{AMIRIS}: {Agent}-based {Market} model for the {Investigation} of {Renewable} and {Integrated} energy {Systems}},
	volume = {8},
	doi = {10.21105/joss.05041},
	number = {84},
	journal = {Journal of Open Source Software},
	author = {Schimeczek, Christoph and Nienhaus, Kristina and Frey, Ulrich and Sperber, Evelyn and Sarfarazi, Seyedfarzad and Nitsch, Felix and Kochems, Johannes and Ghazi, A. Achraf El},
	year = {2023},
	pages = {5041},
}
```

## Acknowledgements

The development of AMIRIS was funded by the German Aerospace Center, the German Federal Ministry for Economic Affairs and Energy, the German Federal Ministry of Research, Technology and Space, and the German Federal Ministry for the Environment, Climate Action, Nature Conservation and Nuclear Safety.
It has also received funding from the European Union’s Horizon 2020 research and innovation programme under grant agreement No 864276.
Furthermore, it was funded by CETPartnership, the European Partnership under Joint Call 2022 for research proposals, co-funded by the European Commission (grant agreement No 101069750) and with funding organisations listed on the [CETPartnership website](https://cetpartnership.eu/calls/funded-project/man0euvre).
An extended list of third-party funded research projects can be found on the [AMIRIS Home Page](https://helmholtz.software/software/amiris).
We express our gratitude to all [contributors](CONTRIBUTING.md#list-of-contributors).

## What next?

* [Install and run AMIRIS](./docs/Get-Started.md)
* [Read previous publications & material](https://zenodo.org/communities/amiris)
* [Ask questions](https://forum.openmod.org/tag/amiris)
* [Contribute an issue or code](./docs/Community/Contribute.md)
