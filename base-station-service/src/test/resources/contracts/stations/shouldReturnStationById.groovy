package contracts.stations

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "should return station by ID"
    description "Returns a single base station when queried by ID"
    
    request {
        method GET()
        url "/api/v1/stations/1"
        headers {
            contentType applicationJson()
        }
    }
    
    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
            id: 1,
            stationName: $(regex('[A-Z]{2}-[0-9]{3}')),
            location: $(anyNonBlankString()),
            latitude: $(anyDouble()),
            longitude: $(anyDouble()),
            stationType: $(regex('MACRO_CELL|MICRO_CELL|SMALL_CELL|FEMTO_CELL|PICO_CELL')),
            status: $(regex('ACTIVE|INACTIVE|MAINTENANCE|OFFLINE|ERROR'))
        ])
    }
}

