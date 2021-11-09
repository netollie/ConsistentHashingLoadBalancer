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
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>
 * 一致性Hash算法负载均衡
 * </p>
 *
 * @author netollie
 * @date 2021/11/01
 */
public class ConsistentHashingLoadBalancer implements ReactorServiceInstanceLoadBalancer {
    /**
     * 服务id
     */
    private final String serviceId;

    /**
     * 轮询机制位置
     */
    private final AtomicInteger position;

    /**
     * 服务列表提供类
     */
    private ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

    /**
     * 一致性哈希算法实例表
     */
    private static Map<String, ConsistentHashingAlgorithm<ServiceInstance>> ALGORITHM_MAP = new ConcurrentHashMap<>();

    /**
     * <p>
     * 构造方法
     * </p>
     *
     * @param serviceInstanceListSupplierProvider 服务列表提供类
     * @param serviceId 服务id
     * @author netollie
     * @date 2021/11/01
     */
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

    /**
     * <p>
     * 加工响应实例
     * </p>
     *
     * @param request 请求数据
     * @param supplier 服务实例提供者
     * @param serviceInstances 服务实例列表
     * @return 响应实例
     * @author netollie
     * @date 2021/11/09
    1    */
    private Response<ServiceInstance> processInstanceResponse(
        Request request, ServiceInstanceListSupplier supplier, List<ServiceInstance> serviceInstances) {
        Response<ServiceInstance> serviceInstanceResponse = getInstanceResponse(request, serviceInstances);
        if (supplier instanceof SelectedInstanceCallback && serviceInstanceResponse.hasServer()) {
            ((SelectedInstanceCallback) supplier).selectedServiceInstance(serviceInstanceResponse.getServer());
        }
        return serviceInstanceResponse;
    }

    /**
     * <p>
     * 选择响应实例
     * 当Header中不存在Loadbalancer-Key时, 采用轮询机制
     * 否则使用Loadbalancer-Key值进行一致性哈希
     * </p>
     *
     * @param request 请求数据
     * @param instances 服务实例列表
     * @return 响应实例
     * @author netollie
     * @date 2021/11/09
     */
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
            ConsistentHashingAlgorithm<ServiceInstance> algorithm = loadAlgorithm(instances);
            instance = algorithm.getNode(loadbalancerKeys.get(0));
        }
        return new DefaultResponse(instance);
    }

    /**
     * <p>
     * 加载算法
     * </p>
     *
     * @param instances 实例列表
     * @return 对应算法
     * @author netollie
     * @date 2021/11/09
     */
    private ConsistentHashingAlgorithm<ServiceInstance> loadAlgorithm(List<ServiceInstance> instances) {
        String key = String.format("%s_%d", this.serviceId, instances.hashCode());
        return ALGORITHM_MAP.computeIfAbsent(key, (String s) -> {
            ConsistentHashingAlgorithm<ServiceInstance> algorithm
                = new ConsistentHashingAlgorithm<>(ServiceInstance::getInstanceId, 3);
            instances.forEach(algorithm::addNode);
            return algorithm;
        });
    }
}

