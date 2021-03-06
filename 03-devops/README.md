# Day 3 - 使用Prometheus监控应用

## 1. 准备工作

* 准备Kubernetes命名空间：

> 未安装kubernetes-cli的同学，参考Day 1的文档安装

```
$ kubectl create namespace YOUR_NAME
$ alias k="kubectl -n YOUR_NAME" # 后续使用k操作自己的命名空间
$ k get pods
```

清理资源：

```
$ k delete ingress --all
$ k delete deployments --all
$ k delete service --all
$ k delete pod --all
$ k delete configmap --all
```

* 准备镜像仓库：

```
$ export DOCKER_NAMESPACE=k8s-mirrors #修改为自己的仓库
$ export DOCKER_REPO=registry.cn-hangzhou.aliyuncs.com/$DOCKER_NAMESPACE/kube-app
```

* 下载实例项目：

点击下载[kubeapp](http://7pn5d3.com1.z0.glb.clouddn.com/kube-app-3.zip)实例程序

## 2. 初识Prometheus

在当前命名空间下安装Prometheus:

创建`manifests/prometheus-setup-v1.yaml`，如下所示：

> 该YAML中，定义了Promtheus相关的ConfigMap, Deployment、Service, Ingress等所有信息

```
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: prometheus
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: prometheus.$NAMESPACE.com # 注意，请修改为自己的域名 如 prometheus.your_name.com
    http:
      paths:
      - backend:
          serviceName: prometheus
          servicePort: 9090
---
apiVersion: v1
kind: "Service"
metadata:
  name: prometheus
  labels:
    name: prometheus
spec:
  ports:
  - name: prometheus
    protocol: TCP
    port: 9090
    targetPort: 9090
  selector:
    app: prometheus
  type: ClusterIP
---
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  labels:
    name: prometheus
  name: prometheus
spec:
  replicas: 1
  template:
    metadata:
      labels:
        app: prometheus
    spec:
      containers:
      - name: prometheus
        image: prom/prometheus:v2.2.1
        command:
        - "/bin/prometheus"
        args:
        - "--config.file=/etc/prometheus/prometheus.yml"
        ports:
        - containerPort: 9090
          protocol: TCP
        volumeMounts:
        - mountPath: "/etc/prometheus"
          name: prometheus-config
      volumes:
      - name: prometheus-config
        configMap:
          name: prometheus-config
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
data:
  prometheus.yml: |-
    global:
      scrape_interval:     15s 
      evaluation_interval: 15s
    scrape_configs:
    - job_name: 'prometheus'
      # metrics_path defaults to '/metrics'
      # scheme defaults to 'http'.
      static_configs:
      - targets: ['localhost:9090']
```

通过YAML创建相关资源：

```
$ k create -f manifests/prometheus-setup-v1.yaml
ingress.extensions/prometheus created
service/prometheus created
deployment.extensions/prometheus created
configmap/prometheus-config created
```

查看访问地址：

```
$ k get ingress
NAME           HOSTS                    ADDRESS         PORTS     AGE
echo-ingress   echo.kubernetes101.com   39.96.133.114   80        8d
prometheus     prometheus.$NAMESPACE.com   39.96.133.114   80        23s
```

修改本地/etc/hosts文件，添加域名映射：

```
39.96.133.114 prometheus.$NAMESPACE.com #请修改为自己的域名
```

从浏览器打开http://prometheus.$NAMESPACE.com

> 讲师时间

常用PromQL参考:

查询当前Prometheus服务的HTTP请求总量：

```
http_requests_total
```

查询特定Handler的HTTP请求量：

```
http_requests_total{handler="query"}
```

查询当前Prometheus服务的HTTP请求量变化确实

```
rate(http_requests_total[2m])
```

## 3. 使用Prometheus采集主机状态

安装Node Exporter,创建文件`manifests/node-exporter-setup.yaml`

```
apiVersion: extensions/v1beta1
kind: DaemonSet
metadata:
  labels:
    app: prometheus-node-exporter
  name: prometheus-node-exporter
  namespace: kube-system
spec:
  selector:
    matchLabels:
      app: prometheus-node-exporter
  template:
    metadata:
      labels:
        app: prometheus-node-exporter
    spec:
      containers:
      - args:
        - --path.procfs=/host/proc
        - --path.sysfs=/host/sys
        - --web.listen-address=0.0.0.0:9100
        image: quay.io/prometheus/node-exporter:v0.15.2
        imagePullPolicy: IfNotPresent
        name: node-exporter
        ports:
        - containerPort: 9100
          hostPort: 9100
          name: metrics
          protocol: TCP
        volumeMounts:
        - mountPath: /host/proc
          name: proc
          readOnly: true
        - mountPath: /host/sys
          name: sys
          readOnly: true
      hostNetwork: true
      hostPID: true
      volumes:
      - hostPath:
          path: /proc
          type: ""
        name: proc
      - hostPath:
          path: /sys
          type: ""
        name: sys
```

部署node exporter

```
k apply -f manifests/node-exporter-setup.yaml -n kube-system
```

创建`manifests/prometheus-setup-v2.yaml`，内容如下所示：

```
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
data:
  prometheus.yml: |-
    global:
      scrape_interval:     15s 
      evaluation_interval: 15s
    scrape_configs:
    - job_name: 'prometheus'
      static_configs:
      - targets: ['localhost:9090']
    - job_name: 'node'
      static_configs:
      - targets: ['192.168.3.87:9100', '192.168.3.88:9100', '192.168.3.89:9100', '192.168.3.90:9100', '192.168.3.91:9100', '192.168.3.92:9100']
```

更新Prometheus部署

```
$ k apply -f manifests/prometheus-setup-v2.yaml
ingress.extensions/prometheus configured
service/prometheus configured
deployment.extensions/prometheus configured
configmap/prometheus-config configured
```

> 注意，由于Deployment没有变化，需要手动删除prometheus的pod实例

从浏览器打开http://prometheus.$NAMESPACE.com

常用PromQL:

* 查询主机负载

```
node_load1
```

* 查询主机CPU利用率

```
rate(node_cpu[2m])
```

```
avg without(cpu) (rate(node_cpu[2m]))
```

```
1 - avg without(cpu) (rate(node_cpu{mode="idle"}[2m]))
```

## 4. 使用Grafana构建Dashboard

创建文件`manifests/grafana-setup.yaml`，如下所示：

```
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: grafana
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: grafana.$NAMESPACE.com # 注意，请修改为自己的域名
    http:
      paths:
      - backend:
          serviceName: grafana
          servicePort: 3000
---
apiVersion: v1
kind: "Service"
metadata:
  name: grafana
  labels:
    name: prometheus
spec:
  ports:
  - name: grafana
    protocol: TCP
    port: 3000
    targetPort: 3000
  selector:
    app: grafana
  type: ClusterIP
---
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  name: grafana
  labels:
    app: grafana
spec:
  replicas: 1
  selector:
    matchLabels:
      app: grafana
  template:
    metadata:
      labels:
        app: grafana
    spec:
      containers:
      - env:
        - name: GF_SECURITY_ADMIN_USER
          value: admin
        - name: GF_SECURITY_ADMIN_PASSWORD
          value: k8s101
        image: grafana/grafana:5.2.4
        imagePullPolicy: IfNotPresent
        name: grafana
        volumeMounts:
        - mountPath: /var/lib/grafana
          name: storage
      restartPolicy: Always
      volumes:
      - emptyDir: {}
        name: storage
```

创建Grafana:

```
$ k create -f manifests/grafana-setup.yaml
```

获取当前所有ingress:

```
$ k get ingress
NAME           HOSTS                    ADDRESS         PORTS     AGE
grafana        grafana.$NAMESPACE.com      39.96.133.114   80        1m
prometheus     prometheus.$NAMESPACE.com   39.96.133.114   80        18m
```

修改/etc/hosts文件，添加映射：

```
39.96.133.114 grafana.$NAMESPACE.com #请修改为自己的域名
```

浏览器打开访问http://grafana.$NAMESPACE.com

> 讲师时间

Tips:

正则匹配

```
/.*instance="([^"]*).*/
```

查询主机CPU：

```
1 - avg without(cpu) (rate(node_cpu{mode="idle"}[2m]))
```

## 5. Prometheus下的服务发现

创建文件`manifests/prometheus-setup-v4.yaml`,如下所示：

```
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
data:
  prometheus.yml: |-
    global:
      scrape_interval:     15s 
      evaluation_interval: 15s
    scrape_configs:
    - job_name: 'prometheus'
      static_configs:
      - targets: ['localhost:9090']
    - job_name: 'node'
      kubernetes_sd_configs:
      - role: node
```

更新Prometheus：

```
$ k apply -f manifests/prometheus-setup-v4.yaml
```

Prometheus无法访问Kubernetes API,需要进行RBAC授权创建文件`manifests/prometheus-rbac-setup.yaml`

```
apiVersion: rbac.authorization.k8s.io/v1beta1
kind: ClusterRole
metadata:
  name: prometheus
rules:
- apiGroups: [""]
  resources:
  - nodes
  - nodes/proxy
  - services
  - endpoints
  - pods
  verbs: ["get", "list", "watch"]
- apiGroups:
  - extensions
  resources:
  - ingresses
  verbs: ["get", "list", "watch"]
- nonResourceURLs: ["/metrics"]
  verbs: ["get"]
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: prometheus
---
apiVersion: rbac.authorization.k8s.io/v1beta1
kind: ClusterRoleBinding
metadata:
  name: prometheus
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: prometheus
subjects:
- kind: ServiceAccount
  name: prometheus
  namespace: $NAMESPACE # 请修改为自己的命名空间
```

RBAC授权

```
k apply -f manifests/prometheus-rbac-setup.yaml
```

修改Prometheus部署，添加ServiceAccount关联：


```
$ k edit deployments/prometheus

apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  labels:
    name: prometheus
  name: prometheus
spec:
  replicas: 1
  template:
    metadata:
      labels:
        app: prometheus
    spec:
      serviceAccountName: prometheus
      serviceAccount: prometheus
      containers:
      - name: prometheus
        image: prom/prometheus:v2.2.1
        command:
        - "/bin/prometheus"
        args:
        - "--config.file=/etc/prometheus/prometheus.yml"
        ports:
        - containerPort: 9090
          protocol: TCP
        volumeMounts:
        - mountPath: "/etc/prometheus"
          name: prometheus-config
      volumes:
      - name: prometheus-config
        configMap:
          name: prometheus-config
```

浏览器打开访问http://prometheus.$NAMESPACE.com


> 讲师时间

## 6. 使用Relabel定义服务发现过程

创建文件`manifests/prometheus-setup-v5.yaml`,如下所示：

```
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
data:
  prometheus.yml: |-
    global:
      scrape_interval:     15s 
      evaluation_interval: 15s
    scrape_configs:
    - job_name: 'prometheus'
      static_configs:
      - targets: ['localhost:9090']
    - job_name: 'node'
      kubernetes_sd_configs:
      - role: node
      relabel_configs:
      - source_labels: [__meta_kubernetes_node_label_node_role_kubernetes_io_master]
        action: drop
        regex: true
      - action: labelmap
        regex: __meta_kubernetes_node_label_(.+)
      - source_labels: [__meta_kubernetes_node_address_InternalIP]
        regex: (.+)
        target_label: __address__
        replacement: ${1}:9100
```

```
$ k apply -f manifests/prometheus-setup=v5.yaml
```

> 注意需要手动删除一下Promtheus的Pod实例

浏览器打开访问http://prometheus.$NAMESPACE.com

> 讲师时间

## 7. 为应用添加监控埋点

进入kube-app应用，修改build.gradle，添加以下依赖：

```
# 忽略其他内容
dependencies {
  compile('io.micrometer:micrometer-registry-prometheus:latest.release')
  # 忽略其他内容
}
```

在`src/main/resources/application.yaml`中添加以下内容：

```
management:
  endpoints:
    web:
      exposure:
        include: health,info,env,metrics,prometheus
```

启动应用，访问http://localhost:7001/actuator/prometheus

在src/main/java/com/github/workshop/Application.java中添加以下内容：

```
public class Application {

    # 忽略其他内容

    private static Counter counter = Metrics.counter("handler.hello", "uri", "/");

    @GetMapping("")
    public ResponseEntity hello() {
        counter.increment();
        return ResponseEntity.ok(appConfig.getMessage());
    }

}
```

重启应用，访问http://localhost:7001/actuator/prometheus

构建镜像：

```
$ docker build --no-cache -t $DOCKER_REPO:3.0.0 .
$ docker push $DOCKER_REPO:3.0.0
```

创建文件`manifests/kube-app-setup.yaml`：

```
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: kube-app-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: kube-app.$NAMESPACE.com # 修改为自己的命名空间
    http:
      paths:
      - backend:
          serviceName: kube-app
          servicePort: 7001
---
apiVersion: v1
kind: Service
metadata:
  annotations:
    k8s.io/probe: "true"
  labels:
    run: kube-app
  name: kube-app
spec:
  ports:
  - port: 7001
    protocol: TCP
    targetPort: 7001
  selector:
    run: kube-app
  type: ClusterIP
---
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
      annotations:
        k8s.io/should_be_scraped: "true"
        k8s.io/metric_path: /actuator/prometheus
        k8s.io/metric_port: "7001"
      labels:
        run: kube-app
    spec:
      containers:
      - image: registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app:3.0.0
        imagePullPolicy: Always
        name: kube-app
```

创建应用：

```
k apply -f manifests/kube-app-setup.yaml
```

添加域名映射：

```
39.96.133.114 kube-app.$NAMESPACE.com #请修改为自己的域名
```

访问 http://kube-app.$NAMESPACE.com/actuator/prometheus

## 8. 监控Pod实例

创建文件`manifests/prometheus-setup-v6.yaml`, 如下所示：

```
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
data:
  prometheus.yml: |-
    global:
      scrape_interval:     15s 
      evaluation_interval: 15s
    scrape_configs:
    - job_name: 'prometheus'
      static_configs:
      - targets: ['localhost:9090']
    - job_name: 'pods'
      kubernetes_sd_configs:
      - role: pod
      relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_k8s_io_should_be_scraped]
        action: keep
        regex: true
      - action: labelmap
        regex: __meta_kubernetes_pod_(.+)
      - source_labels: [__meta_kubernetes_pod_annotation_k8s_io_metric_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
      - source_labels: [__address__, __meta_kubernetes_pod_annotation_k8s_io_metric_port]
        action: replace
        regex: (.+)
        target_label: __address__
        separator: ":"
        replacement: ${1}
    - job_name: 'node'
      kubernetes_sd_configs:
      - role: node
      relabel_configs:
      - source_labels: [__meta_kubernetes_node_label_node_role_kubernetes_io_master]
        action: drop
        regex: true
      - action: labelmap
        regex: __meta_kubernetes_node_label_(.+)
      - source_labels: [__meta_kubernetes_node_address_InternalIP]
        regex: (.+)
        target_label: __address__
        replacement: ${1}:9100
```

更新配置：

```
k apply -f manifests/prometheus-setup-v6.yaml
```

删除Pod

## 9. 黑盒监控：探测Service可用性

部署Blackbox Exporter，创建`manifests/blackbox-exporter-setup.yaml`:

```
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: blackbox-exporter
  namespace: kube-public
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: blackbox-exporter.$NAMESPACE.com #请修改为自己的命名空间
    http:
      paths:
      - backend:
          serviceName: blackbox-exporter
          servicePort: 9115
---
apiVersion: v1
kind: Service
metadata:
  labels:
    app: blackbox-exporter
  name: blackbox-exporter
  namespace: kube-public
spec:
  ports:
  - name: blackbox
    port: 9115
    protocol: TCP
  selector:
    app: blackbox-exporter
  type: ClusterIP
---
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  labels:
    app: blackbox-exporter
  name: blackbox-exporter
  namespace: kube-public
spec:
  selector:
    matchLabels:
      app: blackbox-exporter
  template:
    metadata:
      labels:
        app: blackbox-exporter
    spec:
      containers:
      - image: prom/blackbox-exporter
        imagePullPolicy: IfNotPresent
        name: blackbox-exporter
```

在kube-public下创建公共的blackbox exporter实例

```
k apply -f manifests/blackbox-exporter-setup.yaml -n kube-public
```

打开 http://blackbox-exporter.$NAMESPACE.com #请修改为自己的IP

尝试使用网络探针探测： /probe?module=http_2xx&target=www.taobao.com

创建`manifests/prometheus-setup-v7.yaml`，如下所示：

```
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
data:
  prometheus.yml: |-
    global:
      scrape_interval:     15s 
      evaluation_interval: 15s
    scrape_configs:
    - job_name: 'prometheus'
      static_configs:
      - targets: ['localhost:9090']
    - job_name: 'service-probe'
      metrics_path: /probe
      params:
        module: [http_2xx]
      kubernetes_sd_configs:
      - role: service
      relabel_configs:
      - source_labels: [__meta_kubernetes_service_annotation_k8s_io_probe]
        action: keep
        regex: true
      - source_labels: [__address__]
        target_label: __param_target
      - target_label: __address__
        replacement: blackbox-exporter.kube-public.svc.cluster.local:9115
      - source_labels: [__param_target]
        target_label: instance
      - action: labelmap
        regex: __meta_kubernetes_service_label_(.+)
      - source_labels: [__meta_kubernetes_namespace]
        target_label: kubernetes_namespace
      - source_labels: [__meta_kubernetes_service_name]
        target_label: kubernetes_name
    - job_name: 'pods'
      kubernetes_sd_configs:
      - role: pod
      relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_k8s_io_should_be_scraped]
        action: keep
        regex: true
      - action: labelmap
        regex: __meta_kubernetes_pod_(.+)
      - source_labels: [__meta_kubernetes_pod_annotation_k8s_io_metric_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
      - source_labels: [__address__, __meta_kubernetes_pod_annotation_k8s_io_metric_port]
        action: replace
        regex: (.+)
        target_label: __address__
        separator: ":"
        replacement: ${1}
    - job_name: 'node'
      kubernetes_sd_configs:
      - role: node
      relabel_configs:
      - source_labels: [__meta_kubernetes_node_label_node_role_kubernetes_io_master]
        action: drop
        regex: true
      - action: labelmap
        regex: __meta_kubernetes_node_label_(.+)
      - source_labels: [__meta_kubernetes_node_address_InternalIP]
        regex: (.+)
        target_label: __address__
        replacement: ${1}:9100
```

更新配置

```
k apply -f manifests/prometheus-setup-v7.yaml
```

## 10. 练习：完善Grafana Dashboard
