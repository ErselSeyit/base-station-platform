package contracts.stations

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "should return 404 for non-existent station"
    description "Returns 404 when station ID does not exist"
    
    request {
        method GET()
        url "/api/v1/stations/99999"
        headers {
            contentType applicationJson()
        }
    }
    
    response {
        status NOT_FOUND()
    }
}

