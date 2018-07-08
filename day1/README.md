Day 1
=====

## Single Pod and Service

```
kubectl create -f busybox-pod.yml
```

```
kubectl create -f nginx-pod.yml
```

```
kubectl create -f nginx-service.yml
```

Service DNS

```
<SERVICE>.<NAMESPACE>.svc.cluster.local
```

```
kubectl exec -ti busybox -- nslookup nginx.default
```

```
kubectl exec busybox cat /etc/resolv.conf
```

## Deployments

```
kubectl create -f nginx-deployment.yml
```

Scale

```
kubectl scale deployments/nginx --replicas=3
```

Updata

```
kubectl set image deployment/nginx nginx=nginx:1.9.1
```

History

```
kubectl rollout history deployments/nginx
```

Undo

```
kubectl rollout undo deployments/nginx
```

```
kubectl rollout undo deployments/nginx --to-revision=3
```

## Container health and probe

```
kubectl create -f nginx-deployment-probe.yml
```

## ConfigMaps

### env

```
kubectl create -f  nginx-with-env.yml
```

```
kubectl create -f  special-config.yml
```

```
kubectl create -f nginx-with-env-from-configmaps.yml
```

```
kubectl create -f nginx-with-env-all-from-configmaps.yml
```

### config file

```
kubectl create configmap nginx-config --from-file=nginx.conf
```

```
kubectl create -f nginx-with-config-file.yml
```

### Secret

```
$ echo -n 'admin' | base64
YWRtaW4=
$ echo -n '1f2d1e2e67df' | base64
MWYyZDFlMmU2N2Rm
```

```
kubectl create -f secret.yml
```

```
kubectl create -f nginx-with-secret-file.yml
```

## Ingress

```
minikube  addons enable ingress
```

```
kubectl create -f nginx-ingress.yml
```

## Day Summary