package contracts.metrics

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "should return metrics for a station"
    description "Returns a list of metrics for a given station ID"

    request {
        method GET()
        url "/api/v1/metrics/station/1"
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
                stationId: 1,
                metricType: $(regex('CPU_USAGE|MEMORY_USAGE|TEMPERATURE|POWER_CONSUMPTION|SIGNAL_STRENGTH|DATA_THROUGHPUT')),
                value: $(anyDouble())
            ]
        ])
    }
}
