# Java Hello World Container & GitOps

This repository contains a simple Java Hello World service, packaged as a container, and structured for GitOps deployment using Kustomize, GitHub Actions, ArgoCD, and Kargo.

## Repository Structure

```
.
├── .github/
│   └── workflows/
│       └── build-and-push.yaml    # GitHub Actions CI/CD pipeline
├── gitops/
│   ├── base/
│   │   ├── deployment.yaml        # Common Kubernetes Deployment manifest
│   │   └── kustomization.yaml     # Kustomize base configuration
│   └── overlays/
│       └── dev/
│           └── kustomization.yaml # Development overlay patching the image tag
├── kargo/                         # (Planned) Kargo promotion engine configuration
│   ├── project.yaml               # Kargo Project CRD defining the workspace
│   └── stage-dev.yaml             # Kargo Stage CRD defining dev environment
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── example/
│                   └── HelloWorldApp.java # Java hello world logger
├── .dockerignore                  # Files excluded from Docker builds
├── Dockerfile                     # Multi-stage Docker build file
├── Plan.md                        # Master implementation plan
├── pom.xml                        # Maven dependency and build manifest
└── README.md                      # Quickstart documentation
```

---

## Local Development & Testing

### 1. Compile & Build Locally (Requires Java 17 + Maven)
```bash
mvn clean package
java -jar target/java-hello-world-1.0-SNAPSHOT.jar
```

### 2. Run with Docker (Requires Docker)
Build the image:
```bash
docker build -t myusername/java-hello-world .
```

Run the container:
```bash
docker run --env INTERVAL_SECONDS=5 myusername/java-hello-world
```

---

## GitHub Actions Secret Configuration

To enable the GitHub Actions pipeline, configure the following secrets in your repository settings (`Settings` > `Secrets and variables` > `Actions`):
- `DOCKER_USERNAME`: Your DockerHub username.
- `DOCKER_PASSWORD`: Your DockerHub personal access token (or password).

Also, ensure that under `Settings` > `Actions` > `General` > `Workflow permissions`, you select **"Read and write permissions"** so the workflow can push the updated Kustomize tags back to the repository.

---

## Setting up the Local Dev Environment

Refer to [Plan.md](file:///home/tonyh/_Projects/java-hello-world-container/Plan.md) for full instructions on setting up Kind, ArgoCD, and Kargo.
