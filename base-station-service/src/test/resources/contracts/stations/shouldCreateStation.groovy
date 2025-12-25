package contracts.stations

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "should create a new base station"
    description "Creates a new base station and returns it with generated ID"
    
    request {
        method POST()
        url "/api/v1/stations"
        headers {
            contentType applicationJson()
        }
        body([
            stationName: "BS-100",
            location: "Test Location",
            latitude: 40.7128,
            longitude: -74.0060,
            stationType: "MACRO_CELL",
            status: "ACTIVE",
            powerConsumption: 1500.0
        ])
    }
    
    response {
        status CREATED()
        headers {
            contentType applicationJson()
        }
        body([
            id: $(anyNumber()),
            stationName: "BS-100",
            location: "Test Location",
            latitude: 40.7128,
            longitude: -74.0060,
            stationType: "MACRO_CELL",
            status: "ACTIVE",
            powerConsumption: 1500.0,
            createdAt: $(anyNonBlankString()),
            updatedAt: $(anyNonBlankString())
        ])
    }
}

