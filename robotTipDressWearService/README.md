# Industrial Robot Tip Dress Wear Service (TEST)
A service that listens to tid dress RAPID data events from an industrial ABB robot and evaluates whether the cutter needs to be exchanged or not.

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
