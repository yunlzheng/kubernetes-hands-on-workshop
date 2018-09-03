# Day 2 - Kubernetes Core Concept

## 1. 访问集群内的应用

> 思考，我们现在是如何将应用暴露给用户的

创建Service并且关联kube-app应用，创建文件`deploy/manifests/kube-app-svc.yaml`：

```
apiVersion: v1
kind: Pod
metadata:
  labels:
    run: kube-app
  name: kube-app-pod
spec:
  containers:
  - image: registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:1.4.3
    name: kube-app
    env:
    - name: NAMESPACE
      valueFrom:
        fieldRef:
          apiVersion: v1
          fieldPath: metadata.namespace
```

使用`k apply`命名创建SVC:

```
$ k apply -f deploy/manifests/kube-app-svc.yaml
```

查看SVC详情：

```
$ k describe svc/kube-app
Name:              kube-app
Namespace:         yunlong
Labels:            run=kube-app
Annotations:       kubectl.kubernetes.io/last-applied-configuration={"apiVersion":"v1","kind":"Service","metadata":{"annotations":{},"labels":{"run":"kube-app"},"name":"kube-app","namespace":"yunlong"},"spec":{"ports":[...
Selector:          run=kube-app
Type:              ClusterIP
IP:                172.19.9.99
Port:              <unset>  7001/TCP
TargetPort:        7001/TCP
Endpoints:         172.16.1.229:7001 
Session Affinity:  None
Events:            <none>
```

> 确保endpoints中包含kube-app-pod实例的IP地址

Service定义了集群内的负载均衡能力，为了能够在集群外能够访问到服务，需要建立Ingress资源，创建YAML文件`deploy/manifests/kube-app-ingress.yaml`：

```
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: kube-app-ingress
spec:
  backend:
    serviceName: kube-app
    servicePort: 7001
```

创建Ingress资源：

```
$ k apply -f deploy/manifests/kube-app-ingress.yaml
```

查看当前命名空间下的Ingress资源:

```
$ k get ingress -o wide
NAME               HOSTS     ADDRESS         PORTS     AGE
kube-app-ingress   *         39.96.133.114   80        41s
```

```
export ADDRESS=39.96.133.114
```

在浏览器中访问ingress，`https://39.96.133.114/`

### 根据请求路径转发

修改`deploy/manifests/kube-app-ingress.yaml`如下所示：

```
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: kube-app-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - http:
      paths:
      - path: /kube-app
        backend:
          serviceName: kube-app
          servicePort: 7001
      - path: /echo
        backend:
          serviceName: echo
          servicePort: 80
```

修改ingress配置：

```
$ k apply -f deploy/manifests/kube-app-ingress.yaml
```

尝试根据ingress规则访问服务

### Virtual Hosting模式

修改`deploy/manifests/kube-app-ingress.yaml`,如下所示：

```
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: kube-app-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: kube-app.kubernetes101.com
    http:
      paths:
      - backend:
          serviceName: kube-app
          servicePort: 7001
  - host: echo.kubernetes101.com
    http:
      paths:
      - backend:
          serviceName: echo
          servicePort: 80
```

更新ingress配置：

```
$ k apply -f deploy/manifests/kube-app-ingress.yaml
```

指定Http Header访问Ingress地址：

```
$ curl -H 'Host: kube-app.kubernetes101.com' $ADDRESS
$ curl -H 'Host: echo.kubernetes101.com' $ADDRESS
```

## 2. 为应用传递参数

> 思考：如何在Kubernetes下管理应用配置?

### 2.1 在启动参数中定义配置

修改`deploy/manifests/kube-app-pod.yaml`文件，使用args定义容器的启动参数：

```
apiVersion: v1
kind: Pod
metadata:
  labels:
    run: kube-app
  name: kube-app-pod
spec:
  containers:
  - image: registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:1.4.3
    name: kube-app
    args:
    - --app.message=message2
    env:
    - name: NAMESPACE
      valueFrom:
        fieldRef:
          apiVersion: v1
          fieldPath: metadata.namespace
```

重新加载Pod配置：

```
$ k delete -f deploy/manifests/kube-app-pod.yaml
$ k apply -f deploy/manifests/kube-app-pod.yaml
```

访问应用：

```
$ curl -H 'Host: kube-app.kubernetes101.com' $ADDRESS
```

### 2.2, 使用环境变量管理应用配置

使用环境变量管理配置，修改src/main/resoures/application.yaml如下所示：

```
server:
  port: 7001
app:
  message: ${MESSAGE:This is the message from config file}
```

```
$ docker build --no-cache -t $DOCKER_REPO:1.4.6 .
$ docker push $DOCKER_REPO:1.4.6
```

修改，并更新deploy/manifests/kube-app-pod.yaml, 如下所示:

```
apiVersion: v1
kind: Pod
metadata:
  labels:
    run: kube-app
  name: kube-app-pod
spec:
  containers:
  - image:  registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:1.4.6
    name: kube-app
    imagePullPolicy: Always
    env:
    - name: NAMESPACE
      valueFrom:
        fieldRef:
          apiVersion: v1
          fieldPath: metadata.namespace
    - name: MESSAGE
      value: 'This is the message from environment'
```

重建Pod.

## 3. 分离应用和配置

> 思考：到目前为止，镜像已经能够支持通过命令行和环境变量的方式修改启动参数，但是这样符合最佳实践吗？

### 3.1 使用ConfigMap管理环境变量

创建文件`deploy/manifests/kube-app-configmap.yaml`，内容如下：

```
apiVersion: v1
kind: ConfigMap
metadata:
  name: kube-app
data:
  message: 'This is the message from configmap env'
```

创建configmaps

```
$ k create -f deploy/manifests/kube-app-configmap.yaml
```

修了能够在Pod中应用配置，修改`deploy/manifests/kube-app-pod.yaml`:

```
apiVersion: v1
kind: Pod
metadata:
  labels:
    run: kube-app
  name: kube-app-pod
spec:
  containers:
  - image: registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:1.4.6 # 请更改为相应的镜像
    name: kube-app
    imagePullPolicy: Always
    env:
    - name: NAMESPACE
      valueFrom:
        fieldRef:
          apiVersion: v1
          fieldPath: metadata.namespace
    - name: MESSAGE
      valueFrom:
        configMapKeyRef:
          name: kube-app
          key: message
```

重建Pod

```
$ k delete -f deploy/manifests/kube-app-pod.yaml
$ k create -f deploy/manifests/kube-app-pod.yaml
```

通过Ingress访问应用：

```
$ curl -H 'Host: kube-app.kubernetes101.com' $ADDRESS
This is the message from configmap env
```

### 3.2 使用ConfigMap管理配置文件

直接使用ConfigMap管理应用配置文件，并通过Volume的形式挂载，修改`deploy/manifests/kube-app-configmap.yaml`,如下所示：

```
apiVersion: v1
kind: ConfigMap
metadata:
  name: kube-app
data:
  message: 'This is the message from configmap env'
  application.yaml: |-
    server:
      port: 7001
    app:
      message: 'hello from config maps'
```

修改`deploy/manifests/kube-app-pod.yaml`如下所示：

```
apiVersion: v1
kind: Pod
metadata:
  labels:
    run: kube-app
  name: kube-app-pod
spec:
  containers:
  - image: registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:1.4.6 # 请更改为相应的镜像
    name: kube-app
    imagePullPolicy: Always
    volumeMounts:
      - name: config-volume
        mountPath: /etc/config
    env:
    - name: NAMESPACE
      valueFrom:
        fieldRef:
          apiVersion: v1
          fieldPath: metadata.namespace
    - name: MESSAGE
      valueFrom:
        configMapKeyRef:
          name: kube-app
          key: message
  volumes:
    - name: config-volume
      configMap:
        name: kube-app
```

更新kube:

```
$ k delete -f deploy/manifests/kube-app-pod.yaml
$ k apply -f deploy/manifests/kube-app-pod.yaml
```

检查pod中的内容：

```
$ k exec -it kube-app-pod bash
root@kube-app-pod:/# cd /etc/config/
root@kube-app-pod:/etc/config# ls
application.yaml  message
```

### 3.2 中间件与Kubernetes整合增强配置管理

在kube-app的build.gradle添加依赖：

```
compile 'org.springframework.cloud:spring-cloud-starter-kubernetes-config:0.3.0.RELEASE'
```

修改src/main/resources/bootstrap.yaml,添加一下配置：

```
spring:
  application:
    name: kube-app
  cloud:
    kubernetes:
      client:
        namespace: ${NAMESPACE:default}
      reload:
        enabled: true
        mode: polling
        period: 5000
      config:
        sources:
          - name: ${spring.application.name}
```

修改src/main/resources/application.yaml, 添加一下配置：

```
management:
  endpoint:
    restart:
      enabled: true
```

在本地启动应用程序：

```
$ ./gradlew bootRun
# 忽略其他输出
2018-09-03 16:46:46.907  INFO 7224 --- [           main] b.c.PropertySourceBootstrapConfiguration : Located property source: ConfigMapPropertySource {name='configmap.kube-app.yunlong'}
2018-09-03 16:46:46.908  INFO 7224 --- [           main] b.c.PropertySourceBootstrapConfiguration : Located property source: SecretsPropertySource {name='secrets.kube-app.yunlong'}
```

验证应用程序配置：

```
$ curl http://127.0.0.1:7001
hello from config maps
```

尝试通过`kubectl edit configmaps kube-app`直接修改配置文件内容。

重新打包镜像：

```
$ docker build --no-cache -t $DOCKER_REPO:1.4.6.2 .
$ docker push $DOCKER_REPO:1.4.6.2
```

修改deploy/manifests/kube-app-pod.yaml:

```
apiVersion: v1
kind: Pod
metadata:
  labels:
    run: kube-app
  name: kube-app-pod
spec:
  containers:
  - image: registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:1.4.6.2 # 请更改为相应的镜像
    name: kube-app
    imagePullPolicy: Always
    env:
    - name: NAMESPACE
      valueFrom:
        fieldRef:
          apiVersion: v1
          fieldPath: metadata.namespace
```

部署最新的kube-app镜像：

```
$ k delete -f deploy/manifests/kube-app-pod.yaml
$ k create -f deploy/manifests/kube-app-pod.yaml
```

## 4. 使用更好的方式管理应用生命周期

> 思考1：直接使用Pod管理应用给你带来了哪些不好的体验？
> 思考2：在管理应用有哪些常见的场景?

### 4.1 使用Deployment部署应用

创建文件`deploy/manifests/kube-app-deployment.yaml`,内容如下所示：

```
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  labels:
    run: kube-app
  name: kube-app
spec:
  replicas: 1
  selector:
    matchLabels:
      run: kube-app
  template:
    metadata:
      labels:
        run: kube-app
    spec:
      containers:
      - image: registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:1.4.6.2
        name: kube-app
```

使用`k apply`创建Deployment资源

```
$ k apply -f deploy/manifests/kube-app-deployment.yaml
```

查看Deployments状态：

```
$ k get deployments
NAME       DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
kube-app   1         1         1            1           3m
```

查看通过Deployment创建的kube-app实例：

```
$ k get pods
NAME                        READY     STATUS    RESTARTS   AGE
echo                        1/1       Running   0          4d
kube-app-5848c4647d-5t5t9   1/1       Running   0          3m
```

常用操作：

* 扩容应用

```
$ k scale deployments/kube-app --replicas=3
deployment.extensions/kube-app scaled
```

* 更新镜像版本

```
$ k set image deployments/kube-app kube-app=registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:1.4.6
deployment.extensions/kube-app image updated
```

* 回滚到上一个操作

```
k rollout undo deployments/kube-app
```

* 查看所有历史版本

```
$ k rollout history deployments/kube-app
deployments "kube-app"
REVISION  CHANGE-CAUSE
4         <none>
5         <none>
6         <none>
```

* 查看历史版本详情

```
k rollout history deployments/kube-app --revision=5
```

* 回滚到指定版本

```
k rollout undo deployments/kube-app --to-revision=3
```