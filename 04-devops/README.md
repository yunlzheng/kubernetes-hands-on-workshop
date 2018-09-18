# Day 4 - 使用Jenkins构建持续交付流水线

## 1. 准备工作

### 1.1 Kubernetes客户端

* 创建命名空间

```
$ kubectl create namespace YOUR_NAME
$ alias k="kubectl -n YOUR_NAME" # 后续使用k操作自己的命名空间
$ k get pods
```

* 清理集群资源:

```
$ k delete ingress --all
$ k delete deployments --all
$ k delete service --all
$ k delete pod --all
$ k delete configmap --all
```

### 1.1 安装Helm

* 安装Helm客户端

```
curl -OL https://storage.googleapis.com/kubernetes-helm/helm-v2.9.1-darwin-amd64.tar.gz
tar -xzvf helm-v2.9.1-darwin-amd64.tar.gz
cp darwin-amd64/helm /usr/local/bin/
```

* 初始化：

```
$ helm init --client-only
$HELM_HOME has been configured at /Users/yunlong/.helm.
Not installing Tiller due to 'client-only' flag having been set
Happy Helming!
```

* 检查版本:

```
$ helm version
Client: &version.Version{SemVer:"v2.9.1", GitCommit:"20adb27c7c5868466912eebdf6664e7390ebe710", GitTreeState:"clean"}
Server: &version.Version{SemVer:"v2.9.1", GitCommit:"20adb27c7c5868466912eebdf6664e7390ebe710", GitTreeState:"clean"}
```

### 1.3 准备Helm仓库服务

进入[云效私有仓库服务](https://repomanage.rdc.aliyun.com/my/repo?spm=a2c40.rdc_myindex.0.0.5ac62a00KYYdPK)，选择Helm仓库，创建命名空间：

获取仓库地址，以及用户名密码，使用一下命令添加仓库：

```
# 修改为自己的仓库，用户名以及密码
$ helm repo add k8s101 https://repomanage.rdc.aliyun.com/helm_repositories/1148-test --username=fFTXtJ --password=ghpEhQ2b7S
"k8s101" has been added to your repositories
```

### 1.4 准备示例应用

* 下载实例应用

```
curl -LO http://7pn5d3.com1.z0.glb.clouddn.com/kube-app-4.zip
unzip kube-app-4.zip
cd kube-app-4
```

* 编译并构建镜像

```
$ export DOCKER_NAMESPACE=k8s-mirrors #修改为自己的仓库
$ export DOCKER_REPO=registry.cn-hangzhou.aliyuncs.com/$DOCKER_NAMESPACE/kube-app

$ docker build -t $DOCKER_REPO:4.0.0 .
$ docker push $DOCKER_REPO:4.0.0
```


### 下载依赖镜像

```
$ docker pull yunlzheng/jenkins2:lts-k8s
```

## 2. Helm快速入门

```
$ cd kube-app-4 # 所有操作在应用目录下进行
```

### 2.1 初始化Chart

```
$ mkdir helm
$ cd helm
$ helm create kube-app
```

查看当前目录结构

```
├── helm
│   └── kube-app
│       ├── Chart.yaml
│       ├── charts
│       ├── templates
│       │   ├── NOTES.txt
│       │   ├── _helpers.tpl
│       │   ├── deployment.yaml
│       │   ├── ingress.yaml
│       │   └── service.yaml
│       └── values.yaml
```

```
$ vim helm/kube-app/values.yaml

# 省略其它部署
replicaCount: 1

image:
  repository: registry.cn-hangzhou.aliyuncs.com/k8s-mirrors/kube-app
  tag: 4.0.0
  pullPolicy: IfNotPresent
```

```
$ vim helm/kube-app/templates/deployment.yaml

# 忽略其它部分
          ports:
            - name: http
              containerPort: 7001 # 修改为应用端口
              protocol: TCP
```

### 2.2 安装Chart

```
$ helm install ./helm/kube-app --name quieting-dolphin --namespace yunlong # 修改为自己的命名空间
NAME:   quieting-dolphin
LAST DEPLOYED: Tue Sep 18 23:19:15 2018
NAMESPACE: yunlong
STATUS: DEPLOYED

RESOURCES:
==> v1/Service
NAME                       TYPE       CLUSTER-IP   EXTERNAL-IP  PORT(S)  AGE
quieting-dolphin-kube-app  ClusterIP  172.19.2.67  <none>       80/TCP   0s

==> v1beta2/Deployment
NAME                       DESIRED  CURRENT  UP-TO-DATE  AVAILABLE  AGE
quieting-dolphin-kube-app  1        1        1           0          0s

==> v1/Pod(related)
NAME                                        READY  STATUS             RESTARTS  AGE
quieting-dolphin-kube-app-7bf68bcd98-9wnnf  0/1    ContainerCreating  0         0s


NOTES:
1. Get the application URL by running these commands:
  export POD_NAME=$(kubectl get pods --namespace yunlong -l "app=kube-app,release=quieting-dolphin" -o jsonpath="{.items[0].metadata.name}")
  echo "Visit http://127.0.0.1:8080 to use your application"
  kubectl port-forward $POD_NAME 8080:80
```

访问应用：

```
$ export POD_NAME=$(kubectl get pods --namespace yunlong -l "app=kube-app,release=quieting-dolphin" -o jsonpath="{.items[0].metadata.name}")

$ kubectl port-forward $POD_NAME 7001:7001 --namespace=yunlong
Forwarding from 127.0.0.1:7001 -> 7001

$ curl http://localhost:7001
This is the message from config file
```

### 2.3 管理Release

查询Release列表

```
$ helm list --namespace=yunlong # 修改为自己的命名空间
NAME                    REVISION        UPDATED                         STATUS          CHART           NAMESPACE
quieting-dolphin        1               Tue Sep 18 23:19:15 2018        DEPLOYED        kube-app-0.1.0  yunlong
```

升级Release:

```
# 也可以直接修改values.yaml文件
$ helm upgrade --set image.tag=1.2.0 quieting-dolphin ./helm/kube-app --namespace=yunlong # 修改为自己的命名空间
Release "quieting-dolphin" has been upgraded. Happy Helming!
# 省略其它输出
```

查看release版本：

```
$ helm history quieting-dolphin
REVISION        UPDATED                         STATUS          CHART           DESCRIPTION
1               Tue Sep 18 23:19:15 2018        SUPERSEDED      kube-app-0.1.0  Install complete
2               Tue Sep 18 23:27:53 2018        SUPERSEDED      kube-app-0.1.0  Upgrade complete
3               Tue Sep 18 23:29:10 2018        DEPLOYED        kube-app-0.1.0  Upgrade complete
```

回滚版本：

```
$ helm rollback quieting-dolphin 1
Rollback was a success! Happy Helming!
```

## 3. 发布Chart到仓库

安装插件

```
$ helm plugin install https://github.com/chartmuseum/helm-push
Downloading and installing helm-push v0.7.1 ...
https://github.com/chartmuseum/helm-push/releases/download/v0.7.1/helm-push_0.7.1_darwin_amd64.tar.gz
Installed plugin: push
```

校验Chart格式

```
$ helm lint ./helm/kube-app
==> Linting ./helm/kube-app
[INFO] Chart.yaml: icon is recommended

1 chart(s) linted, no failures
```

打包Chart:

```
$ helm package ./helm/kube-app --destination=build/
build/kube-app-0.1.0.tgz
```

发布Chart到仓库

```
$ helm push build/kube-app-0.1.0.tgz k8s101
```

更新本地索引

```
$ helm repo update
Hang tight while we grab the latest from your chart repositories...
...Skip local chart repository
...Successfully got an update from the "k8s101" chart repository
Update Complete. ⎈ Happy Helming!⎈
```

搜索Chart

```
$ helm search k8s101
k8s101/kube-app         0.1.0           1.0             A Helm chart for Kubernetes
```

从仓库安装Chart实例：

```
$ helm install k8s101/kube-app --name=plinking-dachshund --namespace=yunlong
NAME:   plinking-dachshund
LAST DEPLOYED: Tue Sep 18 23:44:49 2018
NAMESPACE: yunlong
STATUS: DEPLOYED
# 忽略其它输出
```

## 4. 构建基于Helm的持续交付流水线

TODO

```

```