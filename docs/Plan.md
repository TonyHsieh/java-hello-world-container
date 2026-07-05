# Implementation Plan: Java Hello World GitOps & Local Cluster Setup

This document outlines the step-by-step plan to implement a Java Hello World application service, package it as a Docker container, publish it via GitHub Actions, and configure a local Kubernetes environment running Kind, Argo CD, and Kargo.

---

## Phase 1: Java Application & Containerization

### Step 1.1: Initialize Java Project
- **Objective:** Create a lightweight Java application that outputs `Hello World - [timestamp]` every $N$ seconds (default: 10 seconds).
- **Deliverables:**
  - `pom.xml` (Maven configuration targeting Java 17/21).
  - `src/main/java/com/example/HelloWorldApp.java` containing the application loop.
- **Configurability:** Read `INTERVAL_SECONDS` from environment variables, fallback to `10`.

### Step 1.2: Containerize the Application
- **Objective:** Build a Dockerfile that compiles and packages the Java application securely.
- **Deliverables:**
  - `Dockerfile` using a multi-stage build:
    - **Stage 1 (Build):** `maven:3.9-eclipse-temurin` to build the `.jar`.
    - **Stage 2 (Runtime):** `eclipse-temurin:17-jre-alpine` or `21-jre-alpine` (lightweight JRE environment).
  - `.dockerignore` to exclude `target/`, `.git/`, and other local directories.

### Step 1.3: Local Verification
- **Objective:** Build and run the image locally using Docker to ensure timestamp format and interval configs work.
- **Commands:**
  ```bash
  docker build -t java-hello-world .
  docker run --env INTERVAL_SECONDS=5 java-hello-world
  ```

---

## Phase 2: GitHub Actions Workflow & DockerHub Integration

### Step 2.1: Establish GitHub Actions Directory
- **Objective:** Setup GitHub Actions workflows folder.
- **Deliverables:** `.github/workflows/build-and-push.yaml`

### Step 2.2: Implement Build & Push Pipeline
- **Objective:** Compile Java app, build container, and push to DockerHub on git push.
- **Pipeline Stages:**
  1. **Checkout:** Pull repository code.
  2. **Set up JDK:** Install Java JDK (matching the configured version).
  3. **Build jar:** Run `mvn clean package`.
  4. **Docker login:** Authenticate with DockerHub using GitHub repository secrets (`DOCKER_USERNAME`, `DOCKER_PASSWORD`).
  5. **Build and Push:** Tag the image with Git Commit SHA (`${{ github.sha }}`) and `latest` (if main branch) and push.

### Step 2.3: Integrate Kustomize Upstream Updates
- **Objective:** Automatically update the Kustomize overlay manifest image tag with the new commit SHA tag and push it back to the repo.
- **Pipeline Actions:**
  - Run `kustomize edit set image ...` or a standard string replacement (`sed`/`yq`) in `gitops/overlays/dev/kustomization.yaml`.
  - Commit and push changes back to the repository using a GitHub Actions service account.

---

## Phase 3: Kustomize Directory Setup

### Step 3.1: Define Base Configs
- **Objective:** Create the core Kubernetes manifests template.
- **Deliverables in `gitops/base/`:**
  - `deployment.yaml`: Deployment spec with a single replica running our container. Contains env var `INTERVAL_SECONDS`.
  - `kustomization.yaml`: Aggregates resources under base.

### Step 3.2: Define Overlay Configurations
- **Objective:** Environment-specific configuration overlays.
- **Deliverables in `gitops/overlays/dev/`:**
  - `kustomization.yaml`: Imports `../../base` and specifies the image name to patch with the tag.
  - Optional `patches.yaml` to customize settings specific to Dev (like changing the log interval to 5 seconds).

---

## Phase 4: Local Kind Cluster with Argo CD & Kargo

### Step 4.1: Provision Kind Cluster
- **Objective:** Create a local cluster using Kind.
- **Deliverables:** `kind-config.yaml` to handle port-mapping (if ingress or local services are needed).
- **Command:** `kind create cluster --config kind-config.yaml --name dev-cluster`

### Step 4.2: Install Argo CD
- **Objective:** Deploy GitOps controller.
- **Commands:**
  ```bash
  kubectl create namespace argocd
  kubectl apply --server-side --force-conflicts -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
  ```

### Step 4.3: Install Kargo
- **Objective:** Install the promotion engine on the cluster.
- **Prerequisite:** Install `cert-manager` (required by Kargo webhooks):
  ```bash
  kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.15.1/cert-manager.yaml
  ```
- **Commands (Install via Helm):**
  ```bash
  # 1. Generate password hash (requires apache2-utils for htpasswd)
  # On Ubuntu/WSL: sudo apt-get install -y apache2-utils
  PASS=$(openssl rand -base64 48 | tr -d "=+/" | head -c 32)
  HASHED_PASS=$(htpasswd -bnBC 10 "" $PASS | tr -d ':\n')
  SIGNING_KEY=$(openssl rand -base64 48 | tr -d "=+/" | head -c 32)

  echo "Kargo Admin Password: $PASS"

  # 2. Deploy Kargo
  helm upgrade --install kargo oci://ghcr.io/akuity/kargo-charts/kargo \
    --namespace kargo \
    --create-namespace \
    --set api.adminAccount.passwordHash=$HASHED_PASS \
    --set api.adminAccount.tokenSigningKey=$SIGNING_KEY \
    --wait
  ```

### Step 4.4: Accessing the Dashboards (UIs)
- **Objective:** Access the web-based interfaces for Argo CD and Kargo.
- **Argo CD:**
  1. Port-forward: `kubectl port-forward svc/argocd-server -n argocd 8080:443`
  2. URL: `https://localhost:8080` (bypass certificate warning)
  3. Credentials: Username `admin`, password retrieved via:
     `kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d`
- **Kargo:**
  * **Option A (CLI):** Run `kargo dashboard`.
  * **Option B (Manual Port-Forward):**
    1. Port-forward: `kubectl port-forward svc/kargo-api -n kargo 8081:443`
    2. URL: `https://localhost:8081`
    3. Credentials: Log in using the admin password generated in Step 4.3.

### Step 4.5: Configure Kargo Projects, Warehouses, Stages & Git Credentials
- **Objective:** Define Kargo GitOps pipeline config and grant it write access to GitHub.
- **1. Generate GitHub Personal Access Token (PAT):**
  * Generate a **Fine-grained PAT** (Settings > Developer Settings > Personal Access Tokens > Fine-grained tokens).
  * Set repository scope to **Only select repositories** -> `java-hello-world-container`.
  * Grant **Repository permissions** -> **Contents**: `Read and write` (which automatically sets **Metadata** to `Read-only`).
- **2. Define Credentials Template:**
  * Configure credentials secret in `kargo/git-credentials.yaml` using your GitHub username and the PAT as the password.
  * Apply: `kubectl apply -f kargo/git-credentials.yaml`
- **3. Configure Project, Warehouse, Stage, and Auto-Promotion:**
  * `kargo/project.yaml`: Declares the Kargo Project namespace `java-hello-world-container-project`.
  * `kargo/project-config.yaml`: Defines promotion policies (e.g. `autoPromotionEnabled: true` for the `dev` stage).
  * `kargo/warehouse.yaml`: Subscribes Kargo to the Docker Hub image `solidstrider/java-hello-world-container` matching 40-character Git commit SHAs.
  * `kargo/stage-dev.yaml`: Configures the promotion template utilizing Kargo's `git-clone`, `kustomize-set-image`, `git-commit`, and `git-push` steps to promote new images to the `dev` overlay.
- **4. Apply Configs to Cluster:**
  ```bash
  kubectl apply -f kargo/project.yaml
  kubectl apply -f kargo/project-config.yaml
  kubectl apply -f kargo/warehouse.yaml
  kubectl apply -f kargo/stage-dev.yaml
  ```
- **5. Sanity Check Kargo Integration:**
  * **Verify Resources:** Ensure Kargo objects are live:
    ```bash
    kubectl get projects.kargo.akuity.io
    kubectl get warehouses,stages -n java-hello-world-container-project
    ```
  * **Verify Credentials:** Check that the repository secret exists:
    ```bash
    kubectl get secret github-repo-credentials -n java-hello-world-container-project
    ```
  * **Verify Warehouse Connection:** Check that the Warehouse is polling Docker Hub successfully:
    ```bash
    kubectl describe warehouse java-hello-world-container-warehouse -n java-hello-world-container-project
    ```
  * **Verify Freight Generation:** Confirm Kargo discovered your tags and generated a Freight object:
    ```bash
    kubectl get freight -n java-hello-world-container-project
    ```

### Step 4.6: Configure Argo CD Application
- **Objective:** Point Argo CD to the `gitops/overlays/dev/` directory on the cluster.
- **Deliverables:** `gitops/argo-app.yaml` applied via `kubectl apply -f gitops/argo-app.yaml`.

---

## Phase 5: Verification & Testing (Kargo Flow)

1. **Trigger CI:** Make a change in the Java log message in `src/main/java/com/example/HelloWorldApp.java`, commit, and push to main.
2. **Verify Image Build:** Confirm the GitHub Actions workflow successfully compiles and pushes the new image (`solidstrider/java-hello-world-container:<SHA>`) to Docker Hub.
3. **Verify Kargo Freight Discovery:**
   * Confirm that Kargo's Warehouse discovers the new tag and generates a new Freight resource:
     ```bash
     kubectl get freight -n java-hello-world-container-project
     ```
4. **Promote the Freight (Manually or Automatically):**
   * Promote the Freight to the `dev` stage using the Kargo CLI:
     ```bash
     kargo stage promote dev-cluster dev --project java-hello-world-container-project --freight <freight-name>
     ```
   * Or open the Kargo Dashboard, click on your project, select the new Freight, and click **Promote**.
5. **Verify Kargo Promotion Commit:** Confirm that Kargo clones the repo, modifies `gitops/overlays/dev/kustomization.yaml` with the new SHA tag, commits the change with a `[skip ci]` flag, and pushes it back to GitHub.
6. **Verify Argo CD Deployment:** Confirm that Argo CD detects Kargo's commit, syncs it, and rolls out the updated container in the Kind cluster's `dev` namespace.
