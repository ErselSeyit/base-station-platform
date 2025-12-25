package contracts.stations

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "should return all base stations"
    description "Returns a list of all base stations"
    
    request {
        method GET()
        url "/api/v1/stations"
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
            [
                id: $(anyNumber()),
                stationName: $(regex('[A-Z]{2}-[0-9]{3}')),
                location: $(anyNonBlankString()),
                latitude: $(anyDouble()),
                longitude: $(anyDouble()),
                stationType: $(regex('MACRO_CELL|MICRO_CELL|SMALL_CELL|FEMTO_CELL|PICO_CELL')),
                status: $(regex('ACTIVE|INACTIVE|MAINTENANCE|OFFLINE|ERROR'))
            ]
        ])
    }
}

