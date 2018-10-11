```
ks port-forward kibana-logging-7445dc9757-fllct 5601:5601
```

```
kubectl label node cn-beijing.i-2ze52j61t5p9z4n60c9k beta.kubernetes.io/fluentd-ds-ready=true
kubectl label node cn-beijing.i-2ze52j61t5p9z4n60c9l beta.kubernetes.io/fluentd-ds-ready=true
kubectl label node cn-beijing.i-2ze52j61t5p9z4n60c9m beta.kubernetes.io/fluentd-ds-ready=true
```