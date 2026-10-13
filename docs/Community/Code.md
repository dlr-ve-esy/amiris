# Code Contributions to AMIRIS Repositories

We highly appreciate your interest in contributing code.
Please note that code contributions (e.g. via pull requests) require you to complete and sign the [Contributor License Agreement](./docs/Community/CLA.pdf), before we can merge your code into the project.

## Definition of Done

A feature can be **closed**, once it has been

- implemented
- tested
- documented in the code, e.g., JavaDoc or DocString
- documented in the docs folder
- reviewed by another team member
- merged to `dev`

## Project Environment

We recommend to use [Eclipse](https://www.eclipse.org/) for AMIRIS development.
[Maven](https://maven.apache.org/) is strongly recommended for resolving the dependencies of AMIRIS.
We recommend Java 11 (64 bit) to build and run AMIRIS.

## Model Architecture

to be added

## Branch Structure

Each repository has two protected branches `main` and `dev`.
*New features* should be developed on a separate branch and are to be merged into `dev`.
*Bug fixes* should be developed in a separate branch and may be merged either into `dev` or `main` if deemed urgent.
Any change to the model code or model data in the `main` branch advances the version (see below) and must be accompanied by a release.
Changes that do not affect the behaviour of the model, e.g., related to documentation, continuous integration, or licence headers, may be released without advancing the version.

## Coding Conventions

Please follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).
The file `CodeStyle.xml` in the `src/test/resources` folder of the repository covers most of Google's specifications.
Correct code formatting and [REUSE compatibility](https://reuse.software/) is enforced by the CI.

## Commit Messages

Inspired by [conventional commits](https://www.conventionalcommits.org/en/v1.0.0) and the [Git Emoji Guide](https://gitmoji.dev/) commit messages should follow this structure:

```
:type-emoji:(scope) description

body
```

where
* `type-emoji` MUST declare the type of commit (see table below),
* `scope` is a MANDATORY noun describing a section of the affected codebase, surrounded by parenthesis,
* `description` is a MANDATORY short summary of the code changes that immediately follows the colon and space after the type/scope prefix,
* `body` MAY be provided after the description providing additional contextual information. The body MUST begin one blank line after the description.

### Emoji-Table

| Commit Type | Emoji              | EmojiCode        | Description                                                        |
|-------------|--------------------|------------------|--------------------------------------------------------------------|
| build       | :wrench:           | `wrench`           | update the build system or external dependencies                   |
| ci          | :rocket:           | `rocket`           | change CI configurations                                           |
| delete      | :fire:             | `fire`             | remove code or files                                              |
| docs        | :memo:             | `memo`             | change the documentation, e.g., README, license headers, changelog |
| feat        | :sparkles:         | `sparkles`         | implement a feature                                                |
| fix         | :bug:              | `bug`              | fix a bug                                                          |
| perf        | :zap:              | `zap`              | improve performance                                                |
| refactor    | :recycle:          | `recycle`          | code changes that neither fix a bug nor add a feature              |
| release     | :bookmark:         | `bookmark`         | release a version                                                  |
| style       | :lipstick:         | `lipstick`         | change style or formatting, not affecting code meaning             |
| test        | :white_check_mark: | `white_check_mark` | add, update or correct tests                                       |

## Pull-request
### Before submitting

Please, check the following points before submitting a pull request:
1. Please complete the [Contributor License Agreement](./docs/Community/CLA.pdf) and send it to [amiris@dlr.de](mailto:amiris@dlr.de).
1. Make sure there is a corresponding issue to your code contribution.
1. Make sure your code is based on the latest version of the *dev* branch and that there are no conflicts. If there are any conflicts, fix them first.
1. Make sure all existing unit tests are successful. Add new unit tests that cover your code contributions and ensure that your code works as intended. If there is a bug you don't know how to fix, ask the main developers for help.
1. Update `CHANGELOG.md` in the main repository to reflect the code changes made. Follow the changelog [style guide](https://github.com/vweevers/common-changelog)
1. Update the project version number in the `pom.xml` following the conventions of [semantic versioning](https://semver.org/) 2.0.0. Attach "-alpha<N>" to versions on the dev-branch, where <N> is an integer increased for each feature branch.
1. Make sure all files are correctly formatted and follow REUSE conventions by running the `spotless` maven plugin.
1. If there is a breaking change introduced: add upgrade instructions to `UPGRADING.md`.

### Submit

1. Submit your request using the provided *pull_request* template & (briefly) describe the changes you made in the pull request.
1. Contact the main developers via [amiris@dlr.de](mailto:amiris@dlr.de) or on the [openMod Forum](https://forum.openmod.org/tag/amiris) in case you've got any questions.

## Versioning

We follow the [semantic versioning 2.0.0 scheme](https://semver.org/) for all AMIRIS Repositories.

## Release

When preparing a release, please use the release template to see a full list of required updates.

Tags of AMIRIS have a name.
The overall theme of naming tags in the AMIRIS group repository is [**Physical Geography**](https://en.wikipedia.org/wiki/Physical_geography).
The following schemes apply for the different projects within the group:

| Project       | Tag name scheme                                                                               |
|---------------|-----------------------------------------------------------------------------------------------|
| amiris        | [Volcanoes](https://www.vulkane.net/blogmobil/welcher-vulkan-eruptiert-aktuell/vulkan-liste/) |
| examples      | n/a                                                                                           |
| amiris-py     | [Rivers](https://en.wikipedia.org/wiki/List_of_alternative_names_for_European_rivers)                       |
| investment    | n/a                                                                                           |
| scengen       | [Landscapes](https://en.wikipedia.org/wiki/Glossary_of_landforms#Landforms,_alphabetic)       |
| priceforecast | [Soil](https://en.wikipedia.org/wiki/Category:Types_of_soil)                                  |

Tags names are to be chosen in alphabetical order (i.e. starting with "A").
For each new major version number (**x**.a.b) the first letter of the tag name is increased (to "B", "C", etc.).
For each new minor revision number (a.**x**.c) the first letter stays the same, but a new scheme-specific name is chosen, increasing in alphabetical order.
Bug fix revisions (a.b.**x**) do not receive an individual name.
