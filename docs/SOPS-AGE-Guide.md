# Guide: Secret Management with SOPS & age

This guide explains how to secure sensitive configurations (like the Docker Hub password and GitHub Personal Access Token) using **SOPS (Secrets Operations)** and **age** (a modern, simple file encryption tool), allowing you to safely commit encrypted secrets to your Git repository.

---

## 1. Concepts Overview

```
                   LOCAL MACHINE                                         KIND CLUSTER
┌──────────────────────────────────────────────────┐        ┌──────────────────────────────────────┐
│  Plain Secret         age Key Pair (key.txt)     │        │  Argo CD Repo Server                 │
│  (git-secrets.yaml)   ├── Public Key (for encryption)    │        │  (Has private key secret applied)    │
│       │               └── Private Key (keep secure)      │        │           │                          │
│       ▼                                          │  Push  │           ▼                          │
│   ┌───────┐                                      ├───────►│       ┌───────┐                      │
│   │ SOPS  ├─► Encrypted Secret (safe for Git)    │        │       │ SOPS  ├─► Decrypted Secret   │
│   └───────┘                                      │        │       └───────┘   (applied to Pods)  │
└──────────────────────────────────────────────────┘        └──────────────────────────────────────┘
```

* **age:** Generates a key pair (a public key for encrypting files, and a private key for decrypting them).
* **SOPS:** Uses the public key to encrypt specific value fields in your YAML files while keeping the keys (metadata structure) readable by Kubernetes/Kustomize.
* **Argo CD / Cluster Decryption:** The cluster stores the private key securely. During sync, Argo CD decrypts the YAML file on-the-fly and applies the plain secret to the cluster namespace.

---

## 2. Local Setup (Developer Machine)

### Step 1: Install SOPS & age Binaries
* **macOS (Homebrew):**
  ```bash
  brew install sops age
  ```
* **Linux (Ubuntu/WSL):**
  ```bash
  # Install age
  sudo apt-get install -y age
  
  # Download and install SOPS
  SOPS_VER=$(curl -s "https://api.github.com/repos/getsops/sops/releases/latest" | grep -oP '"tag_name": "\K[^"]+')
  curl -LO "https://github.com/getsops/sops/releases/download/${SOPS_VER}/sops-${SOPS_VER}.linux.amd64"
  sudo mv sops-*.linux.amd64 /usr/local/bin/sops
  sudo chmod +x /usr/local/bin/sops
  ```

### Step 2: Generate an age Keypair
Generate a new keypair and save it locally (do **never** commit this file to Git):
```bash
mkdir -p ~/.config/sops/age
age-keygen -o ~/.config/sops/age/keys.txt
```
Look at your public key (you will need this for the SOPS config):
```bash
cat ~/.config/sops/age/keys.txt | grep "public key:"
# Example output: public key: age1z9jxlqpqp4q...
```

### Step 3: Configure SOPS Configuration File (`.sops.yaml`)
Create a `.sops.yaml` file at the root of your repository. This tells SOPS which age keys to use when encrypting files:
```yaml
creation_rules:
  - path_regex: .*/secrets/.*\.yaml$
    key_groups:
      - age:
          - age1z9jxlqpqp4q... # Replace with your actual public key
```

---

## 3. Secret Encryption Workflow

### Step 1: Create a Plaintext Secret
Create a raw secret file (e.g. `gitops/overlays/dev/secrets/git-credentials-raw.yaml`):
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: github-repo-credentials
  namespace: java-hello-world-container-project
  labels:
    kargo.akuity.io/cred-type: git
stringData:
  repoURL: https://github.com/TonyHsieh/java-hello-world-container.git
  username: TonyHsieh
  password: github_pat_12345ABCDE...
```
*(Make sure `*-raw.yaml` is added to your `.gitignore`!)*

### Step 2: Encrypt the Secret File with SOPS
Run SOPS to encrypt the raw secret and write the output file:
```bash
sops --encrypt gitops/overlays/dev/secrets/git-credentials-raw.yaml > gitops/overlays/dev/secrets/git-credentials.yaml
```

If you open `git-credentials.yaml`, you will notice that the sensitive `password` field is encrypted into a ciphertext block, while the structure of the YAML remains intact. This file is now safe to commit to GitHub!

---

## 4. In-Cluster Decryption (Argo CD Integration)

To allow Argo CD to deploy your encrypted secrets, it must have access to your private key to decrypt them.

### Step 1: Import the Private Key into the Cluster
Create a Kubernetes secret containing your age private key in the `argocd` namespace:
```bash
kubectl create secret generic helm-secrets-private-keys \
  --namespace argocd \
  --from-file=key.txt=$HOME/.config/sops/age/keys.txt
```

### Step 2: Configure Argo CD Repo-Server to use SOPS
Configure Argo CD to use the **argocd-vault-plugin** or the **Helm Secrets** wrapper plugin. 

For a lightweight Kustomize-friendly setup, deploy the **sops-operator** in the Kind cluster:
1. **Install the operator:**
   ```bash
   kubectl apply -f https://github.com/isindir/sops-secrets-operator/releases/latest/download/sops-secrets-operator.yaml
   ```
2. **Apply the age private key to the operator namespace:**
   This lets the operator automatically scan Kargo/GitOps namespaces for SOPS secrets and decrypt them on-the-fly.
