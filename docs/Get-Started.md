Instructions on how to set up, execute and modify AMIRIS

# Requirements

## Technical

AMIRIS is a *JAVA* application configured via *Python* scripts.

To run AMIRIS **Python 3.9 or higher** and Java Development Kit **JDK 11 or higher** are required.
The latter can be obtained from e.g. [here](https://adoptium.net/).

## Skills

To configure and **run AMIRIS** applications, **no programming skills** are required.
Experience with energy system modelling and **Python is helpful**.
However, if you want to modify or extend the functionality of AMIRIS, you should have at least a basic understanding of Java.
To design new agents and interactions from scratch a basic understanding of [FAME](https://gitlab.com/fame-framework/wiki/-/wikis/home) is also required.

## Tools

These tools are recommended when working with AMIRIS:

| Action                 | Format    | Tools                                       | Level                 |
|------------------------|-----------|---------------------------------------------|-----------------------|
| Run AMIRIS             | command   | command line (e.g. bash, powershell)        | user                  |
| Inspect AMIRIS results | CSV       | spreadsheet application (e.g. Excel, Calc)  | user                  |
| Configure AMIRIS       | YAML, CSV | text editor (e.g. Notepad++, NodepadNext)   | user                  |
| New agents / logic     | Java      | Java IDE (e.g. Eclipse, Intellij, VSCode)   | programmer            |
| Multi-Core Mode        | command   | Docker (or Linux, openMPI, Maven, git, gcc) | programmer (or admin) |

# Install and Run AMIRIS

After you have met the technical requirements as described above, **choose one of the following** installation guides for AMIRIS: 

- **[Quickstart Guide](./Get-Started/QuickStart.md)**: Get your first simulation result in less than 5 minutes using `amirispy`.
- **[Step-by-Step Guide](./Get-Started/StepByStep.md)**: Follow this guide if you want additional explanations on environment setup.
- **[All-the-Details Guide](Get-Started/FameioSetup.md)**: Gain full control over all model execution parameters by using the `fameio` package instead of the `amirispy` wrapper. Not recommended for beginners!

# Results

Congratulations, you successfully ran your first simulation with AMIRIS! Check the [Result Page](./Get-Started/Results.md) to learn more about the results you just generated.

# Run your first real-world simulation

The [`Simple`](https://gitlab.com/dlr-ve/esy/amiris/examples/-/tree/dev/demo/Simple) scenario example is intended to achieve a fast setup - it is not based on a real-world energy system.
In the [backtest](https://gitlab.com/dlr-ve/esy/amiris/examples/-/tree/dev/backtest) folder you find multiple scenarios to investigate market dynamics closer to a real-world electricity system.
Run another simulation and check the results.

# Experiment

You may ask yourself, "how would a higher carbon price impact market dynamics"? 
To find out, navigate to the example that you would like to modify, e.g., in the folder `backtest/Germany2018/`.
Open the file `MarketsAndForecast.yaml` in the subfolder `agents`.
Search for the agent `CarbonMarket` and replace the `Co2Prices` with your value, for example

```
Co2Prices: 200
```

Rerun the simulation and observe the impact of your changes on the electricity prices.

Please also refer to the [FAME-Wiki](https://gitlab.com/fame-framework/wiki/-/wikis) when applying more advanced adaptations to your scenario, such as changing the [simulation duration](https://gitlab.com/fame-framework/wiki/-/wikis/GetStarted/core/Contracts).

# Build AMIRIS

So far you only ran the AMIRIS model as it is provided.
If you were to modify code and change agent logic of AMIRIS, you would also need to package the application.
See the [AMIRIS Build Guide](./Get-Started/Build.md) for instructions.

# Multi-Core Mode

AMIRIS, as any FAME application, can be run using a single process, or in parallel mode with multiple processes.
By default, AMIRIS runs in single process mode.
Most configurations execute quite fast (depending on your machine, of course) even with only a single process.
Running any of the AMIRIS-Examples takes only a few seconds on a standard desktop computer.

However, if your simulations take too long, you might want to run AMIRIS in parallel mode.
Since version v4.2, AMIRIS jar files are prepared to be used together with an OpenMPI installation.

For this you need a system with [Open-MPI](https://www.open-mpi.org/) that has been compiled with the java-dependency in mind, see the [FAME-Wiki](https://gitlab.com/fame-framework/wiki/-/wikis/GetStarted/parallel/RunParallel).
Since this is platform-dependent it requires a manual compilation.
Achieving this on your system, however, is not an easy task.

Alternatively, you can employ this [Docker image](https://gitlab.com/fame-framework/fame-mpi-facade/container_registry) which provides you with an OpenMPI installation that is java-enabled and works hand-in-hand with the provided AMIRIS jar files.
