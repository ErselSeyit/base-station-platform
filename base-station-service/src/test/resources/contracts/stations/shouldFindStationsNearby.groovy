package contracts.stations

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "should find stations within radius"
    description "Returns stations within a given radius using Haversine distance"
    
    request {
        method GET()
        url("/api/v1/stations/search/nearby") {
            queryParameters {
                parameter "lat": "40.7580"
                parameter "lon": "-73.9855"
                parameter "radiusKm": "10"
            }
        }
        headers {
            contentType applicationJson()
        }
    }
    
    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        // Returns array of stations - test data seeds station at these coords
        body([
            [
                id: $(anyNumber()),
                stationName: $(anyNonBlankString()),
                location: $(anyNonBlankString()),
                latitude: $(anyDouble()),
                longitude: $(anyDouble()),
                stationType: $(regex('MACRO_CELL|MICRO_CELL|SMALL_CELL|FEMTO_CELL|PICO_CELL')),
                status: $(regex('ACTIVE|INACTIVE|MAINTENANCE|OFFLINE|ERROR'))
            ]
        ])
    }
}

