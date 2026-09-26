# 🏗️ DevSecOps Full Jenkins + ArgoCD on Multi-Kubernetes 
---

## 📖 หัวข้อการเรียนรู้

### 1. ทำความรู้จัก Infrastructure และองค์ประกอบหลัก
- 💻 **Compute** – เครื่องเซิร์ฟเวอร์หรือ VM ที่ใช้ประมวลผล  


> 🔎 เราจะใช้ **Google Cloud Platform (GCP)** เป็นหลัก และมีภาพ Diagram Infra ประกอบ (อยู่ในโฟลเดอร์ `image/`)
![image description](/image/infra.png)


### 2. ติดตั้ง Software
Jenkins (CI/CD Server)

```
sudo wget -O /usr/share/keyrings/jenkins-keyring.asc \
  https://pkg.jenkins.io/debian-stable/jenkins.io-2023.key

echo "deb [signed-by=/usr/share/keyrings/jenkins-keyring.asc] \
  https://pkg.jenkins.io/debian-stable binary/" | sudo tee \
  /etc/apt/sources.list.d/jenkins.list > /dev/null

sudo apt-get update
sudo apt-get install -y jenkins maven
sudo apt update
sudo apt install -y fontconfig openjdk-17-jre 

sudo systemctl enable jenkins
sudo systemctl start jenkins

# Plugin & Tools เสริม

# install docker
sudo apt update
sudo apt install docker.io
sudo usermod -aG docker ${USER}
sudo chmod 777 /var/run/docker.sock

# install maven
sudo apt install maven
mvn -v

# install trivy (Security Scan)
sudo apt-get install wget apt-transport-https gnupg lsb-release
wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | gpg --dearmor | sudo tee /usr/share/keyrings/trivy.gpg > /dev/null
echo "deb [signed-by=/usr/share/keyrings/trivy.gpg] https://aquasecurity.github.io/trivy-repo/deb $(lsb_release -sc) main" | sudo tee -a /etc/apt/sources.list.d/trivy.list
sudo apt-get update
sudo apt-get install trivy

```


install docker, docker compose
```

sudo apt-get update
sudo apt-get install ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update

sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

🚀 ArgoCD (GitOps Deployment)
```
kubectl create namespace argocd

kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Get Admin Password
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo

# Forward Port เพื่อเข้า UI
kubectl port-forward svc/argocd-server -n argocd 8080:443

# Namespace สำหรับ Application
kubectl create namespace application

```

📊 SonarQube (Code Quality – Docker Compose)
```
cd day-1/tool/sonaqube
docker network create sonarqube
sudo nano /etc/sysctl.conf
vm.max_map_count=262144
sudo sysctl -p
docker compose up -d
```

downloads
```
curl https://gitlab.com/wenwen6046/devops-bootcamp/-/raw/main/day-1/tool/sonaqube/docker-compose.yml -o docker-compose.yml
curl https://gitlab.com/wenwen6046/devops-bootcamp/-/raw/main/day-1/tool/observability/docker-compose.yml -o docker-compose.yml
curl https://gitlab.com/wenwen6046/devops-bootcamp/-/raw/main/day-1/tool/observability/tempo.yaml -o tempo.yaml
curl https://gitlab.com/wenwen6046/devops-bootcamp/-/raw/main/day-1/tool/authentik/docker-compose.yml -o docker-compose.yml
```

install Vault On Kubernetes
```
helm upgrade --install --wait vault-operator oci://ghcr.io/bank-vaults/helm-charts/vault-operator
kubectl kustomize https://github.com/bank-vaults/vault-operator/deploy/rbac | kubectl apply -f -
kubectl apply -f https://raw.githubusercontent.com/bank-vaults/vault-operator/v1.21.0/deploy/examples/cr-raft.yaml
kubectl get pods
```

```
kubectl port-forward vault-0 8200 &

export VAULT_ADDR=https://127.0.0.1:8200

kubectl get secret vault-tls -o jsonpath="{.data.ca\.crt}" | base64 --decode > $PWD/vault-ca.crt

export VAULT_CACERT=$PWD/vault-ca.crt

vault status

export VAULT_TOKEN=$(kubectl get secrets vault-unseal-keys -o jsonpath={.data.vault-root} | base64 --decode)

##
สำหรับ microk8s

microk8s kubectl port-forward vault-0 8200:8200 &

export VAULT_ADDR=https://0.0.0.0:8200

microk8s kubectl get secret vault-tls -o jsonpath="{.data.ca\.crt}" | base64 --decode > $PWD/vault-ca.crt

export VAULT_CACERT=$PWD/vault-ca.crt

export VAULT_TOKEN=$(microk8s kubectl get secrets vault-unseal-keys -o jsonpath={.data.vault-root} | base64 --decode)

echo $VAULT_TOKEN


```

Deploy Webhook Vault
```
kubectl create namespace vault-infra
kubectl label namespace vault-infra name=vault-infra

helm upgrade --install --wait vault-secrets-webhook oci://ghcr.io/bank-vaults/helm-charts/vault-secrets-webhook --namespace vault-infra

kubectl get pods --namespace vault-infra

```

Install Prometheus and Grafana on gke
```
kubectl create namespace monitoring
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm install prometheus-operator prometheus-community/kube-prometheus-stack -n monitoring
```
base64 to string
```
echo VEEtN243eU...... | base64 --decode
```

username password สำหรับ grafana
```
user : admin
pass : prom-operator
```

อ้างอิง https://prometheus-community.github.io/helm-charts/

install grafana loki for logging view
```
helm repo add grafana https://grafana.github.io/helm-charts
helm install loki grafana/loki-stack -n monitoring
```
อ้างอิง https://grafana.com/docs/loki/latest/setup/install/helm/install-monolithic/

Host สำหรับใช้เชื่อมต่อ
```
http://loki.monitoring.svc.cluster.local:3100
```

```
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
```

```
helm install nginx-ingress-public ingress-nginx/ingress-nginx \
  --namespace kube-system \
  --set controller.service.type=LoadBalancer \
  --set controller.ingressClass=nginx \
  --set controller.metrics.serviceMonitor.enabled=true \
  --set controller.metrics.serviceMonitor.additionalLabels.release="prometheus-operator" \
  --set controller.service.annotations."cloud\.google\.com/load-balancer-type"="External"

```
Create secret
```
kubectl create secret docker-registry gcr-json-key \
  --docker-server=https://asia-southeast1-docker.pkg.dev \
  --docker-username=_json_key \
  --docker-password="$(cat key.json)" \
  --docker-email=gitlab-ci@xxxx.iam.gserviceaccount.com -n application
```