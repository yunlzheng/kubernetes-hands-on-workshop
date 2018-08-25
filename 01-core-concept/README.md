# Day 1 - Kubernetes Core Concept

今天将从一个Spring Cloud的项目实例开始，了解Kubernetes相关的核心概念

## 准备工作

* IDEA: Java集成开发环境，[下载安装地址](https://www.jetbrains.com/idea/download/#section=mac)
* Kubectl: Kubernetes命令行工具，

1, 安装Kubectl命令行工具：

```
brew install kubernetes-cli
```

获取Kubernetes集群配置文件[config](config),并保存到本地文件`~/.kube/config`

检查是否能够正常访问集群

```
$ kubectl
kubectl cluster-info
Kubernetes master is running at https://x.x.x.x:6443
Heapster is running at https://x.x.x.x:6443/api/v1/namespaces/kube-system/services/heapster/proxy
KubeDNS is running at https://x.x.x.x:6443/api/v1/namespaces/kube-system/services/kube-dns:dns/proxy
monitoring-influxdb is running at https://x.x.x.x:6443/api/v1/namespaces/kube-system/services/monitoring-influxdb/proxy

To further debug and diagnose cluster problems, use 'kubectl cluster-info dump'.
```

2, 下载初始化项目代码，并导入IDEA环境

从[https://github.com/yunlzheng/kubernetes-hands-on-workshop/releases/tag/v0.0.1](https://github.com/yunlzheng/kubernetes-hands-on-workshop/releases/tag/v0.0.1)下载实例项目

解压到工作目录`$WORKSPACE/kube-app`

确保项目能够正常编译

```
cd $WORKSPACE/kube-app
./gradlew clean build
```

将kube-app项目导入到IDEA，进入Application.java运行main函数：

![./images/start-app.png](./images/start-app.png)

启动成功后，访问8080端口，确保程序以正常运行:

```
$ curl http://localhost:8080
This is the message from config file
```