package com.lon.mcpgateway.gateway.tool;

import java.util.List;

public interface BusinessServiceDiscovery {
    List<String> findHealthyServiceNames();

    List<ServiceAddress> findHealthyInstances(String serviceName);

    record ServiceAddress(String host, int port, boolean secure) {
    }
}
