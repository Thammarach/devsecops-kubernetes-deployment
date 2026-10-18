# 🏗️ DevSecOps Full Jenkins + ArgoCD on Multi-Kubernetes 

## 📖 หัวข้อการเรียนรู้

### 1. ทำความรู้จัก Infrastructure และองค์ประกอบหลัก
- 💻 **Compute** – เครื่องเซิร์ฟเวอร์หรือ VM ที่ใช้ประมวลผล  

### 2. ติดตั้ง Software
Jenkins (CI/CD Server)

```bash
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

```bash
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

```bash
kubectl create namespace argocd

kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Get Admin Password
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo

# Forward Port เพื่อเข้า UI
kubectl port-forward svc/argocd-server -n argocd 8080:443
microk8s kubectl port-forward svc/argocd-server -n argocd 8080:443 &

# Namespace สำหรับ Application
kubectl create namespace application

# Install ArGo CLI
curl -sSL -o argocd-linux-amd64 https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64
sudo install -m 555 argocd-linux-amd64 /usr/local/bin/argocd
rm argocd-linux-amd64

argocd version
> argocd: v3.5.5+a5038b0

argocd login 127.0.0.1:8080 --username admin --password P@ssw0rd --insecure
Handling connection for 8080
Handling connection for 8080
Handling connection for 8080
Handling connection for 8080
Handling connection for 8080
'admin:login' logged in successfully
Context '127.0.0.1:8080' updated

# You should be log in aws with cli before add add cluster
argocd cluster add arn:aws:eks:ap-southeast-7:638846798909:cluster/eks-training --kubeconfig eks.yml
WARNING: This will create a service account `argocd-manager` on the cluster referenced by context `arn:aws:eks:ap-southeast-7:638846798909:cluster/eks-training` with full cluster level privileges. Do you want to continue [y/N]? y
{"level":"fatal","msg":"failed to create service account \"argocd-manager\" in namespace \"kube-system\": Post \"https://25CB6DD8A775DFFC280B353EAF48547E.yl4.ap-southeast-7.eks.amazonaws.com/api/v1/namespaces/kube-system/serviceaccounts\": getting credentials: exec: executable aws not found\n\nIt looks like you are trying to use a client-go credential plugin that is not installed.\n\nTo learn more about this feature, consult the documentation available at:\n      https://kubernetes.io/docs/reference/access-authn-authz/authentication/#client-go-credential-plugins","time":"2026-10-13T15:33:24Z"}
```

📊 SonarQube (Code Quality – Docker Compose)

```bash
cd day-1/tool/sonaqube
docker network create sonarqube
sudo nano /etc/sysctl.conf
vm.max_map_count=262144
sudo sysctl -p
docker compose up -d
```

downloads

```bash
curl https://gitlab.com/wenwen6046/devops-bootcamp/-/raw/main/day-1/tool/sonaqube/docker-compose.yml -o docker-compose.yml
curl https://gitlab.com/wenwen6046/devops-bootcamp/-/raw/main/day-1/tool/observability/docker-compose.yml -o docker-compose.yml
curl https://gitlab.com/wenwen6046/devops-bootcamp/-/raw/main/day-1/tool/observability/tempo.yaml -o tempo.yaml
curl https://gitlab.com/wenwen6046/devops-bootcamp/-/raw/main/day-1/tool/authentik/docker-compose.yml -o docker-compose.yml
```

install Vault On Kubernetes

```bash
helm upgrade --install --wait vault-operator oci://ghcr.io/bank-vaults/helm-charts/vault-operator
kubectl kustomize https://github.com/bank-vaults/vault-operator/deploy/rbac | kubectl apply -f -
kubectl apply -f https://raw.githubusercontent.com/bank-vaults/vault-operator/v1.21.0/deploy/examples/cr-raft.yaml
kubectl get pods
```

```bash
kubectl port-forward vault-0 8200 &

export VAULT_ADDR=https://vault-server.svc.microk8s.site

kubectl get secret vault-tls -o jsonpath="{.data.ca\.crt}" | base64 --decode > $PWD/vault-ca.crt

export VAULT_CACERT=$PWD/vault-ca.crt

vault status

export VAULT_TOKEN=$(kubectl get secrets vault-unseal-keys -o jsonpath={.data.vault-root} | base64 --decode)

# For microk8s

microk8s kubectl port-forward vault-0 8200:8200 &

export VAULT_ADDR=https://0.0.0.0:8200

microk8s kubectl get secret vault-tls -o jsonpath="{.data.ca\.crt}" | base64 --decode > $PWD/vault-ca.crt

export VAULT_CACERT=$PWD/vault-ca.crt

export VAULT_TOKEN=$(microk8s kubectl get secrets vault-unseal-keys -o jsonpath={.data.vault-root} | base64 --decode)

echo $VAULT_TOKEN
```

Deploy Webhook Vault

```bash
kubectl create namespace vault-infra
kubectl label namespace vault-infra name=vault-infra

helm upgrade --install --wait vault-secrets-webhook oci://ghcr.io/bank-vaults/helm-charts/vault-secrets-webhook --namespace vault-infra

kubectl get pods --namespace vault-infra
```

Install Prometheus and Grafana on gke

```bash
kubectl create namespace monitoring
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
# helm install prometheus-operator prometheus-community/kube-prometheus-stack -n monitoring
```

base64 to string

```bash
echo VEEtN243eU...... | base64 --decode
```

username password สำหรับ grafana
```bash
user : admin
pass : prom-operator
```

อ้างอิง https://prometheus-community.github.io/helm-charts/

install grafana loki for logging view

```bash
helm repo add grafana https://grafana.github.io/helm-charts
helm install loki grafana/loki-stack -n monitoring
```
อ้างอิง https://grafana.com/docs/loki/latest/setup/install/helm/install-monolithic/

Host สำหรับใช้เชื่อมต่อ

```bash
http://loki.monitoring.svc.cluster.local:3100
```

```bash
#helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo add traefik https://traefik.github.io/charts
helm repo update
```

```bash
# helm install nginx-ingress-public ingress-nginx/ingress-nginx \
  --namespace kube-system \
  --set controller.service.type=LoadBalancer \
  --set controller.ingressClass=nginx \
  --set controller.metrics.serviceMonitor.enabled=true \
  --set controller.metrics.serviceMonitor.additionalLabels.release="prometheus-operator" \
  --set controller.service.annotations."cloud\.google\.com/load-balancer-type"="External"

helm install traefik-public traefik/traefik --namespace traefik --create-namespace -f traefik-values.yaml

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm upgrade traefik-public traefik/traefik -n traefik -f traefik-values.yaml
```
Create secret
```bash
kubectl create secret docker-registry gcr-json-key \
  --docker-server=https://asia-southeast1-docker.pkg.dev \
  --docker-username=_json_key \
  --docker-password="$(cat key.json)" \
  --docker-email=gitlab-ci@xxxx.iam.gserviceaccount.com -n application
```