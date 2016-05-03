# Industrial Robot Cycle Store Service
A service that listens to low level events from an industrial robot regarding cycle start and stops as well as program
pointer change events from the service *robot-isWaiting-service*. These events are used to store robot cycles into
*Elasticsearch*.

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
