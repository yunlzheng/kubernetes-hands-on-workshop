# Day 3 - DevOps In Kubernetes

## 准备工作

* 准备Kubernetes命名空间：

> 未安装kubernetes-cli的同学，参考Day 1的文档安装

```
$ kubectl create namespace YOUR_NAME
$ alias k="kubectl -n YOUR_NAME" # 后续使用k操作自己的命名空间
$ k get pods
```

* 准备镜像仓库：

```
$ export DOCKER_NAMESPACE=k8s-mirrors #修改为自己的仓库
$ export DOCKER_REPO=registry.cn-hangzhou.aliyuncs.com/$DOCKER_NAMESPACE/kube-app
```

* 下载实例项目：

点击下载[kubeapp](http://7pn5d3.com1.z0.glb.clouddn.com/kube-app-v3.zip)实例程序

## 初识Prometheus

在当前命名空间下安装Prometheus:

创建`manifests/prometheus-setup-v1.yaml`，如下所示：

> 该YAML中，定义了Promtheus相关的ConfigMap, Deployment、Service, Ingress等所有信息

```
#
# v1 Prometheus初识
#
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

## 使用Prometheus采集主机状态

创建`manifests/prometheus-setup-v2.yaml`，内容如下所示：

```
#
# V2 使用静态配置
#
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: prometheus
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: prometheus.yunlong.com
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

## 使用Grafana构建Dashboard

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

## Prometheus下的服务发现

创建文件`manifests/prometheus-v4-setup.yaml`,如下所示：

```
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: prometheus
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: prometheus.yunlong.com
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
      static_configs:
      - targets: ['localhost:9090']
    - job_name: 'node'
      kubernetes_sd_configs:
      - role: node
      relabel_configs:
      - source_labels: [__meta_kubernetes_node_address_InternalIP]
        regex: (.+)
        target_label: __address__
        replacement: ${1}:9100
```

更新Prometheus：

```
$ k apply -f manifests/prometheus-setup-v4.yaml
```

> 注意需要手动删除一下Promtheus的Pod实例

浏览器打开访问http://prometheus.$NAMESPACE.com

> 讲师时间

## 使用Relabel定义服务发现过程

创建文件`manifests/prometheus-v5-setup.yaml`,如下所示：

```
#
# v5 使用服务发现自动发现当前的node节点
# 使用relabel替换标签,过滤master节点
#
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: prometheus
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: prometheus.yunlong.com
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
$ k apply -f manifests/prometheus-setup-v5.yaml
```

> 注意需要手动删除一下Promtheus的Pod实例

浏览器打开访问http://prometheus.$NAMESPACE.com

> 讲师时间

## 使用Prometheus监控应用

> TODO: