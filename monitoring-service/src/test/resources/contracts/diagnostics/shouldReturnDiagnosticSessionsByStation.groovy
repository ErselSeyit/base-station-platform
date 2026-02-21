package contracts.diagnostics

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "should return diagnostic sessions for a station"
    description "Returns a list of diagnostic sessions for a given station ID"

    request {
        method GET()
        url "/api/v1/diagnostics/station/1"
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
        body([
            [
                id: $(anyNonBlankString()),
                stationId: 1,
                category: $(regex('hardware|network|power|software')),
                severity: $(regex('low|medium|high|critical')),
                problemCode: $(anyNonBlankString()),
                message: $(anyNonBlankString()),
                status: $(regex('DETECTED|DIAGNOSED|APPLIED|PENDING_CONFIRMATION|RESOLVED|FAILED'))
            ]
        ])
    }
}
