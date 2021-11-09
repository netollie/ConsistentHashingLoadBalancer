# 一致性hash算法-网关负载均衡

## 算法简述
https://www.cnblogs.com/moonandstar08/p/5405991.html

## DEMO
```bash
$ curl -i 'http://localhost:8000/data/load' -H 'Loadbalancer-Key:1'
data service[8080]
```
```bash
$ curl -i 'http://localhost:8000/data/load' -H 'Loadbalancer-Key:7'
data service[8081]
```
