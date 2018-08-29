# Day 1 - Kubernetes Core Concept

今天将从一个Spring Cloud的项目实例开始，了解Kubernetes相关的核心概念。

## 1. 准备工作

* IDEA: Java集成开发环境，[下载安装地址](https://www.jetbrains.com/idea/download/#section=mac)；
* Kubectl: Kubernetes命令行工具；
* Docker: Mac用户安装[Docker for mac](https://store.docker.com/editions/community/docker-ce-desktop-mac)即可；
* 阿里云账号，并开通[阿里云容器服务](https://cr.console.aliyun.com/);

### 1.1 安装Kubectl命令行工具：

```
$ brew install kubernetes-cli
```

获取Kubernetes集群配置文件[config](config),并保存到本地文件`~/.kube/config`

检查是否能够正常访问集群

```
$ kubectl cluster-info
Kubernetes master is running at https://x.x.x.x:6443

To further debug and diagnose cluster problems, use 'kubectl cluster-info dump'.
```

初始化命名空间

```
$ kubectl create namespace YOUR_NAME
$ alias k="kubectl -n YOUR_NAME" # 后续使用k操作自己的命名空间
$ k get pods
No resources found.
```

> 注意，后续使用alias定义的别名k操作k8S集群

### 1.2 下载初始化项目代码，并导入IDEA环境

从[http://7xj61w.com1.z0.glb.clouddn.com/kube-app-v1.zip](http://7xj61w.com1.z0.glb.clouddn.com/kube-app-v1.zip)下载实例项目

解压到工作目录`$WORKSPACE/kube-app`

确保项目能够正常编译

```
$ cd $WORKSPACE/kube-app
$ ./gradlew clean build
#  省略的部分
BUILD SUCCESSFUL in 12s
4 actionable tasks: 3 executed, 1 up-to-date
```

将kube-app项目导入到IDEA，导入成功后启动应用程序：

```
$ ./gradlew bootRun
#  省略的部分
2018-08-28 14:28:39.530  INFO 13620 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): 7001 (http) with context path ''
2018-08-28 14:28:39.539  INFO 13620 --- [           main] com.github.workshop.Application          : Started Application in 3.226 seconds (JVM running for 3.615)
<=========----> 75% EXECUTING [15s]
> :bootRun
```

启动成功后，访问7001端口，确保程序以正常运行:

```
$ curl http://localhost:7001
This is the message from config file
```

## 1.3 准备容器仓库

在阿里云镜像仓库服务中，创建仓库命名空间，建议以自己的名字命名：

![./cr_repo.png](./images/cr_repo.png)

请确保当前命名空间`开启自动创建仓库功能`且仓库类型为`公有`。

```
$ export DOCKER_NAMESPACE=k8s-mirrors #修改为自己的仓库
$ export DOCKER_REPO=registry.cn-hangzhou.aliyuncs.com/$DOCKER_NAMESPACE/kube-app
```

确保Docker能够正常登陆镜像仓库：

```
$ docker login registry.cn-hangzhou.aliyuncs.com
Username: XXXXX
Password:
Login Succeeded
```

## 2 应用容器化

为了能够让应用程序能够运行在Kubernetes中，我们需要对应用进行容器化。在项目根路径上创建Dockerfile文件：

```
FROM gradle:4.9.0-jdk8 AS builder

ADD src ./src
ADD build.gradle ./build.gradle
ADD settings.gradle ./settings.gradle
RUN gradle build

FROM java:8
COPY --from=builder /home/gradle/build/libs/kube-app.jar kube-app.jar

ADD entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh
ENTRYPOINT ["./entrypoint.sh"]
```

这里采用Mutil-Stage形式定义Dockerfile文件，这样可以直接在容器中定义项目构建，以及镜像构建过程。

* 阶段一：在gradle镜像中完成项目的编译
* 阶段二：从gradle镜像中拷贝jar包，到java:8镜像中，并且使用自定义的entrypoint.sh作为启动脚本

在项目根目录下创建`entrypoint.sh`，内容如下：

```
#!/usr/bin/env bash
java -Xmx512m -Djava.security.egd=file:/dev/./urandom -jar kube-app.jar $@
```

在entrypoint.sh中，我们使用了$@，这样在运行容器时，可以添加任意的命令行参数。

使用Docker命令行工具，构建镜像并推送到远端

```
$ docker build --no-cache -t $DOCKER_REPO:1.2.0 .
#  省略的部分
Removing intermediate container 01297d6325cc
 ---> 9062c5c18a4d
Successfully built 9062c5c18a4d
Successfully tagged registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:1.2.0

$ docker push $DOCKER_REPO:1.2.0
#  省略的部分
0eb22bfb707d: Layer already exists
a2ae92ffcd29: Layer already exists
1.2.0: digest: sha256:220a47f7a0afa0e4b2665332221a74d71f802b36b9154d278c88b2df8b703863 size: 2626
```

验证，镜像是否能够正常运行:

```
$ docker run -it -p 7001:7001 $DOCKER_REPO:1.2.0
# 或者通过命令行修改镜像参数
$ docker run -it -p 7001:7001 $DOCKER_REPO:1.2.0 --app.message=test
# 省略的部分
2018-08-28 06:36:53.209  INFO 7 --- [           main] o.s.j.e.a.AnnotationMBeanExporter        : Registering beans for JMX exposure on startup
2018-08-28 06:36:53.299  INFO 7 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): 7001 (http) with context path ''

$ curl http://127.0.0.1:7001
test
```

## 3. 使用Pod部署kube-app应用

在项目根路径下，创建目录deploy/manifests

```
mkdir -p deploy/manifests
```

### 3.1 使用单容器的Pod部署kube-app应用

创建文件deploy/manifests/kube-app-pod.yaml,内容如下：

> 注意：请修改$DOCKER_REPO到相应的镜像

```
apiVersion: v1
kind: Pod
metadata:
  labels:
    run: kube-app
  name: kube-app-pod
spec:
  containers:
  - image: $DOCKER_REPO:1.2.0 # 请更改为相应的镜像
    name: kube-app
```

使用kubectl创建Pod实例

```
$ k apply -f deploy/manifests/kube-app-pod.yaml
pod/kube-app-pod created
```

查询所有Pod实例`k get <resource>`

```
$ k get pods
NAME           READY     STATUS    RESTARTS   AGE
kube-app-pod   1/1       Running   0          8s
```

查看Pod的启动信息`k describe`

```
$ k describe pods/kube-app-pod
# 省略奇特输出
  Type    Reason                 Age   From                                        Message
  ----    ------                 ----  ----                                        -------
  Normal  Scheduled              7s    default-scheduler                           Successfully assigned kube-app-pod to cn-beijing.i-2ze52j61t5p9z4n60c9m
  Normal  SuccessfulMountVolume  7s    kubelet, cn-beijing.i-2ze52j61t5p9z4n60c9m  MountVolume.SetUp succeeded for volume "default-token-tzpfg"
  Normal  Pulling                7s    kubelet, cn-beijing.i-2ze52j61t5p9z4n60c9m  pulling image "registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:1.4.3"
  Normal  Pulled                 3s    kubelet, cn-beijing.i-2ze52j61t5p9z4n60c9m  Successfully pulled image "registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:1.4.3"
  Normal  Created                3s    kubelet, cn-beijing.i-2ze52j61t5p9z4n60c9m  Created container
  Normal  Started                3s    kubelet, cn-beijing.i-2ze52j61t5p9z4n60c9m  Started container
```

查看日志`k logs`:

```
$ k logs -f kube-app-pod
#省略的部分
2018-08-28 06:41:24.686  INFO 6 --- [           main] com.github.workshop.Application          : Starting Application on kube-app-pod with PID 6 (/kube-app.jar started by root in /)
2018-08-28 06:41:24.695  INFO 6 --- [           main] com.github.workshop.Application          : No active profile set, falling back to default profiles: default
```

进入到Pod的容器`k exec`

```
$ k exec -it kube-app-pod bash
root@kube-app-pod:/# curl http://127.0.0.1:7001
This is the message from config file
```

### 3.2 使用Pod部署应用和它的依赖

假设kube-app项目依赖了名为echo-server的服务，使用容器在本地运行服务依赖。

```
$ docker run -itd -p 8080:8080 jmalloc/echo-server
af14be36ad0670b32c20376b0b529fd70d3462d82764ffd24b0fa7bf345e49cd

$ curl http://127.0.0.1:8080
Request served by af14be36ad06

HTTP/1.1 GET /

Host: 127.0.0.1:8080
User-Agent: curl/7.54.0
Accept: */*
```

为了能够让kub-app能够使用echo-server提供的服务，在包com.github.workshop.service中创建类EchoService，内容如下

```
package com.github.workshop.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class EchoService {

    public String echo(String resource) {
        ResponseEntity<String> response = new RestTemplate().getForEntity("http://127.0.0.1:8080/echo/" + resource, String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }
        throw new RuntimeException("Echo service unreachable now");
    }

}
```

在Application.java中添加API映射，如下：

```
public class Application {

    #.....省略的部分

    @Autowired
    private EchoService echoService;

    @GetMapping("/echo/{resource}")
    public ResponseEntity echo(@PathVariable("resource") String resource) {
        return ResponseEntity.ok(echoService.echo(resource));
    }

    #.....省略的部分

}
```

重新运行应用程序：

```
./gradlew bootRun
```

访问echo api：

```
curl http://127.0.0.1:7001/echo/hello
Request served by 619e49f5ff50

HTTP/1.1 GET /echo/hello

Host: 127.0.0.1:8080
Accept: text/plain, application/json, application/*+json, */*
User-Agent: Java/1.8.0_51
Connection: keep-alive
```

重新打包镜像：

```
$ docker build --no-cache -t $DOCKER_REPO:1.3.2 .
# 省略输出的部分
$ docker push $DOCKER_REPO:1.3.2
# 省略输出的部分
```

为了将kube-app应用部署到Kubernetes中，可以直接在Pod中运行多个容器实例:

修改deploy/manifests/kube-app-pod.yaml，内容如下：

```
apiVersion: v1
kind: Pod
metadata:
  labels:
    run: kube-app
  name: kube-app-pod
spec:
  containers:
  - image:  registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:1.3.2 # 请更改为相应的镜像
    name: kube-app
  - image: jmalloc/echo-server
    name: echo-server
```

删除，并重建Pod

```
$ k apply -f deploy/manifests/kube-app-pod.yaml # 不能直接更新Pod
The Pod "kube-app-pod" is invalid: spec.containers: Forbidden: pod updates may not add or remove containers
```

```
$ k delete -f deploy/manifests/kube-app-pod.yaml
pod "kube-app-pod" deleted
$ k create -f deploy/manifests/kube-app-pod.yaml
pod/kube-app-pod created
```

查看Pod状态

```
$ k get pods
NAME           READY     STATUS              RESTARTS   AGE
kube-app-pod   0/2       ContainerCreating   0          7s
```

查看Pod日志

```
$ k logs -f kube-app-pod 
Error from server (BadRequest): a container name must be specified for pod kube-app-pod, choose one of: [kube-app echo-server]

$ k logs -f kube-app-pod -c kube-app
# 省略的部分
2018-08-28 07:00:11.577  INFO 6 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): 7001 (http) with context path ''
2018-08-28 07:00:11.582  INFO 6 --- [           main] com.github.workshop.Application          : Started Application in 3.878 seconds (JVM running for 4.431)
```

进入Pod，验证功能：

```
$ k exec -it kube-app-pod -c kube-app bash
root@kube-app-pod:/# curl http://127.0.0.1:7001/echo/hello
```

> 思考： Pod是什么？哪些场景时候使用多容器Pod，哪些场景不适合？

## 4. 使用Service和Endpoint找到依赖的服务

部署独立的echo-server, 创建文件deploy/manifests/echo-server-pod.yaml:

```
apiVersion: v1
kind: Pod
metadata:
  labels:
    run: echo
  name: echo
spec:
  containers:
  - image:  jmalloc/echo-server
    name: echo
```

使用Kubectl部署部署echo-server：

```
$ k apply -f deploy/manifests/echo-server-pod.yaml
pod/echo created
```

### 4.1 Service服务器端服务发现

创建deploy/manifests/echo-server-svc.yaml文件，内容如下：

```
apiVersion: v1
kind: Service
metadata:
  labels:
    run: echo
  name: echo
spec:
  ports:
  - port: 80
    protocol: TCP
    targetPort: 8080
  selector:
    run: echo
  type: ClusterIP
```

创建名为echo的service:

```
$ k apply -f deploy/manifests/echo-server-svc.yaml
service/echo created
```

使用Kubectl查询所有服务：

```
$ k get svc
NAME      TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)   AGE
echo      ClusterIP   172.19.11.202   <none>        80/TCP    19s
```

进入kube-app验证，DNS域名：

```
$ k exec -it kube-app-pod -c kube-app bash

root@kube-app-pod:/# ping echo
PING echo.yunlong.svc.cluster.local (172.19.11.202): 56 data bytes
^C--- echo.yunlong.svc.cluster.local ping statistics ---
2 packets transmitted, 0 packets received, 100% packet loss

root@kube-app-pod:/# curl http://echo
Request served by echo

HTTP/1.1 GET /

Host: echo
User-Agent: curl/7.38.0
Accept: */*
```

在Kubernetes中内置的DNS可以通过`<svc>.<namespace>.svc.cluster.local`访问。 Service与Pod之间通过标签形成松耦合的映射关系，Service会将请求转发到标签匹配的Pod实例上。

### 4.2 Endpoints客户端服务发现

使用describe查看资源详情，查看echo Service详细信息：

```
$ k describe svc echo
Name:              echo
Namespace:         yunlong
Labels:            run=echo
Annotations:       kubectl.kubernetes.io/last-applied-configuration={"apiVersion":"v1","kind":"Service","metadata":{"annotations":{},"labels":{"run":"echo"},"name":"echo","namespace":"yunlong"},"spec":{"ports":[{"port":...
Selector:          run=echo
Type:              ClusterIP
IP:                172.19.11.202
Port:              <unset>  80/TCP
TargetPort:        8080/TCP
Endpoints:         172.16.2.168:8080
Session Affinity:  None
Events:            <none>
```

查看-o wide查看Pod的更多信息，如下：

```
$ k get pods echo -o wide
NAME      READY     STATUS    RESTARTS   AGE       IP             NODE
echo      1/1       Running   0          3m        172.16.2.168   cn-beijing.i-2ze52j61t5p9z4n60c9m
```

> 思考？ Service和Pod是如何关联起来的？

查看当前集群中的Endpoints信息：

```
$ k get endpoints
NAME      ENDPOINTS           AGE
echo      172.16.2.168:8080   2m
```

查看endpoints详情：

```
$ k describe endpoints  echo
Name:         echo
Namespace:    yunlong
Labels:       run=echo
Annotations:  <none>
Subsets:
  Addresses:          172.16.2.168
  NotReadyAddresses:  <none>
  Ports:
    Name     Port  Protocol
    ----     ----  --------
    <unset>  8080  TCP

Events:  <none>
```

> 思考：假设数据库部署在集群外，K8S集群中的应用应该如何访问？

### 4.3 在项目中使用spring-cloud-kubernetes实现客户端服务发现

在kube-app的build.gradle文件中，添加依赖：

```
...
dependencies {
  ...
  compile 'org.springframework.cloud:spring-cloud-starter-kubernetes:0.3.0.RELEASE'
}

...
```

在项目中创建文件src/main/resources/bootstrap.yaml,内容如下：

```
spring:
  application:
    name: kube-app
  cloud:
    kubernetes:
      client:
        namespace: ${NAMESPACE:default} # 请将default修改为自己的命名空间
```

在src/main/java/Application.java中添加以下内容：

```
public class Application {
    #省略的部分

    @Autowired
    private DiscoveryClient discoveryClient;

    @GetMapping("/services")
    public ResponseEntity services() {
        return ResponseEntity.ok(discoveryClient.getServices());
    }

    @GetMapping("/services/{name}")
    public ResponseEntity service(@PathVariable("name") String name) {
        List<ServiceInstance> instances = discoveryClient.getInstances(name);
        return ResponseEntity.ok(instances);
    }
```

重启应用

```
$ ./gradlew bootRun
# 省略输出
```

访问新的API，通过discovery client查询当前集群中所有服务：

```
$ curl http://localhost:7001/services
["echo"]
```

通过discovery client查询服务echo的详细详情：

```
curl http://localhost:7001/services/echo
[{
  "serviceId":"echo",
  "secure":false,
  "metadata":{"run":"echo"},
  "host":"172.16.2.168",
  "port":8080,
  "uri":"http://172.16.2.168:8080",
  "scheme":null
}]
```

使用Discvery Client重构Echo Service

```
@Service
public class EchoService {

    @Autowired
    private DiscoveryClient discoveryClient;

    public String echo(String resource) {

        List<ServiceInstance> instances = discoveryClient.getInstances("echo");
        Optional<ServiceInstance> instance = instances.stream().findAny();

        if (!instance.isPresent()) {
            throw new RuntimeException("Echo service not found now");
        }

        ResponseEntity<String> response = new RestTemplate()
                .getForEntity(String.format("%s/echo/%s", instance.get().getUri(), resource), String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }

        throw new RuntimeException("Echo service unreachable now");
    }

}
```

构建镜像：

```
$ docker build --no-cache -t $DOCKER_REPO:1.4.3 .
$ docker push $DOCKER_REPO:1.4.3
```

修改deploy/manifests/kube-app-pod.yaml文件如下，使用env定义环境变量：

```
apiVersion: v1
kind: Pod
metadata:
  labels:
    run: kube-app
  name: kube-app-pod
spec:
  containers:
  - image:  registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:1.4.3 # 请更改为相应的镜像
    name: kube-app
    env:
    - name: NAMESPACE
      value: yunlong # 请修改为自己的命名空间
```

```
$ k delete -f deploy/manifests/kube-app-pod.yaml
$ k create -f deploy/manifests/kube-app-pod.yaml
```

对于环境变量K8S也支持直接获取当前Pod实例的信息，修改deploy/manifests/kube-app-pod.yaml如下所示

```
apiVersion: v1
kind: Pod
metadata:
  labels:
    run: kube-app
  name: kube-app-pod
spec:
  containers:
  - image:  registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:1.4.3 # 请更改为相应的镜像
    name: kube-app
    imagePullPolicy: Always
    env:
    - name: NAMESPACE
      valueFrom:
        fieldRef:
          apiVersion: v1
          fieldPath: metadata.namespace
```

重建Kube-app

```
$ k delete -f deploy/manifests/kube-app-pod.yaml
$ k create -f deploy/manifests/kube-app-pod.yaml
```

查看Pod日志：

```
$ k logs -f kube-app-pod
2018-08-26 11:58:17.074  WARN 6 --- [           main] o.s.cloud.kubernetes.StandardPodUtils    : Failed to get pod with name:[kube-app-pod]. You should look into this if things aren't working as you expect. Are you missing serviceaccount permissions?
io.fabric8.kubernetes.client.KubernetesClientException: Operation: [get]  for kind: [Pod]  with name: [kube-app-pod]  in namespace: [default]  failed.
```

```
$ k get pods kube-app-pod -o yaml
```

> 思考： 为什么本地都可以正常运行？ 从KubernetesAutoConfiguration开始

## 5. 使用RBAC管理应用权限

> 思考： 为了能够让运行在Kubernetes集群中的应用能够访问集群相关的信息？

创建deploy/manifests/kube-app-rbac-setup.yaml文件：

```
apiVersion: rbac.authorization.k8s.io/v1beta1
kind: ClusterRole
metadata:
  name: cloud:service
rules:
- apiGroups: [""]
  resources:
  - nodes
  - services
  - endpoints
  - pods
  - configmaps
  verbs: ["get", "list", "watch"]
- apiGroups:
  - extensions
  resources:
  - ingresses
  verbs: ["get", "list", "watch"]
---
apiVersion: rbac.authorization.k8s.io/v1beta1
kind: ClusterRoleBinding
metadata:
  name: cloud:service
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cloud:service
subjects:
- kind: ServiceAccount
  name: default
  namespace: yunlong # 请切换到自己的命名空间
```

```
$ k create -f deploy/manifests/kube-app-rbac-setup.yaml
```

重建Pod

## 6. 使用ConfigMap和Secret管理配置

> 思考：如何在Kubernetes下管理应用配置?

### 6.1, 使用环境变量管理应用配置

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
  - image:  registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:1.4.6 # 请更改为相应的镜像
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

## 6.2 使用ConfigMap管理应用

创建文件deploy/manifests/kube-app-configmap.yaml，内容如下：

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
k create -f deploy/manifests/kube-app-configmap.yaml
```

修了能够在Pod中应用配置，修改deploy/manifests/kube-app-pod.yaml:

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

直接使用ConfigMap管理应用配置文件，修改kube-app-configmap.yaml,如下所示：

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

在大多数情况下，我们可以直接将configmap作为Volume挂载到Pod上, 修改deploy/manifests/kube-app-pod.yaml如下所示：

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
k apply -f deploy/manifests/kube-app-pod.yaml
```

检查pod中的内容：

```
k exec -it kube-app-pod bash
root@kube-app-pod:/# cd /etc/config/
root@kube-app-pod:/etc/config# ls
application.yaml  message
```

不过由于kube-app是基于spring-boot构建的应用程序，配置文件是直接打包到jar中的，因此需要借助与中间件spring-cloud-kubernetes-config完成：

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

重启应用：

```
./gradlew bootRun
```

验证应用程序：

```
curl http://127.0.0.1:7001
hello from config maps
```

重新打包镜像：

```
docker build --no-cache -t $DOCKER_REPO:1.4.6.2 .
docker push $DOCKER_REPO:1.4.6.2
```

尝试直接修改configmap的内容。

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
    volumeMounts:
      - name: config-volume
        mountPath: /etc/config
    env:
    - name: NAMESPACE
      valueFrom:
        fieldRef:
          apiVersion: v1
          fieldPath: metadata.namespace
```

部署最新的kube-app镜像：

```
k apply -f deploy/manifests/kube-app-pod.yaml
```

## 总结

## 课后练习

如何访问运行在集群中的kube-app应用程序？
