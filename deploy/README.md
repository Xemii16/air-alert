# CI/CD Setup: GitHub Actions -> GCP Compute Engine

This directory contains everything needed to run and auto-deploy `:backend` to your GCP Compute Engine (`e2-micro`) VM on every push to `main`.

## 1. One-Time Setup on GCP (IAM & Service Account)

Create a dedicated Service Account for GitHub Actions with only the permissions needed to SSH/SCP into your Compute Engine instance:

```bash
export PROJECT_ID="your-gcp-project-id"

gcloud iam service-accounts create github-deployer \
  --project="$PROJECT_ID" \
  --display-name="GitHub Actions Compute Deployer"

SA_EMAIL="github-deployer@${PROJECT_ID}.iam.gserviceaccount.com"

# Allow managing/connecting to Compute Engine instances + acting as VM service account
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/compute.instanceAdmin.v1"

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/iam.serviceAccountUser"

# Generate JSON key for GitHub Secrets (keep private!)
gcloud iam service-accounts keys create gcp-sa-key.json \
  --iam-account="$SA_EMAIL"
```

> If your VM has **OS Login** enabled, also grant `roles/compute.osAdminLogin` (needed for `sudo` inside `deploy-vm.sh`).

## 2. One-Time Setup on the Compute Engine VM

SSH into your VM and configure your runtime secrets once in `/etc/air-alert/`:

```bash
sudo mkdir -p /etc/air-alert

# 1. Upload or create your Firebase Admin SDK service account JSON:
#    /etc/air-alert/firebase-service-account.json

# 2. Create /etc/air-alert/backend.env:
sudo tee /etc/air-alert/backend.env > /dev/null << 'EOF'
PORT=8080
UKRAINE_ALARM_API_TOKEN=your_ukraine_alarm_api_token
WEBHOOK_PUBLIC_URL=https://yourname.duckdns.org/api/v1/webhook/ukrainealarm
WEBHOOK_AUTO_REGISTER=true
FIREBASE_CREDENTIALS_PATH=/etc/air-alert/firebase-service-account.json
EOF

sudo chmod 600 /etc/air-alert/backend.env /etc/air-alert/firebase-service-account.json
```

*(Ensure your user/GitHub deployer SSH user can run `sudo` without a password prompt — default GCP Compute Engine OS images allow passwordless `sudo` for provisioned SSH/OS Login users.)*

## 3. Add GitHub Repository Secrets

In your GitHub repository → **Settings → Secrets and variables → Actions → New repository secret**, add:

| Secret Name | Value |
|---|---|
| `GCP_PROJECT_ID` | Your GCP Project ID (e.g. `air-alert-ua`) |
| `GCE_INSTANCE_NAME` | Your VM instance name (e.g. `air-alert-vm`) |
| `GCE_ZONE` | Your VM zone (e.g. `us-central1-a`) |
| `GCP_SA_KEY` | Entire contents of `gcp-sa-key.json` |

Delete the local `gcp-sa-key.json` file after adding it to GitHub Secrets.

## 4. Push to GitHub & Trigger CI/CD

If this folder is not initialized as a git repo yet:

```powershell
git init
git add .
git commit -m "Add Air Alert Wear OS + Spring Boot backend and GCP CI/CD"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

Every push to `main` touching `backend/**` or `deploy/**` (or manual run via **Actions → Deploy Backend to GCP Compute Engine → Run workflow**) will:
1. Run `:backend:test` and build the Spring Boot fat JAR (`:backend:bootJar`).
2. Copy the JAR and `systemd` unit to your Compute Engine VM via `gcloud compute scp`.
3. Create a 1.5 GB swap file (if missing), install Java 17 (if missing), restart `air-alert-backend.service`, and verify `http://127.0.0.1:8080/api/v1/regions` responds `200 OK`.
