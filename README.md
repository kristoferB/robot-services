# Industrial ABB Robot Service Collection
A set service that listens to low level events from an industrial robot and/or each other and transforms them into useful information suitable for a GUI.

## Quickstart
Setup the correct configurations in the config file under src/main/resources. Then execute
```
sbt
```
to enter sbt.
## Starting the service
```
sbt run
```
## Packaging the service
See the SBT assembly plugin for more info. To package as zip file run
```
sbt universal:packageBin
```
## Running tests
```
sbt clean test
```
