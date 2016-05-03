# Industrial Robot Cycle Store Service
A service that listens to low level events from an industrial robot regarding cycle start and stops as well as program
pointer change events from the service *robotIsWaitingService*. These events are used to store robot cycles into
*Elasticsearch*.