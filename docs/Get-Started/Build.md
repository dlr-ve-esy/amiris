# AMIRIS Build Guide

If you modify code of AMIRIS you will also need to package the application.
This guide explains how to do that, either using Eclipse or a console with Maven.

### Using Eclipse

Please have a look at our [AMIRIS-with-Eclipse Guide](./WithEclipse.md) or the detailed [Step-by-step installation](./Instructions-with-screenshots.md) instructions.

### Using Maven

AMIRIS has several dependencies, all of which are available via the prominent [Apache Maven](https://maven.apache.org/) build tool.
Most development environments for Java (e.g. Eclipse) offer an integrated version Apache Maven - thus you do not have to install it.
In this manual, however, we are not using an IDE but use the command line version of Maven.
If you haven't got Maven installed, follow these instructions of the [Maven Installation](./MavenInstallation.md).

To build AMIRIS you require all of its source code.
If you haven't done so yet, download or check out the [AMIRIS](https://gitlab.com/dlr-ve/esy/amiris/amiris) project, enter its directory and package AMIRIS:

* `git clone https://gitlab.com/dlr-ve/esy/amiris/amiris.git`
* `cd amiris`
* `mvn package`

Wait for Maven to fetch all dependencies and to build AMIRIS.
This creates a Java archive (JAR) file in the `target/` folder that includes AMIRIS and all of its dependencies.
The file should be named `amiris-core_x.y.z-jar-with-dependencies.jar` by default, where x.y.z is the current version of AMIRIS in the `pom.xml` file.
You need to *re-run* `mvn package` whenever you *change the code-base* of AMIRIS.

## Run AMIRIS in project folder

You probably already checked out the AMIRIS-Examples in the previous steps.
However, in the AMIRIS project there is a dedicated folder for the inputs - called "input".
So, to have the input files there, either check out the Examples again to that "input" folder, or, move the previously downloaded Examples there.

Then, compile the input binary files with FAME-Io.
See the dedicated [AMIRIS with FAME-Io](./FameioSetup.md) guide to learn how this is done.

Finally, run your newly built AMIRIS:

* change directory to the base folder of your AMIRIS project
* adjust the paths in your console command to:

```
java -jar "target/amiris-core_x.y.z-jar-with-dependencies.jar" -f input/examples/backtest/Germany2019/config.pb
```

where

* `jar-with-dependencies` now points to the "target" folder
* `config.pb` is the moved or re-created input file from the AMIRIS-Examples

Also, in the AMIRIS project, the configuration of FAME-Core needs a few adjustments.
The FAME-Core configuration is specified in the file `fameSetup.yaml`. 

Output files

* have time stamps in their name
* are written to the folder "result"
* are named "AMIRIS."

## Verify AMIRIS is working as expected

Well, you got AMIRIS running, but is it running properly?
You can use AMIRIS-Py to compare results of two runs of AMIRIS.

### Configuration

We assume you have

* activated a Python environment with [AMIRIS-Py](https://gitlab.com/dlr-ve/esy/amiris/amiris-py/-/blob/main/README.md),
* installed the latest version of AMIRIS along with its examples using `amiris install`,
* your current working directory is the folder you installed AMIRIS to.

### Verification

To verify AMIRIS is working properly follow these steps:

* enable complex model outputs:
    * create or modify `fameSetup.yaml`:  set `output-complex: True`
* run the "Simple" scenario file:
    * `amiris run -j amiris-core_x.y.z-jar-with-dependencies -s examples/demo/Simple/scenario.yaml -o simple`
    * here "x.y.z" denotes your installed version of AMIRIS
    * this will create a new subfolder named "simple" in the current directory with the results of the Simple scenario
* download the reference result from AMIRIS main branch
    * download and extract the main branch of AMIRIS from its [repository](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/tree/main)
* compare your results with the reference results:
    * `amiris compare -e <Path/to/amiris-main>/result/AMIRIS_Simple_Reference/ -t ./Simple/`
    * where " <Path/to/amiris-main>" refers to the path you extracted the AMIRIS main branch to

Expected output should look like this:

```
    PASS: Found no significant differences for any expected pair of files.
```
