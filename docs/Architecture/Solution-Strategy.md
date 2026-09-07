## Technology decisions

| Goal / Requirement                                                                                               | Solution Strategy |
|------------------------------------------------------------------------------------------------------------------|-------------------|
| We want high performance of the code                                                                             | Use Java          |
| We want do not want to deal with agent scheduling, reading input files, writing output files, or parallelisation | Use FAME          |

## Other decisions

| Goal / Requirement                                                              | Solution Strategy                                                                |
|---------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| We want to be able to easily add capabilities to agents                         | Define Abilities for all agent interactions                                      |
| We do not want to model related market dynamics explicitly                      | Use simplified "markets" to reflect energy carrier prices other than electricity |
| We want users to quickly find out what went wrong in a simulation if it crashes | Provide clear error messages                                                     |
| We want users to be able to execute AMIRIS without building the project         | Provide pre-built jar                                                            |
