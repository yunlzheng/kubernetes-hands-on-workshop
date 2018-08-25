#!/usr/bin/env bash
java -Xmx512m -Djava.security.egd=file:/dev/./urandom -jar kube-app.jar $@