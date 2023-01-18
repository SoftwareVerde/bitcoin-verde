package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.map.Map;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;

import java.util.concurrent.ConcurrentHashMap;

public class ServiceInquisitor implements NodeRpcHandler.ServiceInquisitor {
    protected final ConcurrentHashMap<String, SleepyService.StatusMonitor> _services = new ConcurrentHashMap<>();

    public void addService(final String serviceName, final SleepyService.StatusMonitor statusMonitor) {
        _services.put(serviceName, statusMonitor);
    }

    @Override
    public Map<String, String> getServiceStatuses() {
        final MutableMap<String, String> serviceStatuses = new MutableHashMap<>(_services.size());
        for (final String serviceName : _services.keySet()) {
            final SleepyService.StatusMonitor statusMonitor = _services.get(serviceName);
            serviceStatuses.put(serviceName, statusMonitor.getStatus().toString());
        }
        return serviceStatuses;
    }
}
