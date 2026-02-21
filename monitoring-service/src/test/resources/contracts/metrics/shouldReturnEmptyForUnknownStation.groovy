package contracts.metrics

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "should return empty list for unknown station"
    description "Returns an empty list when no metrics exist for the given station ID"

    request {
        method GET()
        url "/api/v1/metrics/station/99999"
        headers {
            header("X-User-Name", "testuser")
            header("X-User-Role", "ADMIN")
        }
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([])
    }
}
