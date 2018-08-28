
export NAMESPACE=$1
echo "SET_ENV NAMESPACE=$NAMESPACE"
export DOCKER_REPO=registry.cn-hangzhou.aliyuncs.com/$NAMESPACE/kube-app
echo "SET_ENV DOCKER_REPO=$DOCKER_REPO"

alias k='kubectl -n $NAMESPACE'
echo `alias k`

echo "SET DOCKER ACCOUNT"
echo "Login to: registry.cn-hangzhou.aliyuncs.com"
docker login registry.cn-hangzhou.aliyuncs.com