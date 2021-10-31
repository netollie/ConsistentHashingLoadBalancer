package com.example.gateway.loadbalance;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.SelectedInstanceCallback;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsistentHashingLoadBalancer implements ReactorServiceInstanceLoadBalancer {
    private final String serviceId;
    private final AtomicInteger position;
    private ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

    public ConsistentHashingLoadBalancer(
        ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider,
        String serviceId) {
        this.serviceId = serviceId;
        this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
        this.position = new AtomicInteger(new Random().nextInt(1000));
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider
            .getIfAvailable(NoopServiceInstanceListSupplier::new);
        return supplier.get(request).next()
            .map(serviceInstances -> processInstanceResponse(request, supplier, serviceInstances));
    }

    private Response<ServiceInstance> processInstanceResponse(
        Request request, ServiceInstanceListSupplier supplier, List<ServiceInstance> serviceInstances) {
        Response<ServiceInstance> serviceInstanceResponse = getInstanceResponse(request, serviceInstances);
        if (supplier instanceof SelectedInstanceCallback && serviceInstanceResponse.hasServer()) {
            ((SelectedInstanceCallback) supplier).selectedServiceInstance(serviceInstanceResponse.getServer());
        }
        return serviceInstanceResponse;
    }

    private Response<ServiceInstance> getInstanceResponse(Request request, List<ServiceInstance> instances) {
        if (instances.isEmpty()) {
            return new EmptyResponse();
        }
        RequestDataContext context = (RequestDataContext) request.getContext();
        List<String> loadbalancerKeys = context.getClientRequest().getHeaders().get("Loadbalancer-Key");
        ServiceInstance instance = null;
        if (CollectionUtils.isEmpty(loadbalancerKeys)) {
            // 不存在Loadbalancer-Key的Header参数则采用轮询负载均衡机制
            int pos = Math.abs(this.position.incrementAndGet());
            instance = instances.get(pos % instances.size());
        } else {
            // 存在Loadbalancer-Key的Header参数则采用一致性哈希算法负载均衡机制
            ConsistentHashingAlgorithm<ServiceInstance> consistentHashingAlgorithm
                = new ConsistentHashingAlgorithm<>(ServiceInstance::getServiceId, 3);
            instances.forEach(consistentHashingAlgorithm::addNode);
            instance = consistentHashingAlgorithm.getNode(loadbalancerKeys.get(0));
        }
        return new DefaultResponse(instance);
    }
}
