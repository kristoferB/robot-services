# Industrial Robot Cycle Store Service
A service that listens to low level events from an industrial robot regarding cycle start and stops as well as RAPID
routine change events from the service *robotRoutineChangeService*. These events are used to store robot cycles into
*Elasticsearch*.