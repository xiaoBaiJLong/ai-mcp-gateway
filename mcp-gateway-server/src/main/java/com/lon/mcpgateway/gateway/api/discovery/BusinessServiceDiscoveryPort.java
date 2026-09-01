package com.lon.mcpgateway.gateway.api.discovery;

import java.util.List;

public interface BusinessServiceDiscoveryPort {
    List<String> findHealthyServiceNames();

    List<ServiceAddress> findHealthyInstances(String serviceName);

    record ServiceAddress(String host, int port, boolean secure) {
    }
}
