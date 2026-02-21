#!/bin/bash
# Deploy basestation-platform to Kubernetes via Helm
#
# Usage:
#   ./scripts/deploy.sh full              # Build + secrets + deploy
#   ./scripts/deploy.sh build             # Build Docker images into minikube
#   ./scripts/deploy.sh deploy            # Deploy via Helm (default values)
#   ./scripts/deploy.sh deploy-dev        # Deploy with dev overrides (1 replica, no prod features)
#   ./scripts/deploy.sh deploy-prod       # Deploy with prod overrides (HPAs, netpol, backups)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
NAMESPACE="${NAMESPACE:-basestation-platform}"
MINIKUBE="${MINIKUBE:-minikube}"
CHART_DIR="$PROJECT_ROOT/helm/basestation-platform"
RELEASE_NAME="basestation"

setup_minikube_docker() {
    if command -v "$MINIKUBE" &>/dev/null && "$MINIKUBE" status &>/dev/null; then
        echo "Using minikube's Docker daemon..."
        eval $("$MINIKUBE" docker-env)
    else
        echo "WARNING: minikube not running. Images must be available in the cluster."
    fi
}

build_images() {
    setup_minikube_docker
    cd "$PROJECT_ROOT"

    echo "Building Java services..."
    mvn clean package -DskipTests -q

    echo "Building Docker images (into minikube)..."
    docker compose build --parallel

    local version="1.0.0"
    echo ""
    echo "Tagging images with version $version..."
    for svc in ai-diagnostic anomaly-simulator api-gateway auth-service base-station-service frontend monitoring-service notification-service edge-bridge device-simulator; do
        local img="basestation-platform-${svc}"
        if docker image inspect "$img:latest" &>/dev/null; then
            docker tag "$img:latest" "$img:$version"
        fi
    done

    echo ""
    echo "Images built:"
    docker images --format '{{.Repository}}:{{.Tag}}\t{{.Size}}' | grep -E 'basestation-platform' | sort
}

create_secrets() {
    local secrets_script="$PROJECT_ROOT/k8s/create-secrets.sh"
    if [[ ! -f "$secrets_script" ]]; then
        echo "ERROR: $secrets_script not found"
        exit 1
    fi
    bash "$secrets_script" "$NAMESPACE"
}

helm_deploy() {
    local values_file="${1:-}"
    echo "Deploying to namespace: $NAMESPACE..."
    local cmd="helm upgrade --install $RELEASE_NAME $CHART_DIR -n $NAMESPACE --create-namespace"
    if [[ -n "$values_file" ]]; then
        cmd="$cmd -f $values_file"
    fi
    eval "$cmd"
    echo ""
    echo "Waiting for pods..."
    kubectl get pods -n "$NAMESPACE"
}

case "$1" in
    build)
        build_images
        ;;
    secrets)
        create_secrets
        ;;
    deploy)
        helm_deploy
        ;;
    deploy-dev)
        helm_deploy "$CHART_DIR/values-dev.yaml"
        ;;
    deploy-prod)
        helm_deploy "$CHART_DIR/values-prod.yaml"
        ;;
    undeploy)
        echo "Removing all resources from namespace: $NAMESPACE..."
        helm uninstall "$RELEASE_NAME" -n "$NAMESPACE" 2>/dev/null || true
        ;;
    template)
        helm template "$RELEASE_NAME" "$CHART_DIR" -n "$NAMESPACE"
        ;;
    full)
        build_images
        echo ""
        if [[ -f "$PROJECT_ROOT/.env" ]]; then
            if ! kubectl get secret postgres-secrets -n "$NAMESPACE" &>/dev/null; then
                echo "Creating secrets from .env..."
                create_secrets
                echo ""
            fi
        else
            echo "WARNING: No .env file found. Run './scripts/deploy.sh secrets' after creating .env"
        fi
        helm_deploy
        ;;
    status)
        kubectl get pods -n "$NAMESPACE"
        ;;
    logs)
        if [[ -z "$2" ]]; then
            echo "Usage: deploy.sh logs <pod-name>"
            exit 1
        fi
        kubectl logs -n "$NAMESPACE" "$2" --tail=100
        ;;
    restart)
        if [[ -z "$2" ]]; then
            echo "Usage: deploy.sh restart <deployment-name>"
            exit 1
        fi
        kubectl rollout restart deployment/"$2" -n "$NAMESPACE"
        kubectl rollout status deployment/"$2" -n "$NAMESPACE"
        ;;
    --help|-h|"")
        cat << 'EOF'
Usage: deploy.sh <command> [args]

Shell commands (Docker/kubectl):
  build               Build Docker images into minikube's Docker daemon
  secrets             Create K8s secrets from .env file
  status              Show pod status
  logs <pod>          Show pod logs (last 100 lines)
  restart <name>      Rolling restart a deployment

Helm commands:
  deploy              Deploy with default values
  deploy-dev          Deploy with dev overrides (1 replica each, no prod features)
  deploy-prod         Deploy with prod overrides (HPAs, network policies, backups)
  undeploy            Remove all platform resources
  template            Render templates to stdout (dry-run)

Combined:
  full                Build + secrets + deploy (recommended for fresh deploy)

Examples:
  deploy.sh full                  # Full deploy: build + secrets + deploy
  deploy.sh build                 # Build all Docker images into minikube
  deploy.sh deploy-dev            # Deploy with dev config (single replicas)
  deploy.sh deploy-prod           # Deploy with production config
  deploy.sh undeploy              # Tear down everything
  deploy.sh status                # Show pod status

Environment:
  NAMESPACE=prod deploy.sh full   # Deploy to 'prod' namespace
EOF
        ;;
    *)
        echo "Unknown command: $1"
        echo "Run 'deploy.sh --help' for usage"
        exit 1
        ;;
esac
